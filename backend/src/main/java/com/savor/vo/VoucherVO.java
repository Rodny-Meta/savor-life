package com.savor.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券视图对象：含秒杀扩展信息（stock/beginTime/endTime 来自关联表）
 */
@Data
public class VoucherVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long shopId;

    private String title;

    private String subTitle;

    private String rules;

    private Long payValue;

    private Long actualValue;

    private Integer type;

    private Integer status;

    /**
     * 库存（秒杀券才有，来自 tb_seckill_voucher）
     */
    private Integer stock;

    /**
     * 生效时间（秒杀券才有）
     */
    private LocalDateTime beginTime;

    /**
     * 失效时间（秒杀券才有）
     */
    private LocalDateTime endTime;
}
