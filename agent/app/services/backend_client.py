import logging
from typing import Any

import httpx

from app.config import settings

logger = logging.getLogger(__name__)


class BackendError(Exception):
    """灵漫后端返回的业务或网络错误。"""

    def __init__(self, message: str, status_code: int | None = None) -> None:
        super().__init__(message)
        self.message = message
        self.status_code = status_code


class BackendClient:
    """统一封装对灵漫 Spring Boot 后端的 HTTP 调用。"""

    def __init__(self, base_url: str, timeout_seconds: float) -> None:
        self._base_url = base_url
        self._timeout_seconds = timeout_seconds
        self._client: httpx.AsyncClient | None = None

    def _get_client(self) -> httpx.AsyncClient:
        if self._client is None:
            self._client = httpx.AsyncClient(
                base_url=self._base_url,
                timeout=httpx.Timeout(self._timeout_seconds),
                headers={"Accept": "application/json"},
            )
        return self._client

    async def close(self) -> None:
        if self._client is not None:
            await self._client.aclose()
            self._client = None

    async def request(
        self,
        method: str,
        path: str,
        *,
        params: dict[str, Any] | None = None,
        json_body: dict[str, Any] | None = None,
        agent_jwt: str | None = None,
    ) -> Any:
        headers: dict[str, str] = {}
        # 个人数据接口只接受 Spring Boot 签发的 Agent 委托令牌，不使用用户 JWT 或用户 ID 头
        if agent_jwt:
            headers["X-Agent-JWT"] = agent_jwt

        try:
            response = await self._get_client().request(
                method, path, params=params, json=json_body, headers=headers
            )
        except httpx.TimeoutException as exc:
            logger.warning("后端请求超时：%s %s", method, path)
            raise BackendError("后端服务响应超时，请稍后再试") from exc
        except httpx.HTTPError as exc:
            logger.warning("后端连接失败：%s %s (%s)", method, path, type(exc).__name__)
            raise BackendError("无法连接灵漫后端服务，请确认后端已启动") from exc

        logger.debug("后端响应：%s %s -> %s", method, path, response.status_code)
        return self._unwrap(response)

    @staticmethod
    def _unwrap(response: httpx.Response) -> Any:
        try:
            body = response.json()
        except ValueError:
            body = None

        if response.status_code == 401:
            raise BackendError("登录状态已失效，请重新登录", 401)
        if response.status_code == 403:
            raise BackendError("没有权限执行该操作", 403)
        if response.status_code >= 500:
            raise BackendError("后端服务暂时不可用，请稍后再试", response.status_code)
        if response.status_code >= 400:
            message = body.get("msg") if isinstance(body, dict) else None
            raise BackendError(message or f"请求失败（HTTP {response.status_code}）", response.status_code)
        if not isinstance(body, dict) or "code" not in body:
            raise BackendError("后端返回了无法识别的数据格式")
        if body.get("code") != 200:
            raise BackendError(str(body.get("msg") or "请求失败"), body.get("code"))
        return body.get("data")

    async def get(
        self,
        path: str,
        *,
        params: dict[str, Any] | None = None,
        agent_jwt: str | None = None,
    ) -> Any:
        return await self.request("GET", path, params=params, agent_jwt=agent_jwt)

    async def post(
        self,
        path: str,
        *,
        json_body: dict[str, Any] | None = None,
        params: dict[str, Any] | None = None,
        agent_jwt: str | None = None,
    ) -> Any:
        return await self.request(
            "POST", path, json_body=json_body, params=params, agent_jwt=agent_jwt
        )


backend_client = BackendClient(settings.backend_base_url, settings.backend_timeout_seconds)
