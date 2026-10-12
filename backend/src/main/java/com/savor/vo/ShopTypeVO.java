package com.savor.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 商铺类型视图对象
 */
@Data
public class ShopTypeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String icon;

    private Integer sort;
}
