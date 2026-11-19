import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

ROOT_DIR = Path(__file__).resolve().parent.parent
load_dotenv(ROOT_DIR / ".env")


def _env(name: str, default: str | None = None) -> str:
    value = os.getenv(name, default)
    if value is None or value == "":
        raise RuntimeError(f"缺少必需的环境变量：{name}，请在 .env 中配置")
    return value


@dataclass(frozen=True)
class Settings:
    cors_origins: tuple[str, ...]

    llm_provider: str
    llm_temperature: float
    llm_timeout_seconds: float

    ollama_base_url: str
    ollama_model: str

    deepseek_api_key: str
    deepseek_base_url: str
    deepseek_model: str

    backend_base_url: str
    backend_timeout_seconds: float

    agent_jwt_audience: str
    agent_jwt_issuer: str
    agent_jwt_secret: str
    agent_jwt_ttl_seconds: int

    max_history_messages: int

    @property
    def model_name(self) -> str:
        return self.deepseek_model if self.llm_provider == "deepseek" else self.ollama_model


_llm_provider = os.getenv("LLM_PROVIDER", "deepseek").strip().lower()
_deepseek_api_key = os.getenv("DEEPSEEK_API_KEY", "").strip()
_ollama_base_url = os.getenv("OLLAMA_BASE_URL", "").strip()
if _llm_provider == "deepseek" and not _deepseek_api_key:
    raise RuntimeError("LLM_PROVIDER=deepseek 时必须配置 DEEPSEEK_API_KEY")
if _llm_provider == "ollama" and not _ollama_base_url:
    raise RuntimeError("LLM_PROVIDER=ollama 时必须配置 OLLAMA_BASE_URL")


settings = Settings(
    cors_origins=("http://localhost:5173", "http://127.0.0.1:5173"),
    llm_provider=_llm_provider,
    llm_temperature=0.3,
    llm_timeout_seconds=120,
    # 默认为空：使用 Ollama 时在 .env 中填写实际可访问地址
    ollama_base_url=_ollama_base_url,
    ollama_model=os.getenv("OLLAMA_MODEL", "qwen2.5:7b"),
    deepseek_api_key=_deepseek_api_key,
    deepseek_base_url="https://api.deepseek.com",
    deepseek_model="deepseek-flash",
    # 本地为 localhost:8080；Docker 中由 compose 注入 http://backend:8080
    backend_base_url=os.getenv("BACKEND_BASE_URL", "http://localhost:8080").rstrip("/"),
    backend_timeout_seconds=15,
    # 需与后端 application.yml 中 lingani.jwt.agent 的固定值保持一致
    agent_jwt_audience="lingani-agent",
    agent_jwt_issuer="lingani-backend",
    agent_jwt_secret=_env("AGENT_JWT_SECRET"),
    agent_jwt_ttl_seconds=300,
    max_history_messages=20,
)
