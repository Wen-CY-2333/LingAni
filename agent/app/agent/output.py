"""模型输出协议：合法 JSON {reply, works, actions} 的解析、校验与流式提取。"""

import json
import logging
import re
from dataclasses import dataclass, field
from typing import Iterable

logger = logging.getLogger(__name__)

MAX_WORKS = 10
MAX_ACTIONS = 2

_JSON_FENCE = re.compile(r"^```(?:json)?\s*|\s*```$", re.IGNORECASE)

_NAVIGATE_PATTERNS = (
    re.compile(r"^/search\?q=[^#\s]+$"),
    re.compile(r"^/anime/\d+$"),
    re.compile(r"^/anime/\d+/play/\d+$"),
    re.compile(r"^/schedule$"),
    re.compile(r"^/rank$"),
    re.compile(r"^/category$"),
    re.compile(r"^/user/history$"),
    re.compile(r"^/user/favorites$"),
)

_ESCAPE_MAP = {'"': '"', "\\": "\\", "/": "/", "b": "\b", "f": "\f", "n": "\n", "r": "\r", "t": "\t"}


@dataclass
class AgentOutput:
    """一次对话最终要返回给前端的结构。"""

    reply: str
    works: list[dict] = field(default_factory=list)
    actions: list[dict] = field(default_factory=list)


def message_text(content) -> str:
    """把 AIMessage.content（str 或多段列表）统一成纯文本。"""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for part in content:
            if isinstance(part, str):
                parts.append(part)
            elif isinstance(part, dict) and part.get("type") == "text":
                parts.append(str(part.get("text", "")))
        return "".join(parts)
    return ""


def _extract_object(text: str) -> str | None:
    text = (text or "").strip()
    if not text:
        return None
    text = _JSON_FENCE.sub("", text).strip()
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end <= start:
        return None
    return text[start : end + 1]


def _clean_works(raw) -> list[dict]:
    if not isinstance(raw, list):
        return []
    works: list[dict] = []
    seen: set[str] = set()
    for item in raw:
        if not isinstance(item, dict):
            continue
        video_id = str(item.get("videoId", "")).strip()
        title = str(item.get("title", "")).strip()
        if not video_id.isdigit() or not title or video_id in seen:
            continue
        seen.add(video_id)
        works.append({"videoId": video_id, "title": title})
        if len(works) >= MAX_WORKS:
            break
    return works


def _clean_path(path) -> str | None:
    if not isinstance(path, str):
        return None
    path = path.strip()
    if not path or len(path) > 200 or "://" in path or path.startswith("//"):
        return None
    if not any(pattern.match(path) for pattern in _NAVIGATE_PATTERNS):
        return None
    return path


def _clean_actions(raw) -> list[dict]:
    if not isinstance(raw, list):
        return []
    actions: list[dict] = []
    seen: set[tuple] = set()
    for item in raw:
        if not isinstance(item, dict):
            continue
        action_type = item.get("type")
        if action_type == "navigate":
            path = _clean_path(item.get("path"))
            if not path:
                continue
            key = ("navigate", path)
            action = {"type": "navigate", "path": path}
        elif action_type == "add_favorite":
            video_id = str(item.get("videoId", "")).strip()
            if not video_id.isdigit():
                continue
            key = ("add_favorite", video_id)
            action = {"type": "add_favorite", "videoId": video_id}
        else:
            continue
        if key in seen:
            continue
        seen.add(key)
        actions.append(action)
        if len(actions) >= MAX_ACTIONS:
            break
    return actions


def parse_agent_output(text: str) -> AgentOutput | None:
    """严格解析模型输出；结构不合法时返回 None。"""
    candidate = _extract_object(text)
    if not candidate:
        return None
    try:
        data = json.loads(candidate)
    except (ValueError, TypeError):
        return None
    if not isinstance(data, dict):
        return None
    reply = data.get("reply")
    if not isinstance(reply, str) or not reply.strip():
        return None
    return AgentOutput(
        reply=reply.strip(),
        works=_clean_works(data.get("works")),
        actions=_clean_actions(data.get("actions")),
    )


def collect_tool_ids(texts: Iterable[str]) -> tuple[set[str], set[str]]:
    """从本轮工具返回里收集真实 video_id 与 section_id，用于过滤模型编造的 ID。"""
    video_ids: set[str] = set()
    section_ids: set[str] = set()
    for text in texts:
        try:
            data = json.loads(text)
        except (ValueError, TypeError):
            continue
        _collect_ids(data, video_ids, section_ids)
    return video_ids, section_ids


