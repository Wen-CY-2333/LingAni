import jwt
from jwt import InvalidTokenError

from app.config import settings
from app.security.context import RequestIdentity

ALLOWED_SCOPE = "agent"


class AgentJwtError(Exception):
    """Agent 委托令牌缺失、伪造或已过期。"""


def _signing_key() -> str:
    secret = settings.agent_jwt_secret
    if len(secret.encode("utf-8")) < 32:
        raise RuntimeError("AGENT_JWT_SECRET 长度至少需要 32 字节")
    return secret


def verify_agent_jwt(token: str) -> RequestIdentity:
    """校验后端签发的 Agent 委托令牌（短期委托凭据），并转换为请求内可信身份。

    与后端 UserJwtService / AgentJwtService 对称：同属 HS256 JWT，
    区别在于本服务只校验不签发，且只接受 audience=lingani-agent、scope=agent 的短期令牌。
    """
    if not token:
        raise AgentJwtError("缺少委托令牌")

    try:
        claims = jwt.decode(
            token,
            _signing_key(),
            algorithms=["HS256"],
            audience=settings.agent_jwt_audience,
            issuer=settings.agent_jwt_issuer,
            options={"require": ["exp", "iat", "iss", "aud", "sub"]},
        )
    except InvalidTokenError as exc:
        raise AgentJwtError("委托令牌无效或已过期，请重新登录后再试") from exc

    if claims.get("scope") != ALLOWED_SCOPE:
        raise AgentJwtError("委托令牌用途不正确")

    user_id = str(claims.get("sub") or "").strip()
    if not user_id.isdigit():
        raise AgentJwtError("委托令牌缺少有效用户标识")

    display_name = claims.get("nickname") or claims.get("username") or None
    return RequestIdentity(
        user_id=user_id,
        display_name=str(display_name) if display_name else None,
        agent_jwt=token,
    )
