package com.savor.service;

import com.savor.dto.Result;
import com.savor.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀下单（异步版）：Lua 原子判资格 → 通过则入队 → 立即返回订单id
     * @param voucherId 优惠券id
     * @return 订单id
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 创建订单（事务方法：一人一单DB兜底查重 + 乐观锁扣库存 + 插单）
     * 由异步消费端通过代理对象调用（后台线程无 ThreadLocal 用户，用户信息从订单对象取）
     * @param voucherOrder 已生成id的订单信息
     */
    void createVoucherOrder(VoucherOrder voucherOrder);
}
