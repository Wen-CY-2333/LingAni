"""灵漫 AI Agent「小灵」：两阶段实现 —— ReAct 工具循环 + 结构化回答。

阶段 1（工具循环）：模型自主决定调用哪些工具，本轮 ToolMessage 是唯一可信数据源。
阶段 2（结构化回答）：独立的 JSON 输出模型读取用户问题、最近对话与本轮工具结果，
                     直接产出 {"reply": ..., "works": [...], "actions": [...]}。

Python 只负责协议解析与 ID 白名单校验（防止编造 ID 造成 404），
不再用关键词、标题子串等规则推断回复内容或跳转地址。
"""

import json
import logging
from collections.abc import Sequence

from langchain_core.messages import AIMessage, AnyMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_core.tools import BaseTool
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph
from langgraph.prebuilt import ToolNode, tools_condition

from app.agent.llm import build_chat_model
from app.agent.output import (
    AgentOutput,
    ReplyStreamExtractor,
    extract_tool_references,
    message_text,
    parse_agent_output,
    sanitize_output,
)
from app.agent.prompts import build_answer_prompt, build_system_prompt
from app.agent.session import SessionOwnershipError, SessionRegistry, validate_thread_id
from app.agent.state import AgentState
from app.config import settings

logger = logging.getLogger(__name__)

RECURSION_LIMIT = 20

_FALLBACK_REPLY = "抱歉，小灵这次没能整理好回答，请再问一次。"
_MAX_TOOL_RESULT_CHARS = 4000
_MAX_TOOL_CONTEXT_CHARS = 16000
_MAX_HISTORY_LINES = 6
_RETRY_HINT = (
    "上一次输出不是合法 JSON。请严格只输出一个 JSON 对象，"
    "包含 reply、works、actions 三个字段，不要输出任何解释、标记或代码块。"
)