def _collect_ids(node, video_ids: set[str], section_ids: set[str]) -> None:
    if isinstance(node, dict):
        for key, value in node.items():
            if isinstance(value, (str, int)) and not isinstance(value, bool):
                normalized = str(value).strip()
                if key in ("video_id", "videoId") and normalized.isdigit():
                    video_ids.add(normalized)
                elif key in ("section_id", "sectionId") and normalized.isdigit():
                    section_ids.add(normalized)
            _collect_ids(value, video_ids, section_ids)
    elif isinstance(node, list):
        for item in node:
            _collect_ids(item, video_ids, section_ids)


def sanitize_output(output: AgentOutput, video_ids: set[str], section_ids: set[str]) -> AgentOutput:
    """丢弃没有真实工具数据支撑的作品与跳转，避免模型编造 ID。"""
    works = [work for work in output.works if work.get("videoId") in video_ids][:MAX_WORKS]
    actions: list[dict] = []
    for action in output.actions:
        if action.get("type") == "navigate":
            path = action.get("path", "")
            full = re.match(r"^/anime/(\d+)$", path)
            play = re.match(r"^/anime/(\d+)/play/(\d+)$", path)
            if full and full.group(1) not in video_ids:
                continue
            if play and (play.group(1) not in video_ids or play.group(2) not in section_ids):
                continue
        elif action.get("type") == "add_favorite":
            if action.get("videoId") not in video_ids:
                continue
        actions.append(action)
        if len(actions) >= MAX_ACTIONS:
            break
    return AgentOutput(reply=output.reply, works=works, actions=actions)


def extract_tool_references(texts: Iterable[str]) -> tuple[set[str], set[str], dict[str, str]]:
    """从本轮工具结果中收集 video_id、section_id 以及 {video_id: title} 映射。

    返回:
        (video_ids, section_ids, title_map)
    """
    video_ids: set[str] = set()
    section_ids: set[str] = set()
    title_map: dict[str, str] = {}

    def _walk(node):
        if isinstance(node, dict):
            vid = node.get("video_id") or node.get("videoId")
            title = node.get("title") or node.get("name")
            sid = node.get("section_id") or node.get("sectionId")
            if isinstance(vid, (str, int)) and not isinstance(vid, bool):
                v = str(vid).strip()
                if v.isdigit():
                    video_ids.add(v)
                    if isinstance(title, str) and title.strip():
                        title_map.setdefault(v, title.strip())
            if isinstance(sid, (str, int)) and not isinstance(sid, bool):
                s = str(sid).strip()
                if s.isdigit():
                    section_ids.add(s)
            for v in node.values():
                _walk(v)
        elif isinstance(node, list):
            for item in node:
                _walk(item)

    for text in texts:
        try:
            data = json.loads(text)
        except (ValueError, TypeError):
            continue
        _walk(data)

    return video_ids, section_ids, title_map


class ReplyStreamExtractor:
    """从结构化输出器返回的 JSON 文本中增量提取 reply 字段，避免把 JSON 结构推给前端。"""

    def __init__(self) -> None:
        self._buffer = ""
        self._state = "search"
        self._done = False

    def feed(self, chunk: str) -> str:
        if self._done or not chunk:
            return ""
        self._buffer += chunk

        if self._state == "search":
            marker = '"reply"'
            index = self._buffer.find(marker)
            if index < 0:
                if len(self._buffer) > 8192:
                    self._done = True
                    self._buffer = ""
                return ""
            after = self._buffer[index + len(marker) :]
            prefix = re.match(r"\s*:\s*", after)
            if not prefix:
                return ""
            after = after[prefix.end() :]
            if not after:
                return ""
            if after[0] != '"':
                self._done = True
                return ""
            self._buffer = after[1:]
            self._state = "reply"

        if self._state != "reply":
            return ""
        return self._drain()

    def _drain(self) -> str:
        buffer = self._buffer
        out: list[str] = []
        index = 0
        size = len(buffer)
        while index < size:
            char = buffer[index]
            if char == '"':
                self._state = "done"
                self._done = True
                self._buffer = ""
                return "".join(out)
            if char == "\\":
                if index + 1 >= size:
                    break
                nxt = buffer[index + 1]
                if nxt == "u":
                    if index + 6 > size:
                        break
                    hex_part = buffer[index + 2 : index + 6]
                    try:
                        out.append(chr(int(hex_part, 16)))
                    except ValueError:
                        out.append("\\u" + hex_part)
                    index += 6
                    continue
                out.append(_ESCAPE_MAP.get(nxt, nxt))
                index += 2
                continue
            out.append(char)
            index += 1

        self._buffer = buffer[index:]
        return "".join(out)
