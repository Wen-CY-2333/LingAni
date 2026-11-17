package com.lingani.security;

import com.lingani.entity.SysUser;
import com.lingani.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 解析 X-Agent-JWT 头中的 Agent 委托令牌（AGENT JWT），建立请求级用户身份。
 * <p>
 * 仅对个人数据接口放行：Python Agent 代表已登录用户访问 /api/history/**、/api/favorites/** 时，
 * 必须携带 Spring Boot 签发的短期委托令牌；普通请求头（如 X-User-Id）不会建立任何身份。
 * <p>
 * 与 {@link UserJwtAuthenticationFilter} 对称：一个处理长期身份凭据，一个处理短期委托凭据。
 */
@Component
@RequiredArgsConstructor
public class AgentJwtAuthenticationFilter extends OncePerRequestFilter {

    private final AgentJwtService agentJwtService;
    private final SysUserMapper userMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !(path.startsWith("/api/history") || path.startsWith("/api/favorites"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = request.getHeader(AgentJwtService.HEADER_NAME);
            if (token != null && !token.isBlank()) {
                authenticate(token.trim());
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            Claims claims = agentJwtService.verify(token);
            Long userId = Long.valueOf(claims.getSubject());

            SysUser user = userMapper.selectById(userId);
            if (user == null || user.getStatus() == null || user.getStatus() != 1) {
                return;
            }

            LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getNickname(),
                    user.getAvatar(), user.getRole());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    loginUser, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ignored) {
            // Token 无效或过期时保持匿名，由受保护接口返回 401
        }
    }
}
