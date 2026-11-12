package com.lingani.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingani.common.BizException;
import com.lingani.dto.LoginRequest;
import com.lingani.dto.RegisterRequest;
import com.lingani.entity.SysUser;
import com.lingani.mapper.SysUserMapper;
import com.lingani.security.JwtUtil;
import com.lingani.security.TokenBlacklistService;
import com.lingani.vo.LoginVO;
import com.lingani.vo.UserVO;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public UserVO register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BizException("两次输入的密码不一致");
        }

        String username = request.getUsername().trim();
        long exists = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (exists > 0) {
            throw BizException.conflict("用户名已被占用");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        String nickname = StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : username;
        user.setNickname(nickname);
        user.setStatus(1);
        user.setTokenVersion(0);
        user.setDeleted(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw BizException.conflict("用户名已被占用");
        }
        return toUserVO(user);
    }

    public LoginVO login(LoginRequest request, String clientIp) {
        String username = request.getUsername().trim();
        String attemptKey = username + "|" + clientIp;
        loginAttemptService.checkAllowed(attemptKey);

        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            loginAttemptService.recordFailure(attemptKey);
            throw BizException.unauthorized("用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailure(attemptKey);
            throw BizException.unauthorized("用户名或密码错误");
        }

        loginAttemptService.reset(attemptKey);
        int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), tokenVersion);
        return LoginVO.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpireSeconds())
                .user(toUserVO(user))
                .build();
    }

    public void logout(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        try {
            Claims claims = jwtUtil.parse(token);
            if (claims.getExpiration() != null) {
                tokenBlacklistService.revoke(claims.getId(), claims.getExpiration().toInstant());
            }
        } catch (Exception ignored) {
            // Token 已失效时退出登录仍然返回成功，前端清理本地状态即可
        }
    }

    public static UserVO toUserVO(SysUser user) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .status(user.getStatus())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
