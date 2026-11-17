import json
import re
from typing import Annotated, Any

from langchain_core.tools import tool
from pydantic import Field

from app.security.context import RequestIdentity, require_identity
from app.services.backend_client import backend_client

_LEADING_NOISE = re.compile(r"^(?:我的|我|咱们|我们|你|请问|帮我|帮忙|查一下|看看|关于)+")
_TRAILING_INTENT = re.compile(
    r"(看到第几集了|看到哪一集了|看到哪了|看到多少|的观看进度|的播放进度|的进度|追了吗|有没有收藏|收藏了吗).*$"
)


def _dump(payload: Any) -> str:
    return json.dumps(payload, ensure_ascii=False, separators=(",", ":"), default=str)


def _identity() -> RequestIdentity:
    # 身份只来自本次请求的可信上下文，绝不接受模型生成或前端传入的 userId
    return require_identity()


def _normalize_video_id(value: str | int) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError("videoId 不能为空")
    if len(text) > 100:
        raise ValueError("videoId 过长")
    return text


async def _resolve_video_id(video_id: str | int | None, anime_title: str | None) -> str:
    """作品名优先用于解析真实 ID；没有作品名时才信任模型给出的纯数字 ID。"""
    title = _clean_title(anime_title)
    if not title and video_id not in (None, ""):
        text = str(video_id).strip()
        if text.isdigit():
            return text
        title = _clean_title(text)

    if not title:
        raise ValueError("需要提供 video_id，或提供作品名 anime_title 由工具解析")

    data = await backend_client.get(
        "/api/cycani/videos/search", params={"q": title, "page": 1, "pageSize": 3}
    )
    items = (data or {}).get("records") or []
    if not items:
        raise ValueError(f"站内没有找到《{title}》，请先确认作品名称")
    return str(items[0].get("video_id") or items[0].get("id") or "")


def _clean_title(raw: str | None) -> str:
    """去掉模型可能从用户原句带出的口语前缀或意图后缀，只保留作品名。"""
    text = str(raw or "").strip().strip("《》\"'“”‘’")
    text = _LEADING_NOISE.sub("", text)
    text = _TRAILING_INTENT.sub("", text)
    return text.strip(" ，,。.？?、")


def _progress_percent(progress: Any, duration: Any) -> float | None:
    try:
        progress_value = float(progress or 0)
        duration_value = float(duration or 0)
    except (TypeError, ValueError):
        return None
    if duration_value <= 0:
        return None
    return round(min(progress_value / duration_value, 1.0) * 100, 1)


def _history_brief(item: dict) -> dict:
    return {
        "video_id": str(item.get("videoId") or ""),
        "anime_title": item.get("animeTitle") or "",
        "episode_title": item.get("episodeTitle") or "",
        "section_id": str(item.get("sectionId") or ""),
        "progress": item.get("progress"),
        "duration": item.get("duration"),
        "progress_percent": _progress_percent(item.get("progress"), item.get("duration")),
        "last_played_at": item.get("lastPlayedAt") or item.get("updatedAt") or "",
    }


def _favorite_brief(item: dict) -> dict:
    return {
        "favorite_id": item.get("id"),
        "video_id": str(item.get("videoId") or ""),
        "anime_title": item.get("animeTitle") or "",
        "anime_cover": item.get("animeCover") or "",
        "created_at": item.get("createdAt") or "",
    }


def _normalize_page(current: int, size: int) -> tuple[int, int]:
    return max(1, int(current)), max(1, min(int(size), 30))


@tool
async def get_user_history(
    current: Annotated[int, Field(description="页码，从 1 开始，默认 1")] = 1,
    size: Annotated[int, Field(description="每页数量，默认 10，最大 30")] = 10,
) -> str:
    """获取当前登录用户的播放历史，按最近观看时间倒序。
    用户问“我最近在看什么”“我的观看记录”“上次看到哪”时调用。
    返回动漫名称、章节标题、观看进度、进度百分比与最近观看时间。用户身份由系统自动带入。"""
    identity = _identity()
    current, size = _normalize_page(current, size)
    data = await backend_client.get(
        "/api/history",
        params={"current": current, "size": size},
        agent_jwt=identity.agent_jwt,
    )
    records = (data or {}).get("records") or []
    return _dump({
        "total": (data or {}).get("total", len(records)),
        "current": (data or {}).get("current", current),
        "size": (data or {}).get("size", size),
        "items": [_history_brief(item) for item in records[:size] if isinstance(item, dict)],
    })


