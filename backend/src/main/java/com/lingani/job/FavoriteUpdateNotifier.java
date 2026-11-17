package com.lingani.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingani.entity.UserFavorite;
import com.lingani.entity.UserNotification;
import com.lingani.mapper.UserFavoriteMapper;
import com.lingani.mapper.UserNotificationMapper;
import com.lingani.service.CycaniService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class FavoriteUpdateNotifier {

    private static final String TYPE_FAVORITE_UPDATE = "favorite_update";

    private final CycaniService cycaniService;
    private final UserFavoriteMapper favoriteMapper;
    private final UserNotificationMapper notificationMapper;

    /** 启动 30 秒后先跑一次，之后每小时扫描当天更新，同一用户同一作品同一天只提醒一次。 */
    @Scheduled(initialDelay = 30_000, fixedDelay = 3_600_000)
    public void scanTodayUpdates() {
        try {
            int created = notifyTodayUpdates();
            if (created > 0) {
                log.info("追番更新提醒生成 {} 条", created);
            }
        } catch (Exception exception) {
            log.warn("追番更新提醒扫描失败：{}", exception.getMessage());
        }
    }

    public int notifyTodayUpdates() {
        LocalDate today = LocalDate.now();
        Map<String, JsonNode> updated = todayUpdatedVideos(today.getDayOfWeek().getValue());
        if (updated.isEmpty()) {
            return 0;
        }

        List<UserFavorite> favorites = favoriteMapper.selectList(Wrappers.<UserFavorite>lambdaQuery()
                .in(UserFavorite::getVideoId, updated.keySet()));
        if (favorites.isEmpty()) {
            return 0;
        }

        Set<Long> userIds = favorites.stream()
                .map(UserFavorite::getUserId)
                .collect(Collectors.toSet());
        Set<String> existing = notificationMapper.selectList(Wrappers.<UserNotification>lambdaQuery()
                        .eq(UserNotification::getBizDate, today)
                        .in(UserNotification::getUserId, userIds)
                        .in(UserNotification::getVideoId, updated.keySet()))
                .stream()
                .map(item -> item.getUserId() + ":" + item.getVideoId())
                .collect(Collectors.toSet());

        int created = 0;
        for (UserFavorite favorite : favorites) {
            if (existing.contains(favorite.getUserId() + ":" + favorite.getVideoId())) {
                continue;
            }
            JsonNode video = updated.get(favorite.getVideoId());
            String title = firstText(favorite.getAnimeTitle(), text(video, "title"));
            if (!StringUtils.hasText(title)) {
                title = "你追的番剧";
            }

            UserNotification notification = new UserNotification();
            notification.setUserId(favorite.getUserId());
            notification.setType(TYPE_FAVORITE_UPDATE);
            notification.setTitle("追番更新提醒");
            notification.setContent("你追的《" + title + "》今天更新啦，记得来看～");
            notification.setVideoId(favorite.getVideoId());
            notification.setIsRead(0);
            notification.setBizDate(today);
            try {
                notificationMapper.insert(notification);
                created++;
            } catch (DuplicateKeyException ignored) {
                // 并发或重复扫描时由唯一索引兜底
            }
        }
        return created;
    }

    private Map<String, JsonNode> todayUpdatedVideos(int weekday) {
        JsonNode records = cycaniService.getWeekdayVideos().path("records");
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode group : records) {
            if (group.path("weekday").asInt() != weekday) {
                continue;
            }
            for (JsonNode video : group.path("videos")) {
                String videoId = text(video, "videoId", "video_id", "id");
                if (StringUtils.hasText(videoId)) {
                    result.put(videoId, video);
                }
            }
        }
        return result;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String text(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull() && StringUtils.hasText(value.asText(""))) {
                return value.asText("");
            }
        }
        return null;
    }
}
