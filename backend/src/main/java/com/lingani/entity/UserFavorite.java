package com.lingani.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_favorite")
public class UserFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String videoId;

    private String animeTitle;

    /** 封面不入库，列表返回前实时从 CycAni 拉取，避免地址过期。 */
    @TableField(exist = false)
    private String animeCover;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
