package com.lingani.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingani.common.BizException;
import com.lingani.common.PageResult;
import com.lingani.common.PageUtils;
import com.lingani.dto.CommentCreateRequest;
import com.lingani.dto.CommentUpdateRequest;
import com.lingani.entity.AnimeComment;
import com.lingani.entity.SysUser;
import com.lingani.mapper.AnimeCommentMapper;
import com.lingani.mapper.SysUserMapper;
import com.lingani.security.LoginUser;
import com.lingani.security.SecurityUtils;
import com.lingani.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private static final int STATUS_NORMAL = 0;

    private final AnimeCommentMapper commentMapper;
    private final SysUserMapper userMapper;

    public PageResult<CommentVO> page(String videoId, Long current, Long size) {
        String videoKey = normalizeVideoId(videoId);
        long pageCurrent = PageUtils.normalizeCurrent(current);
        long pageSize = PageUtils.normalizeSize(size);

        IPage<AnimeComment> topPage = commentMapper.selectPage(new Page<>(pageCurrent, pageSize),
                Wrappers.<AnimeComment>lambdaQuery()
                        .eq(AnimeComment::getVideoId, videoKey)
                        .eq(AnimeComment::getParentId, 0L)
                        .eq(AnimeComment::getStatus, STATUS_NORMAL)
                        .orderByDesc(AnimeComment::getCreatedAt)
                        .orderByDesc(AnimeComment::getId));

        if (topPage.getRecords().isEmpty()) {
            return PageUtils.of(List.of(), topPage.getTotal(), pageCurrent, pageSize);
        }

        List<Long> topIds = topPage.getRecords().stream().map(AnimeComment::getId).toList();
        List<AnimeComment> replies = commentMapper.selectList(Wrappers.<AnimeComment>lambdaQuery()
                .in(AnimeComment::getParentId, topIds)
                .eq(AnimeComment::getStatus, STATUS_NORMAL)
                .orderByAsc(AnimeComment::getCreatedAt)
                .orderByAsc(AnimeComment::getId));

        Set<Long> userIds = topPage.getRecords().stream().map(AnimeComment::getUserId).collect(Collectors.toSet());
        replies.forEach(reply -> userIds.add(reply.getUserId()));
        Map<Long, SysUser> users = loadUsers(userIds);

        Map<Long, List<CommentVO>> replyMap = replies.stream()
                .map(comment -> toVO(comment, users))
                .collect(Collectors.groupingBy(CommentVO::getParentId));

        List<CommentVO> records = topPage.getRecords().stream()
                .map(comment -> {
                    CommentVO vo = toVO(comment, users);
                    vo.setReplies(replyMap.getOrDefault(comment.getId(), new ArrayList<>()));
                    return vo;
                })
                .toList();

        return PageUtils.of(records, topPage.getTotal(), pageCurrent, pageSize);
    }

    @Transactional
    public CommentVO create(CommentCreateRequest request) {
        LoginUser loginUser = SecurityUtils.currentUser();
        String videoId = normalizeVideoId(request.getVideoId());
        String content = normalizeContent(request.getContent());
        long parentId = request.getParentId() == null ? 0L : request.getParentId();

        if (parentId > 0) {
            AnimeComment parent = commentMapper.selectById(parentId);
            if (parent == null || parent.getStatus() != STATUS_NORMAL) {
                throw BizException.notFound("回复的评论不存在");
            }
            if (parent.getParentId() != null && parent.getParentId() > 0) {
                throw new BizException("本阶段仅支持回复一级评论");
            }
            if (!parent.getVideoId().equals(videoId)) {
                throw new BizException("回复的评论不属于当前动画");
            }
        }

        AnimeComment comment = new AnimeComment();
        comment.setUserId(loginUser.getId());
        comment.setVideoId(videoId);
        comment.setContent(content);
        comment.setParentId(parentId);
        comment.setStatus(STATUS_NORMAL);
        comment.setDeleted(0);
        commentMapper.insert(comment);

        return CommentVO.builder()
                .id(comment.getId())
                .userId(loginUser.getId())
                .username(loginUser.getUsername())
                .nickname(loginUser.getNickname())
                .avatar(loginUser.getAvatar())
                .content(content)
                .parentId(parentId)
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .replies(new ArrayList<>())
                .build();
    }

    @Transactional
    public CommentVO update(Long id, CommentUpdateRequest request) {
        AnimeComment comment = requireOwnedComment(id);
        comment.setContent(normalizeContent(request.getContent()));
        commentMapper.updateById(comment);

        SysUser user = userMapper.selectById(comment.getUserId());
        CommentVO vo = toVO(comment, user == null ? Map.of() : Map.of(user.getId(), user));
        vo.setReplies(new ArrayList<>());
        return vo;
    }

    @Transactional
    public void delete(Long id) {
        AnimeComment comment = requireOwnedComment(id);
        if (comment.getParentId() == null || comment.getParentId() == 0) {
            // 删除一级评论时同步逻辑删除其子回复，避免出现孤儿回复
            commentMapper.delete(Wrappers.<AnimeComment>lambdaQuery().eq(AnimeComment::getParentId, id));
        }
        commentMapper.deleteById(id);
    }

    private AnimeComment requireOwnedComment(Long id) {
        AnimeComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw BizException.notFound("评论不存在");
        }
        if (comment.getStatus() != STATUS_NORMAL) {
            throw BizException.notFound("评论已被屏蔽");
        }
        if (!comment.getUserId().equals(SecurityUtils.currentUserId())) {
            throw BizException.forbidden("只能操作自己发表的评论");
        }
        return comment;
    }

    private String normalizeVideoId(String videoId) {
        String trimmed = videoId == null ? "" : videoId.trim();
        if (trimmed.isEmpty()) {
            throw new BizException("videoId 不能为空");
        }
        return trimmed;
    }

    private String normalizeContent(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new BizException("评论内容不能为空");
        }
        if (trimmed.length() > 500) {
            throw new BizException("评论最多 500 个字符");
        }
        return trimmed;
    }

    private Map<Long, SysUser> loadUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<SysUser> users = userMapper.selectByIds(userIds);
        return users.stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));
    }

    private CommentVO toVO(AnimeComment comment, Map<Long, SysUser> users) {
        SysUser user = users.get(comment.getUserId());
        return CommentVO.builder()
                .id(comment.getId())
                .userId(comment.getUserId())
                .username(user == null ? "deleted_user" : user.getUsername())
                .nickname(user == null ? "已注销用户" : user.getNickname())
                .avatar(user == null ? null : user.getAvatar())
                .content(comment.getContent())
                .parentId(comment.getParentId())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .replies(new ArrayList<>())
                .build();
    }
}
