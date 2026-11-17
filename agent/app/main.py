import json
import logging
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Header, HTTPException
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse

from app.agent.graph import AnimeAgent
from app.agent.session import SessionOwnershipError, validate_thread_id
from app.config import settings
from app.schemas.chat import ChatRequest, ChatResponse
from app.security.context import RequestIdentity, reset_identity, set_identity
from app.security.agent_jwt import AgentJwtError, verify_agent_jwt
from app.services.backend_client import BackendError, backend_client
from app.tools.anime import ANIME_TOOLS
from app.tools.user import USER_TOOLS

logger = logging.getLogger("lingani.agent")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")

agent = AnimeAgent([*ANIME_TOOLS, *USER_TOOLS])


@asynccontextmanager
async def lifespan(_: FastAPI):
    logger.info("灵漫 AI Agent 已启动，模型：%s（%s）", settings.model_name, settings.llm_provider)
    yield
    await backend_client.close()


app = FastAPI(title="灵漫 AI Agent", version="1.0.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=list(settings.cors_origins),
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "Accept"],
)


def _error(status_code: int, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={"code": status_code, "msg": message, "data": None},
    )


@app.exception_handler(HTTPException)
async def handle_http_exception(_, exc: HTTPException) -> JSONResponse:
    return _error(exc.status_code, str(exc.detail))


@app.exception_handler(RequestValidationError)
async def handle_validation_error(_, __: RequestValidationError) -> JSONResponse:
    return _error(422, "请求参数格式不正确")


def _require_identity(authorization: str | None) -> RequestIdentity:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="请先登录后再使用小灵")
    token = authorization[7:].strip()
    try:
        return verify_agent_jwt(token)
    except AgentJwtError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc


def _page_context(payload: ChatRequest) -> dict:
    """只透传合法的播放位置，避免把任意文本带进提示词。"""
    context = payload.context
    if not context:
        return {}
    data: dict = {}
    page = str(getattr(context, "page", "") or "").strip()
    if page in ("play", "detail"):
        data["page"] = page
    for key in ("current_video_id", "current_section_id"):
        value = str(getattr(context, key) or "").strip()
        if value.isdigit():
            data[key] = value
    if context.progress is not None:
        data["progress"] = int(context.progress)
    return data


def _prepare_chat(payload: ChatRequest, authorization: str | None) -> tuple[RequestIdentity, str, str]:
    identity = _require_identity(authorization)

    message = payload.message.strip()
    if not message:
        raise HTTPException(status_code=422, detail="消息不能为空")

    thread_id = (payload.thread_id or uuid.uuid4().hex).strip()
    try:
        agent.ensure_session(thread_id, identity.user_id)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except SessionOwnershipError as exc:
        raise HTTPException(status_code=403, detail=str(exc)) from exc

    return identity, message, thread_id


def _sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


@app.get("/health")
async def health() -> dict:
    return {"status": "ok", "service": "lingani-agent", "model": settings.model_name}


@app.post("/api/agent/chat", response_model=ChatResponse)
async def chat(payload: ChatRequest, authorization: str | None = Header(default=None)) -> ChatResponse:
    identity, message, thread_id = _prepare_chat(payload, authorization)

    set_identity(identity)
    try:
        output = await agent.ainvoke(
            thread_id=thread_id,
            user_id=identity.user_id,
            message=message,
            display_name=identity.display_name,
            user_context=_page_context(payload),
        )
    except SessionOwnershipError as exc:
        raise HTTPException(status_code=403, detail=str(exc)) from exc
    except BackendError as exc:
        logger.warning("Agent 调用后端失败：%s", exc.message)
        raise HTTPException(status_code=502, detail="灵漫后端服务暂时不可用，请稍后再试") from exc
    except Exception:
        logger.exception("Agent 调用失败")
        raise HTTPException(
            status_code=503,
            detail="小灵暂时无法回答：模型服务可能未启动或不可用，请稍后再试",
        ) from None
    finally:
        reset_identity()

    return ChatResponse(
        reply=output.reply,
        thread_id=thread_id,
        works=output.works,
        actions=output.actions,
    )


@app.post("/api/agent/chat/stream")
async def chat_stream(payload: ChatRequest, authorization: str | None = Header(default=None)):
    identity, message, thread_id = _prepare_chat(payload, authorization)

    async def event_stream():
        set_identity(identity)
        try:
            yield _sse("meta", {"thread_id": thread_id})
            async for kind, item in agent.astream(
                thread_id=thread_id,
                user_id=identity.user_id,
                message=message,
                display_name=identity.display_name,
                user_context=_page_context(payload),
            ):
                if kind == "token":
                    yield _sse("token", {"text": item})
                elif kind == "tool_call":
                    yield _sse("tool", {"name": item.get("name"), "args": item.get("args")})
                elif kind == "output":
                    yield _sse(
                        "done",
                        {
                            "thread_id": thread_id,
                            "works": item.works,
                            "actions": item.actions,
                        },
                    )
        except SessionOwnershipError as exc:
            yield _sse("error", {"code": 403, "msg": str(exc)})
        except BackendError as exc:
            logger.warning("流式对话调用后端失败：%s", exc.message)
            yield _sse("error", {"code": 502, "msg": "灵漫后端服务暂时不可用，请稍后再试"})
        except Exception:
            logger.exception("流式对话失败")
            yield _sse(
                "error",
                {"code": 503, "msg": "小灵暂时无法回答：模型服务可能未启动或不可用，请稍后再试"},
            )
        finally:
            reset_identity()

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
