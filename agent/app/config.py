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


def _env_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None or raw == "":
        return default
    try:
        return int(raw)
    except ValueError as exc:
        raise RuntimeError(f"环境变量 {name} 必须是整数") from exc


def _env_float(name: str, default: float) -> float:
    raw = os.getenv(name)
    if raw is None or raw == "":
        return default
    try:
        return float(raw)
    except ValueError as exc:
        raise RuntimeError(f"环境变量 {name} 必须是数字") from exc


@dataclass(frozen=True)
class Settings:
    agent_host: str
    agent_port: int
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
if _llm_provider == "deepseek" and not _deepseek_api_key:
    raise RuntimeError("LLM_PROVIDER=deepseek 时必须配置 DEEPSEEK_API_KEY")


settings = Settings(
    agent_host=os.getenv("AGENT_HOST", "127.0.0.1"),
    agent_port=_env_int("AGENT_PORT", 8000),
    cors_origins=tuple(
        origin.strip()
        for origin in os.getenv(
            "AGENT_CORS_ORIGINS", "http://localhost:5173,http://127.0.0.1:5173"
        ).split(",")
        if origin.strip()
    ),
    llm_provider=_llm_provider,
    llm_temperature=_env_float("LLM_TEMPERATURE", _env_float("OLLAMA_TEMPERATURE", 0.3)),
    llm_timeout_seconds=_env_float("LLM_TIMEOUT_SECONDS", _env_float("OLLAMA_TIMEOUT_SECONDS", 120)),
    ollama_base_url=os.getenv("OLLAMA_BASE_URL", "http://localhost:11434"),
    ollama_model=os.getenv("OLLAMA_MODEL", "qwen2.5:7b"),
    deepseek_api_key=_deepseek_api_key,
    deepseek_base_url=os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com").rstrip("/"),
    deepseek_model=os.getenv("DEEPSEEK_MODEL", "deepseek-chat"),
    backend_base_url=os.getenv("BACKEND_BASE_URL", "http://localhost:8080").rstrip("/"),
    backend_timeout_seconds=_env_float("BACKEND_TIMEOUT_SECONDS", 15),
    agent_jwt_audience=os.getenv("AGENT_JWT_AUDIENCE", "lingani-agent"),
    agent_jwt_issuer=os.getenv("AGENT_JWT_ISSUER", "lingani-backend"),
    agent_jwt_secret=_env("AGENT_JWT_SECRET"),
    agent_jwt_ttl_seconds=_env_int("AGENT_JWT_TTL_SECONDS", 300),
    max_history_messages=_env_int("AGENT_MAX_HISTORY_MESSAGES", 20),
)
