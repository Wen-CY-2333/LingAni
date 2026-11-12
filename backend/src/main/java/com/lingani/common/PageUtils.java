package com.lingani.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 分页参数与返回值的统一处理，避免把 page/size 直接透传给持久层。
 */
public final class PageUtils {

    private PageUtils() {
    }

    public static long normalizeCurrent(Long current) {
        if (current == null || current < 1) {
            return 1;
        }
        return current;
    }

    public static long normalizeSize(Long size) {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, 30);
    }

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        return new PageResult<>(new ArrayList<>(records), total, current, size);
    }
}
