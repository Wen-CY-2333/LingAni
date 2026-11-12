package com.lingani.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingani.common.BizException;
import com.lingani.common.PageResult;
import com.lingani.common.PageUtils;
import com.lingani.entity.AnimeComment;
import com.lingani.entity.AnimeDanmaku;
import com.lingani.entity.SysUser;
import com.lingani.entity.UserFavorite;
import com.lingani.entity.UserHistory;
import com.lingani.mapper.AnimeCommentMapper;
import com.lingani.mapper.AnimeDanmakuMapper;
import com.lingani.mapper.SysUserMapper;
import com.lingani.mapper.UserFavoriteMapper;
import com.lingani.mapper.UserHistoryMapper;
import com.lingani.security.LoginUser;
import com.lingani.security.SecurityUtils;
import com.lingani.vo.AdminCommentVO;
import com.lingani.vo.AdminDanmakuVO;
import com.lingani.vo.AdminFavoriteVO;
import com.lingani.vo.AdminHistoryVO;
import com.lingani.vo.AdminStatsVO;
import com.lingani.vo.AdminUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理后台业务：所有方法入口都会校验管理员身份。
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final SysUserMapper userMapper;
    private final AnimeCommentMapper commentMapper;
    private final AnimeDanmakuMapper danmakuMapper;
    private final UserFavoriteMapper favoriteMapper;
    private final UserHistoryMapper historyMapper;

    public AdminStatsVO stats() {
        checkAdmin();
        return AdminStatsVO.builder()
                .users(userMapper.selectCount(null))
                .comments(commentMapper.selectCount(null))
                .danmaku(danmakuMapper.selectCount(null))
                .favorites(favoriteMapper.selectCount(null))
                .history(historyMapper.selectCount(null))
                .build();
    }

    public PageResult<AdminUserVO> listUsers(Long current, Long size, String keyword) {
        checkAdmin();
        long page = PageUtils.normalizeCurrent(current);
        long pageSize = PageUtils.normalizeSize(size);
        Page<SysUser> result = userMapper.selectPage(new Page<>(page, pageSize),
                Wrappers.<SysUser>lambdaQuery()
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(SysUser::getUsername, keyword)
                                .or()
                                .like(SysUser::getNickname, keyword))
                        .orderByDesc(SysUser::getCreatedAt));
        List<AdminUserVO> records = result.getRecords().stream()
                .map(user -> AdminUserVO.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .nickname(user.getNickname())
                        .avatar(user.getAvatar())
                        .status(user.getStatus())
                        .role(user.getRole())
                        .createdAt(user.getCreatedAt())
                        .build())
                .toList();
        return PageUtils.of(records, result.getTotal(), page, pageSize);
    }

    public void updateUserStatus(Long id, Integer status) {
        LoginUser admin = checkAdmin();
        if (admin.getId().equals(id)) {
            throw BizException.forbidden("不能修改自己的账号状态");
        }
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        var update = Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, id)
                .set(SysUser::getStatus, status);
        if (status == 0) {
            // 禁用时同时递增 Token 版本，避免解禁后旧 Token 自动恢复可用
            update.set(SysUser::getTokenVersion, (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        }
        userMapper.update(null, update);
    }

    public PageResult<AdminCommentVO> listComments(Long current, Long size, String keyword) {
        checkAdmin();
        long page = PageUtils.normalizeCurrent(current);
        long pageSize = PageUtils.normalizeSize(size);
        Page<AnimeComment> result = commentMapper.selectPage(new Page<>(page, pageSize),
                Wrappers.<AnimeComment>lambdaQuery()
                        .like(StringUtils.hasText(keyword), AnimeComment::getContent, keyword)
                        .orderByDesc(AnimeComment::getCreatedAt));
        Map<Long, SysUser> users = userMap(result.getRecords().stream().map(AnimeComment::getUserId).toList());
        List<AdminCommentVO> records = result.getRecords().stream()
                .map(comment -> {
                    SysUser user = users.get(comment.getUserId());
                    return AdminCommentVO.builder()
                            .id(comment.getId())
                            .videoId(comment.getVideoId())
                            .userId(comment.getUserId())
                            .username(user == null ? null : user.getUsername())
                            .nickname(user == null ? null : user.getNickname())
                            .content(comment.getContent())
                            .parentId(comment.getParentId())
                            .status(comment.getStatus())
                            .createdAt(comment.getCreatedAt())
                            .build();
                })
                .toList();
        return PageUtils.of(records, result.getTotal(), page, pageSize);
    }

    public void updateCommentStatus(Long id, Integer status) {
        checkAdmin();
        AnimeComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw BizException.notFound("评论不存在");
        }
        commentMapper.update(null, Wrappers.<AnimeComment>lambdaUpdate()
                .eq(AnimeComment::getId, id)
                .set(AnimeComment::getStatus, status));
    }

    public void deleteComment(Long id) {
        checkAdmin();
        AnimeComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw BizException.notFound("评论不存在");
        }
        if (comment.getParentId() == null || comment.getParentId() == 0) {
            commentMapper.delete(Wrappers.<AnimeComment>lambdaQuery()
                    .eq(AnimeComment::getParentId, id));
        }
        commentMapper.deleteById(id);
    }

    public PageResult<AdminDanmakuVO> listDanmaku(Long current, Long size, String keyword) {
        checkAdmin();
        long page = PageUtils.normalizeCurrent(current);
        long pageSize = PageUtils.normalizeSize(size);
        Page<AnimeDanmaku> result = danmakuMapper.selectPage(new Page<>(page, pageSize),
                Wrappers.<AnimeDanmaku>lambdaQuery()
                        .like(StringUtils.hasText(keyword), AnimeDanmaku::getText, keyword)
                        .orderByDesc(AnimeDanmaku::getCreatedAt));
        Map<Long, SysUser> users = userMap(result.getRecords().stream().map(AnimeDanmaku::getUserId).toList());
        List<AdminDanmakuVO> records = result.getRecords().stream()
                .map(danmaku -> {
                    SysUser user = users.get(danmaku.getUserId());
                    return AdminDanmakuVO.builder()
                            .id(danmaku.getId())
                            .sectionId(danmaku.getSectionId())
                            .userId(danmaku.getUserId())
                            .username(user == null ? null : user.getUsername())
                            .text(danmaku.getText())
                            .time(danmaku.getTime())
                            .color(danmaku.getColor())
                            .mode(danmaku.getMode())
                            .status(danmaku.getStatus())
                            .createdAt(danmaku.getCreatedAt())
                            .build();
                })
                .toList();
        return PageUtils.of(records, result.getTotal(), page, pageSize);
    }

    public void updateDanmakuStatus(Long id, Integer status) {
        checkAdmin();
        AnimeDanmaku danmaku = danmakuMapper.selectById(id);
        if (danmaku == null) {
            throw BizException.notFound("弹幕不存在");
        }
        danmakuMapper.update(null, Wrappers.<AnimeDanmaku>lambdaUpdate()
                .eq(AnimeDanmaku::getId, id)
                .set(AnimeDanmaku::getStatus, status));
    }

    public void deleteDanmaku(Long id) {
        checkAdmin();
        AnimeDanmaku danmaku = danmakuMapper.selectById(id);
        if (danmaku == null) {
            throw BizException.notFound("弹幕不存在");
        }
        danmakuMapper.deleteById(id);
    }

    public PageResult<AdminFavoriteVO> listFavorites(Long current, Long size, String keyword) {
        checkAdmin();
        long page = PageUtils.normalizeCurrent(current);
        long pageSize = PageUtils.normalizeSize(size);
        Page<UserFavorite> result = favoriteMapper.selectPage(new Page<>(page, pageSize),
                Wrappers.<UserFavorite>lambdaQuery()
                        .like(StringUtils.hasText(keyword), UserFavorite::getAnimeTitle, keyword)
                        .orderByDesc(UserFavorite::getCreatedAt));
        Map<Long, SysUser> users = userMap(result.getRecords().stream().map(UserFavorite::getUserId).toList());
        List<AdminFavoriteVO> records = result.getRecords().stream()
                .map(favorite -> AdminFavoriteVO.builder()
                        .id(favorite.getId())
                        .userId(favorite.getUserId())
                        .username(displayName(users.get(favorite.getUserId())))
                        .videoId(favorite.getVideoId())
                        .animeTitle(favorite.getAnimeTitle())
                        .animeCover(favorite.getAnimeCover())
                        .createdAt(favorite.getCreatedAt())
                        .build())
                .toList();
        return PageUtils.of(records, result.getTotal(), page, pageSize);
    }

    public PageResult<AdminHistoryVO> listHistory(Long current, Long size, String keyword) {
        checkAdmin();
        long page = PageUtils.normalizeCurrent(current);
        long pageSize = PageUtils.normalizeSize(size);
        Page<UserHistory> result = historyMapper.selectPage(new Page<>(page, pageSize),
                Wrappers.<UserHistory>lambdaQuery()
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(UserHistory::getAnimeTitle, keyword)
                                .or()
                                .like(UserHistory::getEpisodeTitle, keyword))
                        .orderByDesc(UserHistory::getLastPlayedAt));
        Map<Long, SysUser> users = userMap(result.getRecords().stream().map(UserHistory::getUserId).toList());
        List<AdminHistoryVO> records = result.getRecords().stream()
                .map(history -> AdminHistoryVO.builder()
                        .id(history.getId())
                        .userId(history.getUserId())
                        .username(displayName(users.get(history.getUserId())))
                        .videoId(history.getVideoId())
                        .sectionId(history.getSectionId())
                        .episodeTitle(history.getEpisodeTitle())
                        .progress(history.getProgress())
                        .duration(history.getDuration())
                        .lastPlayedAt(history.getLastPlayedAt())
                        .build())
                .toList();
        return PageUtils.of(records, result.getTotal(), page, pageSize);
    }

    private LoginUser checkAdmin() {
        return SecurityUtils.requireAdmin();
    }

    private Map<Long, SysUser> userMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (left, right) -> left));
    }

    private String displayName(SysUser user) {
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }
}
