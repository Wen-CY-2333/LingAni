from typing import Annotated, Any

from langgraph.graph.message import add_messages
from typing_extensions import TypedDict


class AgentState(TypedDict, total=False):
    """Agent 运行状态：消息历史 + 可信身份上下文。"""

    messages: Annotated[list, add_messages]
    user_id: str
    display_name: str
    request_id: str
    user_context: dict[str, Any]
