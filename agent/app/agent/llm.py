from langchain_core.language_models.chat_models import BaseChatModel
from langchain_ollama import ChatOllama
from langchain_openai import ChatOpenAI

from app.config import settings


def build_chat_model(streaming: bool = False, json_mode: bool = False) -> BaseChatModel:
    """按 LLM_PROVIDER 创建对话模型；json_mode 仅用于结构化输出器。"""
    if settings.llm_provider == "deepseek":
        return ChatOpenAI(
            model=settings.deepseek_model,
            api_key=settings.deepseek_api_key,
            base_url=settings.deepseek_base_url,
            temperature=settings.llm_temperature,
            timeout=settings.llm_timeout_seconds,
            max_retries=1,
            streaming=streaming,
            model_kwargs={"response_format": {"type": "json_object"}} if json_mode else {},
        )
    return ChatOllama(
        model=settings.ollama_model,
        base_url=settings.ollama_base_url,
        temperature=settings.llm_temperature,
        num_ctx=8192,
        streaming=streaming,
        format="json" if json_mode else None,
    )
