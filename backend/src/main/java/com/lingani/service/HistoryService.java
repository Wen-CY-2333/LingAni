package com.lingani.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import tools.jackson.databind.JsonNode;
import com.lingani.common.BizException;
import com.lingani.common.PageResult;
import com.lingani.common.PageUtils;
import com.lingani.dto.HistorySaveRequest;
import com.lingani.entity.UserHistory;
import com.lingani.mapper.UserHistoryMapper;
import com.lingani.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HistoryService {

    private final UserHistoryMapper historyMapper;
    private final CycaniService cycaniService;

    @Transactional
    public UserHistory save(HistorySaveRequest request) {
        Long userId = SecurityUtils.currentUserId();
        String sectionId = request.getSectionId().trim();
        long duration = request.getDuration();
        long progress = request.getProgress();
        if (duration > 0 && progress > duration) {
            progress = duration;
        }

        UserHistory existing = historyMapper.selectOne(Wrappers.<UserHistory>lambdaQuery()
                .eq(UserHistory::getUserId, userId)
                .eq(UserHistory::getSectionId, sectionId));

        if (existing != null) {
            fill(existing, request, progress, duration);
            existing.setLastPlayedAt(LocalDateTime.now());
            historyMapper.updateById(existing);
            return existing;
        }

        UserHistory history = new UserHistory();
        history.setUserId(userId);
        history.setSectionId(sectionId);
        fill(history, request, progress, duration);
        history.setLastPlayedAt(LocalDateTime.now());
        history.setCreatedAt(LocalDateTime.now());
        history.setUpdatedAt(LocalDateTime.now());

        try {
            historyMapper.insert(history);
            return history;
        } catch (DuplicateKeyException exception) {
            // 并发写入时唯一键冲突，退化为更新已有记录
            UserHistory concurrent = historyMapper.selectOne(Wrappers.<UserHistory>lambdaQuery()
                    .eq(UserHistory::getUserId, userId)
                    .eq(UserHistory::getSectionId, sectionId));
            if (concurrent == null) {
                throw exception;
            }
            fill(concurrent, request, progress, duration);
            concurrent.setLastPlayedAt(LocalDateTime.now());
            historyMapper.updateById(concurrent);
            return concurrent;
        }
    }

    public List<UserHistory> listByVideo(String videoId) {
        if (!StringUtils.hasText(videoId)) {
            return List.of();
        }
        Long userId = SecurityUtils.currentUserId();
        return historyMapper.selectList(Wrappers.<UserHistory>lambdaQuery()
                .eq(UserHistory::getUserId, userId)
                .eq(UserHistory::getVideoId, videoId.trim())
                .orderByDesc(UserHistory::getLastPlayedAt)
                .orderByDesc(UserHistory::getId));
    }

    public PageResult<UserHistory> page(Long current, Long size) {
        long pageCurrent = PageUtils.normalizeCurrent(current);
        long pageSize = PageUtils.normalizeSize(size);
        Long userId = SecurityUtils.currentUserId();

        IPage<UserHistory> page = historyMapper.selectPage(new Page<>(pageCurrent, pageSize),
                Wrappers.<UserHistory>lambdaQuery()
                        .eq(UserHistory::getUserId, userId)
                        .orderByDesc(UserHistory::getLastPlayedAt)
                        .orderByDesc(UserHistory::getId));
        // 封面不入数据库（第三方地址会过期），返回前用 CycAni 最新详情填充
        page.getRecords().parallelStream().forEach(this::enrichFromCycAni);
        return PageUtils.of(page.getRecords(), page.getTotal(), pageCurrent, pageSize);
    }

    /** 用 CycAni 最新详情填充历史记录的封面 */
    private void enrichFromCycAni(UserHistory history) {
        try {
            JsonNode detail = cycaniService.getVideoDetail(history.getVideoId());
            if (detail == null) return;
            String cover = text(detail, "cover_url", "cover", "animeCover", "pic");
            if (StringUtils.hasText(cover)) history.setAnimeCover(cover);
        } catch (Exception exception) {
            // 第三方接口异常时保留封面为空，前端显示占位图
        }
    }

    private String text(JsonNode node, String... fields) {
        if (node == null || fields == null) return null;
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                String text = value.asText("");
                if (StringUtils.hasText(text)) return text;
            }
        }
        return null;
    }

    @Transactional
    public void delete(Long id) {
        UserHistory history = historyMapper.selectById(id);
        if (history == null) {
            throw BizException.notFound("历史记录不存在");
        }
        if (!history.getUserId().equals(SecurityUtils.currentUserId())) {
            throw BizException.forbidden("不能删除其他用户的记录");
        }
        historyMapper.deleteById(id);
    }

    @Transactional
    public void clear() {
        historyMapper.delete(Wrappers.<UserHistory>lambdaQuery().eq(UserHistory::getUserId, SecurityUtils.currentUserId()));
    }

    private void fill(UserHistory history, HistorySaveRequest request, long progress, long duration) {
        history.setVideoId(request.getVideoId().trim());
        history.setAnimeTitle(StringUtils.hasText(request.getAnimeTitle()) ? request.getAnimeTitle().trim() : null);
        history.setEpisodeTitle(StringUtils.hasText(request.getEpisodeTitle()) ? request.getEpisodeTitle().trim() : null);
        history.setProgress(progress);
        history.setDuration(duration);
    }
}
