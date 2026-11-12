package com.lingani.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("anime_danmaku")
public class AnimeDanmaku {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sectionId;

    @TableField("`time`")
    private BigDecimal time;

    private String text;

    private Integer color;

    private Integer mode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private Integer status;
}
