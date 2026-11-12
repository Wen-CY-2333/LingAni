-- 灵漫 LingAni 数据库初始化脚本
-- 字符集：utf8mb4，支持中文、特殊符号与 Emoji

CREATE DATABASE IF NOT EXISTS `lingani`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `lingani`;

-- ----------------------------
-- 用户表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(50)  NOT NULL COMMENT '用户名，唯一',
    `password`      VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码',
    `nickname`      VARCHAR(50)  NOT NULL COMMENT '昵称',
    `avatar`        VARCHAR(500)          DEFAULT NULL COMMENT '头像地址',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '账号状态：1 正常，0 禁用',
    `role`          TINYINT      NOT NULL DEFAULT 0 COMMENT '角色：0 普通用户，1 管理员',
    `token_version` INT          NOT NULL DEFAULT 0 COMMENT 'Token 版本，修改密码或强制下线时递增',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    KEY `idx_user_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表';

-- ----------------------------
-- 播放历史表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `user_history` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`        BIGINT       NOT NULL COMMENT '用户 ID',
    `video_id`       VARCHAR(100) NOT NULL COMMENT '播放源动画 ID',
    `section_id`     VARCHAR(100) NOT NULL COMMENT '播放源章节 ID',
    `anime_title`    VARCHAR(255)          DEFAULT NULL COMMENT '动画名称快照',
    `anime_cover`    VARCHAR(500)          DEFAULT NULL COMMENT '动画封面快照',
    `episode_title`  VARCHAR(255)          DEFAULT NULL COMMENT '章节名称',
    `progress`       BIGINT       NOT NULL DEFAULT 0 COMMENT '播放进度（秒）',
    `duration`       BIGINT       NOT NULL DEFAULT 0 COMMENT '视频总时长（秒）',
    `last_played_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近播放时间',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_history_user_section` (`user_id`, `section_id`),
    KEY `idx_history_user_last` (`user_id`, `last_played_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '播放历史表';

-- ----------------------------
-- 追番收藏表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `user_favorite` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL COMMENT '用户 ID',
    `video_id`    VARCHAR(100) NOT NULL COMMENT '播放源动画 ID',
    `anime_title` VARCHAR(255)          DEFAULT NULL COMMENT '动画名称快照',
    `anime_cover` VARCHAR(500)          DEFAULT NULL COMMENT '动画封面快照',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_favorite_user_video` (`user_id`, `video_id`),
    KEY `idx_favorite_user_created` (`user_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '追番收藏表';

-- ----------------------------
-- 评论表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `anime_comment` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT      NOT NULL COMMENT '评论用户 ID',
    `video_id`   VARCHAR(100) NOT NULL COMMENT '播放源动画 ID',
    `content`    TEXT        NOT NULL COMMENT '评论内容',
    `parent_id`  BIGINT      NOT NULL DEFAULT 0 COMMENT '父评论 ID，0 表示一级评论',
    `status`     TINYINT     NOT NULL DEFAULT 0 COMMENT '状态：0 正常，1 屏蔽',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
    PRIMARY KEY (`id`),
    KEY `idx_comment_video` (`video_id`, `status`, `deleted`, `parent_id`, `created_at`),
    KEY `idx_comment_parent` (`parent_id`),
    KEY `idx_comment_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '动画评论表';

-- ----------------------------
-- 弹幕表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `anime_danmaku` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT       NOT NULL COMMENT '发送用户 ID',
    `section_id` VARCHAR(100) NOT NULL COMMENT '播放源章节 ID',
    `time`       DECIMAL(10, 3) NOT NULL COMMENT '弹幕出现时间（秒）',
    `text`       VARCHAR(500) NOT NULL COMMENT '弹幕内容',
    `color`      INT          NOT NULL DEFAULT 16777215 COMMENT '十进制颜色值',
    `mode`       TINYINT      NOT NULL DEFAULT 0 COMMENT '模式：0 滚动，1 顶部，2 底部',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    `status`     TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 正常，1 隐藏/删除',
    PRIMARY KEY (`id`),
    KEY `idx_danmaku_section` (`section_id`, `status`, `time`),
    KEY `idx_danmaku_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '弹幕表';
