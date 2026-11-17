import json
import re
from typing import Annotated, Any

from langchain_core.tools import tool
from pydantic import Field

from app.services.backend_client import BackendError, backend_client

WEEKDAY_LABELS = {1: "周一", 2: "周二", 3: "周三", 4: "周四", 5: "周五", 6: "周六", 7: "周日"}
_NUMERIC_ID = re.compile(r"^\d{1,20}$")


def _dump(payload: Any) -> str:
    return json.dumps(payload, ensure_ascii=False, separators=(",", ":"), default=str)


def _id_of(item: dict) -> str:
    return str(item.get("video_id") or item.get("id") or "")


def _clip(text: Any, limit: int = 300) -> str:
    value = "" if text is None else str(text).replace("\r", " ").replace("\n", " ").strip()
    return value if len(value) <= limit else value[:limit] + "…"


def _tags_of(item: dict) -> list[str]:
    tags = item.get("tags") or []
    return [str(tag) for tag in tags[:6]]


def _video_brief(item: dict) -> dict:
    return {
        "video_id": _id_of(item),
        "title": item.get("title") or "",
        "year": item.get("year") or "",
        "area": item.get("area") or "",
        "score": item.get("score"),
        "remarks": item.get("remarks") or "",
        "total": item.get("total"),
        "tags": _tags_of(item),
    }


def _video_list(items: list[dict], limit: int) -> list[dict]:
    return [_video_brief(item) for item in items[:limit] if isinstance(item, dict)]


def _rank_brief(item: dict, rank: int) -> dict:
    return {
        "rank": rank,
        "video_id": _id_of(item),
        "title": item.get("title") or "",
        "year": item.get("year") or "",
        "score": item.get("score"),
    }


def _normalize_video_id(value: str | int, label: str = "动漫 ID") -> str:
    text = str(value or "").strip()
    if not _NUMERIC_ID.match(text):
        raise ValueError(f"{label} 必须是纯数字（来自搜索结果中的 video_id）")
    return text


def _normalize_page(page: int, size: int, max_size: int) -> tuple[int, int]:
    page = max(1, int(page))
    size = max(1, min(int(size), max_size))
    return page, size


@tool
async def search_anime(
    q: Annotated[str, Field(description="搜索关键词，例如动漫名称、角色名或题材，如“银魂”“青春恋爱”")],
    page: Annotated[int, Field(description="页码，从 1 开始，默认 1")] = 1,
    page_size: Annotated[int, Field(description="每页数量，默认 12，最大 50")] = 12,
) -> str:
    """按关键词搜索站内动漫。用户想找某部番、按名称/题材检索时优先调用。
    返回匹配的动漫列表（video_id、标题、年份、地区、评分、备注、集数、标签）和总数。
    列表中不存在的动漫不要编造。"""
    keyword = str(q or "").strip()
    if not keyword:
        raise ValueError("搜索关键词不能为空")
    page, page_size = _normalize_page(page, page_size, 50)
    data = await backend_client.get(
        "/api/cycani/videos/search",
        params={"q": keyword, "page": page, "pageSize": page_size},
    )
    records = (data or {}).get("records") or []
    total = (data or {}).get("total", len(records))
    current = (data or {}).get("current", page)
    return _dump({
        "keyword": keyword,
        "page": current,
        "total": total,
        "items": _video_list(records, page_size),
    })


@tool
async def get_anime_detail(
    video_id: Annotated[str | int, Field(description="动漫 ID，必须是搜索结果中返回的 video_id，纯数字")],
) -> str:
    """获取单部动漫的详细信息：简介、评分、年份、地区、状态、类型标签、总集数、更新备注、声优与制作信息等。
    用户追问某部动漫的剧情、评分、更新时间、制作阵容时调用。"""
    vid = _normalize_video_id(video_id)
    data = await backend_client.get(f"/api/cycani/videos/{vid}")
    if not isinstance(data, dict):
        raise BackendError("未找到该动漫")
    return _dump({
        "video_id": _id_of(data),
        "title": data.get("title") or "",
        "english_title": data.get("english_title") or "",
        "subtitle": data.get("subtitle") or "",
        "year": data.get("year") or "",
        "area": data.get("area") or "",
        "language": data.get("language") or "",
        "state": data.get("state") or "",
        "score": data.get("score"),
        "score_num": data.get("score_num"),
        "total": data.get("total"),
        "completed": data.get("completed"),
        "remarks": data.get("remarks") or "",
        "weekday": data.get("weekday"),
        "publish_date": data.get("publish_date") or "",
        "tags": _tags_of(data),
        "categories": data.get("categories") or [],
        "director": (data.get("director") or [])[:5],
        "actor": (data.get("actor") or [])[:8],
        "description": _clip(data.get("description"), 400),
        "play_from": data.get("play_from") or [],
    })


