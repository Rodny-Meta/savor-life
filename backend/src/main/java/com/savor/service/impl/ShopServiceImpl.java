package com.savor.service.impl;

import com.savor.entity.Shop;
import com.savor.mapper.ShopMapper;
import com.savor.service.IShopService;
import com.savor.utils.CacheClient;
import com.savor.utils.SystemConstants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.savor.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.savor.utils.RedisConstants.CACHE_SHOP_LOGIC_KEY;
import static com.savor.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.savor.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * 商铺服务：查询走缓存（旁路模式 + 击穿/穿透防护，具体策略由 CacheClient 承载）
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * 根据id查询商铺（互斥锁防击穿 + 空值缓存防穿透）
     * 缓存未命中时抢锁重建；未抢到锁的线程休眠50ms后重查缓存，直到查到或拿到锁
     */
    @Override
    public Shop queryById(Long id) {
        return cacheClient.queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
    }

    /**
     * 根据id查询商铺（逻辑过期防击穿）
     * 未过期直接返回；过期返回旧值并异步重建（牺牲短暂一致性换可用性）
     * 使用独立key前缀 cache:shop:logic:，依赖预热保证key存在
     */
    @Override
    public Shop queryByIdWithLogicalExpire(Long id) {
        return cacheClient.queryWithLogicalExpire(CACHE_SHOP_LOGIC_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
    }

    @Override
    public void deleteCache(Long id) {
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
    }

    /**
     * 缓存预热：查库后以逻辑过期方式写入 cache:shop:logic:{id}（key永不过期）
     */
    @Override
    public void saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        if (shop == null) {
            return;
        }
        cacheClient.setWithLogicalExpire(CACHE_SHOP_LOGIC_KEY + id, shop, expireSeconds, TimeUnit.SECONDS);
    }

    /**
     * 按类型分页查询商铺：
     * - 带坐标：GEOSEARCH shop:geo:{typeId} FROMLONLAT x y BYRADIUS 5000m，按距离升序分页
     * - 不带坐标：数据库 type_id 分页（原有逻辑）
     */
    @Override
    public List<Shop> queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1. 不带坐标：按数据库分页查询
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return page.getRecords();
        }
        // 2. 带坐标：计算分页参数（GEOSEARCH原生命令支持LIMIT offset count，
        //    但Spring Data Redis API只暴露limit(count)，因此查到当前页末再内存截断）
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 3. 查询Redis，按距离升序：GEOSEARCH key FROMLONLAT x y BYRADIUS 5000 m WITHDIST COUNT end
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                .includeDistance().limit(end));
        // 4. 解析结果：shopId + 距离
        if (results == null) {
            return Collections.emptyList();
        }
        List<Shop> shops = new ArrayList<>(results.getContent().size());
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results.getContent()) {
            // 4.1 拿到店铺id（GEO里member存的就是shopId字符串）
            Long shopId = Long.valueOf(result.getContent().getName());
            // 4.2 拿到距离（米）
            Double distance = result.getDistance().getValue();
            // 4.3 根据id查店铺（走缓存）并设置距离
            Shop shop = queryById(shopId);
            if (shop != null) {
                shop.setDistance(distance);
                shops.add(shop);
            }
        }
        // 5. 内存截断当前页（结果已按距离升序，skip跳过前面页的条数）
        return shops.stream()
                .skip(from)
                .limit(SystemConstants.DEFAULT_PAGE_SIZE)
                .collect(Collectors.toList());
    }
}
