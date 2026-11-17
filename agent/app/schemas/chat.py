from pydantic import BaseModel, Field


class PageContext(BaseModel):
    """用户当前页面上下文，仅用于判断所在页面与观看位置，不作为身份凭证。"""

    page: str | None = Field(default=None, description="页面类型：play 播放页 / detail 详情页")
    current_video_id: str | None = Field(default=None, description="当前作品 ID")
    current_section_id: str | None = Field(default=None, description="当前集 ID")
    progress: int | None = Field(default=None, ge=0, description="当前集已播放秒数")


class ChatRequest(BaseModel):
    """对话请求：前端只传自然语言、会话标识与页面上下文，不传任何用户身份。"""

    message: str = Field(min_length=1, max_length=4000, description="用户消息")

    thread_id: str | None = Field(default=None, description="会话标识，不传则新建会话")

    context: PageContext | None = Field(default=None, description="当前页面上下文")


class ChatResponse(BaseModel):
    reply: str
    thread_id: str
    works: list[dict] = Field(default_factory=list)
    actions: list[dict] = Field(default_factory=list)