@tool
async def get_user_progress(
    video_id: Annotated[str | int | None, Field(description="动漫 ID，来自搜索结果或历史记录；不知道时留空并传 anime_title")] = None,
    anime_title: Annotated[str | None, Field(description="作品名称，仅在不知道 video_id 时填写，工具会自行解析")] = None,
) -> str:
    """查询当前登录用户在指定动漫上的观看进度，返回每集看到的位置与百分比。
    用户问“我看到第几集了”“这部番我看了多少”时调用。
    优先传真实 video_id；若只有作品名，可传 anime_title，由工具解析，不要编造数字 ID。用户身份由系统自动带入。"""
    identity = _identity()
    vid = await _resolve_video_id(video_id, anime_title)
    data = await backend_client.get(
        f"/api/history/video/{vid}",
        agent_jwt=identity.agent_jwt,
    )
    records = data or []
    items = [_history_brief(item) for item in records if isinstance(item, dict)]
    latest = items[0] if items else None
    return _dump({
        "video_id": vid,
        "watched_episodes": len(items),
        "latest": latest,
        "items": items,
    })


@tool
async def get_user_favorites(
    current: Annotated[int, Field(description="页码，从 1 开始，默认 1")] = 1,
    size: Annotated[int, Field(description="每页数量，默认 12，最大 30")] = 12,
) -> str:
    """获取当前登录用户的追番（收藏）列表，按收藏时间倒序。
    用户问“我的追番”“我收藏了哪些动漫”时调用。返回追番记录 id、动漫 ID、名称、封面与收藏时间。
    用户身份由系统自动带入。"""
    identity = _identity()
    current, size = _normalize_page(current, size)
    data = await backend_client.get(
        "/api/favorites",
        params={"current": current, "size": size},
        agent_jwt=identity.agent_jwt,
    )
    records = (data or {}).get("records") or []
    return _dump({
        "total": (data or {}).get("total", len(records)),
        "current": (data or {}).get("current", current),
        "size": (data or {}).get("size", size),
        "items": [_favorite_brief(item) for item in records[:size] if isinstance(item, dict)],
    })


@tool
async def add_favorite(
    video_id: Annotated[str | int, Field(description="动漫 ID，必须来自搜索/推荐/榜单结果中的 video_id")],
    anime_title: Annotated[str | None, Field(description="动漫名称，建议填写，便于追番列表展示")] = None,
) -> str:
    """把某部动漫加入当前登录用户的追番列表。仅当用户明确要求“追番/收藏/加入追番”时调用，
    普通浏览或询问信息时不要调用。用户身份由系统自动带入，不要向用户索要用户 ID。"""
    identity = _identity()
    vid = _normalize_video_id(video_id)
    title = str(anime_title).strip() if anime_title else None
    if title and len(title) > 255:
        raise ValueError("动漫名称过长")

    data = await backend_client.post(
        "/api/favorites",
        json_body={"videoId": vid, "animeTitle": title},
        agent_jwt=identity.agent_jwt,
    )
    item = data if isinstance(data, dict) else {}
    return _dump({"success": True, "favorite": _favorite_brief(item)})


@tool
async def check_favorite(
    video_id: Annotated[str | int | None, Field(description="动漫 ID，来自搜索结果；不知道时留空并传 anime_title")] = None,
    anime_title: Annotated[str | None, Field(description="作品名称，仅在不知道 video_id 时填写，工具会自行解析")] = None,
) -> str:
    """检查某部动漫是否已在当前登录用户的追番列表中。
    用户问“这部我追了吗”“有没有收藏过”或准备添加追番前确认状态时调用。
    优先传真实 video_id；若只有作品名，可传 anime_title，不要编造数字 ID。用户身份由系统自动带入。"""
    identity = _identity()
    vid = await _resolve_video_id(video_id, anime_title)
    data = await backend_client.get(
        "/api/favorites/check",
        params={"videoId": vid},
        agent_jwt=identity.agent_jwt,
    )
    item = data if isinstance(data, dict) else {}
    return _dump({
        "video_id": vid,
        "favorited": bool(item.get("favorited")),
        "favorite_id": item.get("id"),
    })


USER_TOOLS = [
    get_user_history,
    get_user_progress,
    get_user_favorites,
    add_favorite,
    check_favorite,
]
