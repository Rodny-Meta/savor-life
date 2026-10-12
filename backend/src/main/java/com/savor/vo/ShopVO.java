package com.savor.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 商铺视图对象：前端展示字段（裁掉 createTime/updateTime 等存储字段）
 */
@Data
public class ShopVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private Long typeId;

    private String images;

    private String area;

    private String address;

    private Double x;

    private Double y;

    private Long avgPrice;

    private Integer sold;

    private Integer comments;

    private Integer score;

    private String openHours;

    /**
     * 距离（附近商户 GEO 功能计算得出）
     */
    private Double distance;
}
