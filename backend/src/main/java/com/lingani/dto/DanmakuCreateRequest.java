package com.lingani.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DanmakuCreateRequest {

    @NotBlank(message = "sectionId 不能为空")
    @Size(max = 100, message = "sectionId 过长")
    private String sectionId;

    @NotNull(message = "弹幕时间不能为空")
    @DecimalMin(value = "0.0", message = "弹幕时间不能为负数")
    @DecimalMax(value = "86400.0", message = "弹幕时间超出合理范围")
    private BigDecimal time;

    @NotBlank(message = "弹幕内容不能为空")
    @Size(max = 200, message = "弹幕最多 200 个字符")
    private String text;

    @NotNull(message = "弹幕颜色不能为空")
    @Min(value = 0, message = "弹幕颜色不合法")
    @Max(value = 16777215, message = "弹幕颜色不合法")
    private Integer color;

    @NotNull(message = "弹幕模式不能为空")
    @Min(value = 0, message = "弹幕模式不合法")
    @Max(value = 2, message = "弹幕模式不合法")
    private Integer mode;
}
