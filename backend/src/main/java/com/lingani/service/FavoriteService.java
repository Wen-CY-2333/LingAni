package com.lingani.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final UserFavoriteMapper favoriteMapper;

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
        favorite.setAnimeCover(StringUtils.hasText(request.getAnimeCover()) ? request.getAnimeCover().trim() : null);
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
        return PageUtils.of(page.getRecords(), page.getTotal(), pageCurrent, pageSize);
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
