package com.lingani.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 放入 SecurityContext 的当前登录用户信息，userId 只从 JWT 解析得到。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private Integer role;

    public boolean isAdmin() {
        return role != null && role == 1;
    }
}
