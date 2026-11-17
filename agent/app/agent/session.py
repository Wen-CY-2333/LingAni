import re
import threading
from collections.abc import Sequence

from langchain_core.messages import AIMessage, AnyMessage, ToolMessage

THREAD_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]{8,128}$")


class SessionOwnershipError(Exception):
    """会话不属于当前用户。"""


def validate_thread_id(thread_id: str) -> str:
    if not isinstance(thread_id, str) or not THREAD_ID_PATTERN.match(thread_id):
        raise ValueError("thread_id 格式不合法：仅允许 8-128 位字母、数字、下划线和短横线")
    return thread_id


class SessionRegistry:
    """会话归属登记表：thread_id 只是会话标识，不能作为身份凭证。"""

    def __init__(self) -> None:
        self._owners: dict[str, str] = {}
        self._lock = threading.Lock()

    def bind_or_verify(self, thread_id: str, user_id: str) -> None:
        with self._lock:
            owner = self._owners.get(thread_id)
            if owner is None:
                self._owners[thread_id] = user_id
                return
            if owner != user_id:
                raise SessionOwnershipError("该会话不属于当前用户")

    def drop(self, thread_id: str) -> None:
        with self._lock:
            self._owners.pop(thread_id, None)


def trim_messages(messages: Sequence[AnyMessage], limit: int) -> list[AnyMessage]:
    """仅保留最近若干条消息，并避免把工具结果与其调用消息拆开。"""
    if limit <= 0 or len(messages) <= limit:
        window = list(messages)
    else:
        window = list(messages[-limit:])
    while window and isinstance(window[0], ToolMessage):
        window.pop(0)
    if window and isinstance(window[-1], AIMessage) and window[-1].tool_calls:
        window.pop()
    return window
