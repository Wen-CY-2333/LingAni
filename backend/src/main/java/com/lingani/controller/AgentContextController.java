package com.lingani.controller;

import com.lingani.common.Result;
import com.lingani.security.AgentJwtService;
import com.lingani.security.LoginUser;
import com.lingani.security.SecurityUtils;
import com.lingani.vo.AgentContextVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agent 服务上下文", description = "为 Python Agent 服务签发短期、限定 audience 的用户身份上下文")
@RestController
@RequestMapping("/api/internal/agent")
@RequiredArgsConstructor
public class AgentContextController {

    private final AgentJwtService agentJwtService;

    /**
     * 用户 JWT 校验通过后，签发仅用于本次 Agent 调用的短期委托令牌。
     */
    @PostMapping("/context")
    public Result<AgentContextVO> context() {
        LoginUser user = SecurityUtils.currentUser();
        String token = agentJwtService.issue(user);
        return Result.success(new AgentContextVO(token, agentJwtService.getTtlSeconds()));
    }
}