@tool
async def get_anime_sections(
    video_id: Annotated[str | int, Field(description="动漫 ID，来自搜索结果的 video_id")],
    page: Annotated[int, Field(description="页码，默认 1")] = 1,
    page_size: Annotated[int, Field(description="每页章节数，默认 50，最大 50")] = 50,
) -> str:
    """获取某部动漫的章节（剧集）列表，返回章节 id、标题、总数。
    用户问“更新到第几集”“有哪些集数”时调用；想看某集播放地址时先把对应章节 id 告知用户。"""
    vid = _normalize_video_id(video_id)
    page, page_size = _normalize_page(page, page_size, 50)
    data = await backend_client.get(
        f"/api/cycani/videos/{vid}/sections",
        params={"page": page, "pageSize": page_size},
    )
    records = (data or {}).get("records") or []
    total = (data or {}).get("total", len(records))
    current = (data or {}).get("current", page)
    return _dump({
        "video_id": vid,
        "page": current,
        "total": total,
        "sections": [{"section_id": str(s.get("id")), "title": s.get("title") or ""} for s in records[:page_size]],
    })


@tool
async def get_anime_recommendations(
    video_id: Annotated[str | int, Field(description="动漫 ID，来自搜索结果的 video_id")],
    limit: Annotated[int, Field(description="推荐数量，默认 10，最大 50")] = 10,
) -> str:
    """获取与指定动漫相似的作品推荐。用户看完某部番想找同类、问“有没有类似的”时调用。"""
    vid = _normalize_video_id(video_id)
    limit = max(1, min(int(limit), 50))
    data = await backend_client.get(
        f"/api/cycani/videos/{vid}/recommendations",
        params={"limit": limit},
    )
    records = (data or {}).get("records") or []
    return _dump({"video_id": vid, "items": _video_list(records, limit)})


@tool
async def get_home_recommend() -> str:
    """获取灵漫首页推荐内容，按分区（如 TV 番组、剧场版）返回热门动漫。
    用户没有明确目标、想随便看看热门推荐时调用。"""
    data = await backend_client.get("/api/cycani/index/recommend")
    groups = (data or {}).get("records") or []
    return _dump({
        "groups": [
            {
                "group": group.get("name") or "",
                "link_type": group.get("link_type") or "",
                "total": len(group.get("videos") or []),
                "items": _video_list(group.get("videos") or [], 12),
            }
            for group in groups[:6]
            if isinstance(group, dict)
        ]
    })


@tool
async def get_weekday_schedule(
    weekday: Annotated[int | None, Field(description="可选，只查看某一天：1=周一 … 7=周日；不传则返回整周")] = None,
) -> str:
    """获取追番周表，即每周各天更新哪些动漫。用户问“今天有什么更新”“周三更新什么”“本周新番表”时调用。
    可按 weekday 过滤某一天；返回每天的更新列表。"""
    data = await backend_client.get("/api/cycani/index/weekday")
    groups = (data or {}).get("records") or []
    days = []
    for group in groups:
        if not isinstance(group, dict):
            continue
        day = group.get("weekday")
        if weekday is not None and day != int(weekday):
            continue
        videos = group.get("videos") or []
        days.append({
            "weekday": day,
            "label": WEEKDAY_LABELS.get(day, f"星期{day}"),
            "total": len(videos),
            "items": _video_list(videos, 10),
        })
    days.sort(key=lambda item: item["weekday"] or 0)
    if weekday is not None and not days:
        raise ValueError("weekday 必须是 1-7 之间的整数")
    return _dump({"days": days})


@tool
async def get_rank_list() -> str:
    """获取排行榜的榜单列表，并附上每个榜单当前的前 5 名（含名次、标题、评分）。
    用户问"第一名是谁""榜上有哪些动漫""看看排行榜"时调用，返回的 top 顺序就是名次，第一条为第一名。
    需要查看某个榜单的更多名次时，再用 rank_id 调用 get_rank_videos。"""
    data = await backend_client.get("/api/cycani/ranks")
    records = (data or {}).get("records") or (data or {}).get("list") or []
    ranks = []
    for item in records:
        if not isinstance(item, dict):
            continue
        rank_id = str(item.get("id") or "")
        top: list[dict] = []
        if _NUMERIC_ID.match(rank_id):
            try:
                detail = await backend_client.get(f"/api/cycani/ranks/{rank_id}/videos")
                videos = (detail or {}).get("records") or (detail or {}).get("list") or []
                top = [_rank_brief(video, index + 1)
                       for index, video in enumerate(videos[:5]) if isinstance(video, dict)]
            except Exception:
                top = []
        ranks.append({
            "rank_id": rank_id,
            "name": item.get("name") or "",
            "type": item.get("type") or "",
            "video_count": item.get("video_count"),
            "top": top,
        })
    return _dump({"ranks": ranks})


@tool
async def get_rank_videos(
    rank_id: Annotated[str | int, Field(description="榜单 ID，必须来自 get_rank_list 返回的 rank_id")],
) -> str:
    """获取指定榜单的上榜动漫列表。用户问“TV 榜有哪些”“排行榜第一是什么”时调用，
    rank_id 必须先通过 get_rank_list 获取，不要猜测。"""
    rid = str(rank_id or "").strip()
    if not _NUMERIC_ID.match(rid):
        raise ValueError("榜单 ID 必须是纯数字，且来自 get_rank_list 的返回结果")
    data = await backend_client.get(f"/api/cycani/ranks/{rid}/videos")
    records = (data or {}).get("records") or (data or {}).get("list") or []
    return _dump({"rank_id": rid, "items": _video_list(records, 20)})


ANIME_TOOLS = [
    search_anime,
    get_anime_detail,
    get_anime_sections,
    get_anime_recommendations,
    get_home_recommend,
    get_weekday_schedule,
    get_rank_list,
    get_rank_videos,
]
