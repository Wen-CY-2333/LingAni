package com.lingani.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_history")
public class UserHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String videoId;

    private String sectionId;

    private String animeTitle;

    private String animeCover;

    private String episodeTitle;

    private Long progress;

    private Long duration;

    private LocalDateTime lastPlayedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
