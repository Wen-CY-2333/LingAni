package com.lingani.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 返回给前端的 Agent 服务身份上下文：短期令牌 + 有效期（秒）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentContextVO {

    private String token;

    private long expiresIn;
}
