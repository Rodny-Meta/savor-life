package com.savor.service;

import com.savor.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询商铺详情（互斥锁防击穿 + 空值缓存防穿透）
     * @param id 商铺id
     * @return 商铺对象；库中不存在时返回 null
     */
    Shop queryById(Long id);

    /**
     * 根据id查询商铺详情（逻辑过期防击穿：未过期直接返回，过期返回旧值并异步重建）
     * 依赖预热（saveShop2Redis）保证缓存key存在
     * @param id 商铺id
     * @return 商铺对象；未预热时返回 null
     */
    Shop queryByIdWithLogicalExpire(Long id);

    /**
     * 删除商铺缓存（更新商铺后调用，保证下次查询回填最新数据）
     * @param id 商铺id
     */
    void deleteCache(Long id);

    /**
     * 缓存预热：查库后以逻辑过期方式写入缓存（cache:shop:logic:{id}）
     * @param id 商铺id
     * @param expireSeconds 逻辑过期时间（秒）
     */
    void saveShop2Redis(Long id, Long expireSeconds);

    /**
     * 按类型分页查询商铺：带坐标时走 Redis GEO（按距离排序，GEOSEARCH 分页），
     * 不带坐标时退化为数据库分页
     * @param typeId 商铺类型id
     * @param current 页码
     * @param x 当前位置经度（可空）
     * @param y 当前位置纬度（可空）
     */
    java.util.List<Shop> queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
