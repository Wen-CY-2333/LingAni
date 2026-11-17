package com.lingani.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingani.common.BizException;
import com.lingani.common.PageResult;
import com.lingani.common.PageUtils;
import com.lingani.entity.UserNotification;
import com.lingani.mapper.UserNotificationMapper;
import com.lingani.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserNotificationMapper notificationMapper;

    public PageResult<UserNotification> page(Long current, Long size) {
        long pageCurrent = PageUtils.normalizeCurrent(current);
        long pageSize = PageUtils.normalizeSize(size);
        Long userId = SecurityUtils.currentUserId();

        IPage<UserNotification> page = notificationMapper.selectPage(new Page<>(pageCurrent, pageSize),
                Wrappers.<UserNotification>lambdaQuery()
                        .eq(UserNotification::getUserId, userId)
                        .orderByAsc(UserNotification::getIsRead)
                        .orderByDesc(UserNotification::getCreatedAt)
                        .orderByDesc(UserNotification::getId));
        return PageUtils.of(page.getRecords(), page.getTotal(), pageCurrent, pageSize);
    }

    public long unreadCount() {
        Long userId = SecurityUtils.currentUserId();
        return notificationMapper.selectCount(Wrappers.<UserNotification>lambdaQuery()
                .eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsRead, 0));
    }

    @Transactional
    public void markRead(Long id) {
        UserNotification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw BizException.notFound("通知不存在");
        }
        if (!notification.getUserId().equals(SecurityUtils.currentUserId())) {
            throw BizException.forbidden("不能操作其他用户的通知");
        }
        if (notification.getIsRead() != null && notification.getIsRead() == 1) {
            return;
        }
        notification.setIsRead(1);
        notificationMapper.updateById(notification);
    }

    @Transactional
    public void markAllRead() {
        Long userId = SecurityUtils.currentUserId();
        UserNotification update = new UserNotification();
        update.setIsRead(1);
        notificationMapper.update(update, Wrappers.<UserNotification>lambdaUpdate()
                .eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsRead, 0));
    }
}
