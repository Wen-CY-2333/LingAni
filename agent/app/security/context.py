from dataclasses import dataclass
from contextvars import ContextVar


@dataclass(frozen=True)
class RequestIdentity:
    """由 Spring Boot 签发的可信身份，仅在当前请求内有效。

    agent_jwt 为短期委托凭据（AGENT JWT），个人数据工具回调后端时原样回传。
    """

    user_id: str
    display_name: str | None
    agent_jwt: str

    def __repr__(self) -> str:
        return f"RequestIdentity(user_id={self.user_id!r}, display_name={self.display_name!r})"


_identity_var: ContextVar[RequestIdentity | None] = ContextVar("request_identity", default=None)


def set_identity(identity: RequestIdentity) -> None:
    _identity_var.set(identity)


def reset_identity() -> None:
    _identity_var.set(None)


def current_identity() -> RequestIdentity | None:
    return _identity_var.get()


def require_identity() -> RequestIdentity:
    identity = _identity_var.get()
    if identity is None:
        raise PermissionError("当前请求缺少可信用户身份，个人数据工具不可用")
    return identity