class AnimeAgent:
    """灵漫 AI 助手「小灵」：先自主调用工具，再由结构化输出模型整理回答。"""

    def __init__(self, tools: Sequence[BaseTool]) -> None:
        self._tools = list(tools)
        self._model = build_chat_model(streaming=True)
        self._model_with_tools = self._model.bind_tools(self._tools) if self._tools else self._model
        self._formatter = build_chat_model(streaming=True, json_mode=True)
        self._checkpointer = MemorySaver()
        self._sessions = SessionRegistry()
        self._graph = self._build_graph()

    # ---- 图构建 ----

    def _build_graph(self):
        builder = StateGraph(AgentState)
        builder.add_node("agent", self._call_model)
        builder.add_edge(START, "agent")

        if self._tools:
            builder.add_node("tools", ToolNode(self._tools, handle_tool_errors=_tool_error_message))
            builder.add_conditional_edges("agent", tools_condition, {"tools": "tools", END: END})
            builder.add_edge("tools", "agent")
        else:
            builder.add_edge("agent", END)

        return builder.compile(checkpointer=self._checkpointer)

    async def _call_model(self, state: AgentState) -> dict:
        system_prompt = build_system_prompt(state.get("display_name"), state.get("user_context"))
        window = self._window_messages(state["messages"])
        response = await self._model_with_tools.ainvoke([SystemMessage(content=system_prompt), *window])
        for call in getattr(response, "tool_calls", None) or []:
            logger.info("工具调用：%s %s", call.get("name"), call.get("args"))
        return {"messages": [response]}

    @staticmethod
    def _window_messages(messages: Sequence[AnyMessage]) -> list[AnyMessage]:
        from app.agent.session import trim_messages
        return trim_messages(messages, settings.max_history_messages)

    # ---- 会话归属 ----

    def _snapshot(self, thread_id: str):
        return self._graph.get_state({"configurable": {"thread_id": thread_id}})

    def _ensure_ownership(self, thread_id: str, user_id: str) -> None:
        self._sessions.bind_or_verify(thread_id, user_id)
        snapshot = self._snapshot(thread_id)
        stored_user_id = (snapshot.values or {}).get("user_id") if snapshot else None
        if stored_user_id and stored_user_id != user_id:
            raise SessionOwnershipError("该会话不属于当前用户")

    def ensure_session(self, thread_id: str, user_id: str) -> None:
        validate_thread_id(thread_id)
        self._ensure_ownership(thread_id, user_id)

    # ---- 消息辅助 ----

    @staticmethod
    def _current_turn(messages: Sequence[AnyMessage]) -> list[AnyMessage]:
        """最后一次 HumanMessage 及之后的所有消息 = 当前轮。"""
        for index in range(len(messages) - 1, -1, -1):
            if isinstance(messages[index], HumanMessage):
                return list(messages[index:])
        return list(messages)

    @staticmethod
    def _current_turn_final_text(messages: Sequence[AnyMessage]) -> str:
        """当前轮里最后一条不含 tool_calls 的 AIMessage 文本（仅作兜底）。"""
        for item in reversed(AnimeAgent._current_turn(messages)):
            if isinstance(item, AIMessage) and not (getattr(item, "tool_calls", None) or []):
                return message_text(item.content).strip()
        return ""

    @staticmethod
    def _collect_turn_tool_texts(messages: Sequence[AnyMessage]) -> list[str]:
        turn = AnimeAgent._current_turn(messages)
        return [message_text(item.content) for item in turn if isinstance(item, ToolMessage)]

    @staticmethod
    def _history_lines(messages: Sequence[AnyMessage], turn: Sequence[AnyMessage]) -> list[str]:
        """当前轮之前的最近对话，供结构化输出模型参考指代。"""
        before = list(messages)[: len(messages) - len(turn)]
        lines: list[str] = []
        for item in reversed(before):
            if isinstance(item, HumanMessage):
                text = message_text(item.content).strip()
                if text:
                    lines.append(f"用户：{text}")
            elif isinstance(item, AIMessage):
                if getattr(item, "tool_calls", None):
                    continue
                text = message_text(item.content).strip()
                if text:
                    lines.append(f"小灵：{text}")
            if len(lines) >= _MAX_HISTORY_LINES:
                break
        return list(reversed(lines))

    # ---- 阶段 2：结构化回答 ----

    def _format_input(self, messages: Sequence[AnyMessage]) -> str:
        turn = self._current_turn(messages)

        question = ""
        for item in reversed(turn):
            if isinstance(item, HumanMessage):
                question = message_text(item.content).strip()
                break

        call_names: dict[str, str] = {}
        for item in turn:
            if isinstance(item, AIMessage):
                for call in getattr(item, "tool_calls", None) or []:
                    call_id = call.get("id")
                    if call_id:
                        call_names[call_id] = call.get("name") or "tool"

        blocks: list[str] = []
        total = 0
        for item in turn:
            if not isinstance(item, ToolMessage):
                continue
            text = message_text(item.content).strip()
            if not text:
                continue
            if len(text) > _MAX_TOOL_RESULT_CHARS:
                text = text[:_MAX_TOOL_RESULT_CHARS] + "…（已截断）"
            if total + len(text) > _MAX_TOOL_CONTEXT_CHARS:
                blocks.append("（其余工具结果过多，已省略）")
                break
            total += len(text)
            blocks.append(f"[{call_names.get(item.tool_call_id, 'tool')}] {text}")

        history = self._history_lines(messages, turn)

        parts = [f"【用户最新问题】\n{question}"]
        if history:
            parts.append("【最近对话】\n" + "\n".join(history))
        parts.append("【本轮工具结果】\n" + ("\n".join(blocks) if blocks else "（本轮没有调用任何工具）"))
        return "\n\n".join(parts)

    async def _format_answer(
        self, system_prompt: str, human_prompt: str
    ) -> AgentOutput | None:
        request = [SystemMessage(content=system_prompt), HumanMessage(content=human_prompt)]
        raw = await self._formatter.ainvoke(request)
        text = message_text(raw.content)
        parsed = parse_agent_output(text)
        if parsed is not None:
            return parsed

        logger.warning("结构化输出解析失败，重试一次：%s", text[:200])
        retry = await self._formatter.ainvoke(
            [
                *request,
                AIMessage(content=text[:2000]),
                HumanMessage(content=_RETRY_HINT),
            ]
        )
        parsed = parse_agent_output(message_text(retry.content))
        if parsed is None:
            logger.error("结构化输出连续失败：%s", text[:500])
        return parsed

    def _finalize(
        self,
        parsed: AgentOutput | None,
        messages: Sequence[AnyMessage],
        fallback_reply: str = "",
    ) -> AgentOutput:
        """用本轮工具结果做 ID 白名单校验；解析失败时才退回阶段 1 文本。"""
        tool_texts = self._collect_turn_tool_texts(messages)
        video_ids, section_ids, _ = extract_tool_references(tool_texts)
        if parsed is None:
            reply = (
                fallback_reply.strip()
                or self._current_turn_final_text(messages)
                or _FALLBACK_REPLY
            )
            return AgentOutput(reply=reply, works=[], actions=[])
        return sanitize_output(parsed, video_ids, section_ids)

    # ---- 写回会话历史 ----

    def _remember(self, thread_id: str, messages: Sequence[AnyMessage], reply: str) -> None:
        """用最终回复覆盖本轮最后一条 AIMessage，避免历史里出现重复回答。"""
        last_ai = next((item for item in reversed(messages) if isinstance(item, AIMessage)), None)
        message = (
            AIMessage(id=last_ai.id, content=reply)
            if last_ai is not None and getattr(last_ai, "id", None)
            else AIMessage(content=reply)
        )
        try:
            self._graph.update_state(
                {"configurable": {"thread_id": thread_id}},
                {"messages": [message]},
            )
        except Exception:
            logger.warning("写回最终回答失败", exc_info=True)

    # ---- 非流式入口 ----

    async def ainvoke(self, *, thread_id: str, user_id: str, message: str,
                      display_name: str | None, user_context: dict | None = None) -> AgentOutput:
        validate_thread_id(thread_id)
        self._ensure_ownership(thread_id, user_id)

        result = await self._graph.ainvoke(
            {
                "messages": [HumanMessage(content=message)],
                "user_id": user_id,
                "display_name": display_name or "",
                "user_context": user_context or {},
            },
            {"configurable": {"thread_id": thread_id}, "recursion_limit": RECURSION_LIMIT},
        )

        messages = result["messages"]
        system_prompt = build_answer_prompt(result.get("user_context"))
        parsed = await self._format_answer(system_prompt, self._format_input(messages))
        output = self._finalize(parsed, messages)
        self._remember(thread_id, messages, output.reply)
        return output

    # ---- 流式入口 ----

    async def astream(self, *, thread_id: str, user_id: str, message: str,
                      display_name: str | None, user_context: dict | None = None):
        """以 (事件类型, 内容) 形式产出增量。

        - tool_call: 阶段 1 的工具调用意图
        - token: 阶段 2 结构化输出中 reply 字段的增量
        - output: 完整 AgentOutput（done 事件）
        """
        validate_thread_id(thread_id)
        self._ensure_ownership(thread_id, user_id)

        config = {"configurable": {"thread_id": thread_id}, "recursion_limit": RECURSION_LIMIT}
        emitted_tool_calls: set[tuple] = set()
        pending_tool_calls: dict[int, dict] = {}

        async for chunk, metadata in self._graph.astream(
            {
                "messages": [HumanMessage(content=message)],
                "user_id": user_id,
                "display_name": display_name or "",
                "user_context": user_context or {},
            },
            config,
            stream_mode="messages",
        ):
            if metadata.get("langgraph_node") != "agent":
                continue

            # 流式工具调用参数：按 index 拼接到能解析为止
            for frag in getattr(chunk, "tool_call_chunks", None) or []:
                index = frag.get("index") or 0
                entry = pending_tool_calls.setdefault(index, {"name": "", "args": ""})
                if frag.get("name"):
                    entry["name"] = frag["name"]
                if frag.get("args"):
                    entry["args"] += frag["args"]
                if not entry["name"] or not entry["args"]:
                    continue
                try:
                    args = json.loads(entry["args"])
                except ValueError:
                    continue
                call = {"name": entry["name"], "args": args}
                pending_tool_calls.pop(index, None)
                key = _tool_key(call)
                if key in emitted_tool_calls:
                    continue
                emitted_tool_calls.add(key)
                yield "tool_call", call

            # 兼容不产生 tool_call_chunks 的模型（此时 args 已是完整 dict）
            for call in getattr(chunk, "tool_calls", None) or []:
                if not call.get("name") or not call.get("args"):
                    continue
                key = _tool_key(call)
                if key in emitted_tool_calls:
                    continue
                emitted_tool_calls.add(key)
                yield "tool_call", call

        snapshot = self._snapshot(thread_id)
        values = (snapshot.values or {}) if snapshot else {}
        messages = values.get("messages", [])

        system_prompt = build_answer_prompt(values.get("user_context"))
        human_prompt = self._format_input(messages)

        extractor = ReplyStreamExtractor()
        raw_parts: list[str] = []
        streamed_parts: list[str] = []
        async for chunk in self._formatter.astream(
            [SystemMessage(content=system_prompt), HumanMessage(content=human_prompt)]
        ):
            text = message_text(getattr(chunk, "content", ""))
            if not text:
                continue
            raw_parts.append(text)
            piece = extractor.feed(text)
            if piece:
                streamed_parts.append(piece)
                yield "token", piece

        parsed = parse_agent_output("".join(raw_parts))
        if parsed is None:
            logger.warning("流式结构化输出解析失败，重试一次")
            parsed = await self._format_answer(system_prompt, human_prompt)

        output = self._finalize(parsed, messages, "".join(streamed_parts))
        if not streamed_parts:
            yield "token", output.reply
        self._remember(thread_id, messages, output.reply)
        yield "output", output


# ---- 工具函数 ----

def _tool_key(call: dict) -> tuple:
    return (call.get("name"), json.dumps(call.get("args") or {}, sort_keys=True, ensure_ascii=False))


def _tool_error_message(exc: Exception) -> str:
    return f"工具调用失败：{exc}"
