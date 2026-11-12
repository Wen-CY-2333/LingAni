package com.lingani.security;

import com.lingani.common.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser currentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }

    public static LoginUser currentUser() {
        LoginUser user = currentUserOrNull();
        if (user == null) {
            throw BizException.unauthorized("请先登录");
        }
        return user;
    }

    public static Long currentUserId() {
        return currentUser().getId();
    }

    /**
     * 管理后台接口统一入口：要求当前登录用户为管理员，否则返回 403。
     */
    public static LoginUser requireAdmin() {
        LoginUser user = currentUser();
        if (!user.isAdmin()) {
            throw BizException.forbidden("需要管理员权限");
        }
        return user;
    }
}
