package com.savor.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 探店笔记视图对象：含展示用补充字段（作者昵称/头像/是否点赞）
 */
@Data
public class BlogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long shopId;

    private Long userId;

    private String title;

    private String images;

    private String content;

    private Integer liked;

    private Integer comments;

    /**
     * 发布时间（前端展示"xx天前发布"）
     */
    private LocalDateTime createTime;

    /**
     * 作者昵称（非表字段，展示用）
     */
    private String name;

    /**
     * 作者头像（非表字段，展示用）
     */
    private String icon;

    /**
     * 当前用户是否已点赞（非表字段，展示用）
     */
    private Boolean isLike;
}
