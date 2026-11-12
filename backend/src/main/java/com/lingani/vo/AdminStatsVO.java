package com.lingani.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsVO {

    private Long users;
    private Long comments;
    private Long danmaku;
    private Long favorites;
    private Long history;
}
