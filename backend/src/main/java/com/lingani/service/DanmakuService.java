package com.lingani.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingani.common.BizException;
import com.lingani.dto.DanmakuCreateRequest;
import com.lingani.entity.AnimeDanmaku;
import com.lingani.entity.SysUser;
import com.lingani.mapper.AnimeDanmakuMapper;
import com.lingani.mapper.SysUserMapper;
import com.lingani.security.LoginUser;
import com.lingani.security.SecurityUtils;
import com.lingani.vo.DanmakuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DanmakuService {

    private static final int STATUS_NORMAL = 0;
    private static final int STATUS_HIDDEN = 1;

    private final AnimeDanmakuMapper danmakuMapper;
    private final SysUserMapper userMapper;
    private final DanmakuRateLimitService rateLimitService;

    public List<DanmakuVO> list(String sectionId) {
        if (!StringUtils.hasText(sectionId)) {
            throw new BizException("sectionId 不能为空");
        }
        List<AnimeDanmaku> list = danmakuMapper.selectList(Wrappers.<AnimeDanmaku>lambdaQuery()
                .eq(AnimeDanmaku::getSectionId, sectionId.trim())
                .eq(AnimeDanmaku::getStatus, STATUS_NORMAL)
                .orderByAsc(AnimeDanmaku::getTime)
                .orderByAsc(AnimeDanmaku::getId));

        Set<Long> userIds = list.stream().map(AnimeDanmaku::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> users = loadUsers(userIds);
        return list.stream().map(danmaku -> toVO(danmaku, users)).toList();
    }

    @Transactional
    public DanmakuVO create(DanmakuCreateRequest request) {
        LoginUser loginUser = SecurityUtils.currentUser();
        rateLimitService.checkAllowed(loginUser.getId());

        String text = request.getText().trim();
        if (text.isEmpty()) {
            throw new BizException("弹幕内容不能为空");
        }
        if (text.length() > 200) {
            throw new BizException("弹幕最多 200 个字符");
        }

        AnimeDanmaku danmaku = new AnimeDanmaku();
        danmaku.setUserId(loginUser.getId());
        danmaku.setSectionId(request.getSectionId().trim());
        danmaku.setTime(request.getTime().setScale(3, java.math.RoundingMode.HALF_UP));
        danmaku.setText(text);
        danmaku.setColor(request.getColor());
        danmaku.setMode(request.getMode());
        danmaku.setStatus(STATUS_NORMAL);
        danmakuMapper.insert(danmaku);
        rateLimitService.record(loginUser.getId());

        return DanmakuVO.builder()
                .id(danmaku.getId())
                .userId(loginUser.getId())
                .username(loginUser.getUsername())
                .nickname(loginUser.getNickname())
                .text(text)
                .time(danmaku.getTime())
                .color(request.getColor())
                .mode(request.getMode())
                .createdAt(danmaku.getCreatedAt())
                .build();
    }

    @Transactional
    public void delete(Long id) {
        AnimeDanmaku danmaku = danmakuMapper.selectById(id);
        if (danmaku == null || danmaku.getStatus() == STATUS_HIDDEN) {
            throw BizException.notFound("弹幕不存在");
        }
        if (!danmaku.getUserId().equals(SecurityUtils.currentUserId())) {
            throw BizException.forbidden("只能删除自己发送的弹幕");
        }
        danmakuMapper.update(null, Wrappers.<AnimeDanmaku>lambdaUpdate()
                .eq(AnimeDanmaku::getId, id)
                .set(AnimeDanmaku::getStatus, STATUS_HIDDEN));
    }

    private Map<Long, SysUser> loadUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
    }

    private DanmakuVO toVO(AnimeDanmaku danmaku, Map<Long, SysUser> users) {
        SysUser user = users.get(danmaku.getUserId());
        BigDecimal time = danmaku.getTime() == null ? BigDecimal.ZERO : danmaku.getTime();
        return DanmakuVO.builder()
                .id(danmaku.getId())
                .userId(danmaku.getUserId())
                .username(user == null ? "deleted_user" : user.getUsername())
                .nickname(user == null ? "已注销用户" : user.getNickname())
                .text(danmaku.getText())
                .time(time)
                .color(danmaku.getColor())
                .mode(danmaku.getMode())
                .createdAt(danmaku.getCreatedAt())
                .build();
    }
}
