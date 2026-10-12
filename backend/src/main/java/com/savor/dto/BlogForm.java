package com.savor.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 发布探店笔记入参：只收用户可填的四个字段。
 * 不用 Blog(PO) 直接接收，防止前端注入 liked/comments/createTime/updateTime 等字段。
 */
@Data
public class BlogForm implements Serializable {

    private static final long serialVersionUID = 1L;

    private String title;

    private Long shopId;

    private String images;

    private String content;
}
