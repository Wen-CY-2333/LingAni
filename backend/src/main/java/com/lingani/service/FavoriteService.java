package com.lingani.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import tools.jackson.databind.JsonNode;
import com.lingani.common.BizException;
import com.lingani.common.PageResult;
import com.lingani.common.PageUtils;
import com.lingani.dto.FavoriteAddRequest;
import com.lingani.entity.UserFavorite;
import com.lingani.mapper.UserFavoriteMapper;
import com.lingani.security.SecurityUtils;
import com.lingani.vo.FavoriteCheckVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final UserFavoriteMapper favoriteMapper;
    private final CycaniService cycaniService;

    @Transactional
    public UserFavorite add(FavoriteAddRequest request) {
        Long userId = SecurityUtils.currentUserId();
        String videoId = request.getVideoId().trim();

        UserFavorite existing = findExisting(userId, videoId);
        if (existing != null) {
            throw BizException.conflict("该动画已在追番列表中");
        }

        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setVideoId(videoId);
        favorite.setAnimeTitle(StringUtils.hasText(request.getAnimeTitle()) ? request.getAnimeTitle().trim() : null);
        favorite.setCreatedAt(LocalDateTime.now());

        try {
            favoriteMapper.insert(favorite);
            return favorite;
        } catch (DuplicateKeyException exception) {
            throw BizException.conflict("该动画已在追番列表中");
        }
    }

    public PageResult<UserFavorite> page(Long current, Long size) {
        long pageCurrent = PageUtils.normalizeCurrent(current);
        long pageSize = PageUtils.normalizeSize(size);
        Long userId = SecurityUtils.currentUserId();

        IPage<UserFavorite> page = favoriteMapper.selectPage(new Page<>(pageCurrent, pageSize),
                Wrappers.<UserFavorite>lambdaQuery()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getCreatedAt)
                        .orderByDesc(UserFavorite::getId));
        List<UserFavorite> records = page.getRecords();
        // 封面不入数据库（第三方地址会过期），返回前用 CycAni 最新详情填充封面与标题
        records.parallelStream().forEach(this::enrichFromCycAni);
        return PageUtils.of(records, page.getTotal(), pageCurrent, pageSize);
    }

    /** 用 CycAni 最新详情填充追番记录的封面与标题 */
    private void enrichFromCycAni(UserFavorite favorite) {
        try {
            JsonNode detail = cycaniService.getVideoDetail(favorite.getVideoId());
            if (detail == null) return; // 第三方接口异常时保留已存信息，不影响列表展示
            String cover = text(detail, "cover_url", "cover", "animeCover", "pic");
            if (StringUtils.hasText(cover)) favorite.setAnimeCover(cover);
            String title = text(detail, "title", "animeTitle", "name");
            if (StringUtils.hasText(title)) favorite.setAnimeTitle(title);
        } catch (Exception exception) {
            // 第三方接口异常时保留已存信息，不影响列表展示
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

    public FavoriteCheckVO check(String videoId) {
        Long userId = SecurityUtils.currentUserId();
        UserFavorite favorite = StringUtils.hasText(videoId)
                ? findExisting(userId, videoId.trim())
                : null;
        if (favorite == null) {
            return new FavoriteCheckVO(false, null);
        }
        return new FavoriteCheckVO(true, favorite.getId());
    }

    @Transactional
    public void delete(Long id) {
        UserFavorite favorite = favoriteMapper.selectById(id);
        if (favorite == null) {
            throw BizException.notFound("追番记录不存在");
        }
        if (!favorite.getUserId().equals(SecurityUtils.currentUserId())) {
            throw BizException.forbidden("不能取消其他用户的追番");
        }
        favoriteMapper.deleteById(id);
    }

    private UserFavorite findExisting(Long userId, String videoId) {
        return favoriteMapper.selectOne(Wrappers.<UserFavorite>lambdaQuery()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getVideoId, videoId));
    }
}
