package com.savor;

import com.savor.entity.Shop;
import com.savor.service.IShopService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.savor.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * GEO数据导入 + HyperLogLog UV统计实测（连真实Redis 6380）
 * 跑完后可用 redis-cli MEMORY USAGE 对比 HLL 与 Set 的内存占用
 */
@SpringBootTest
class GeoDataTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopService shopService;

    /**
     * 店铺坐标批量导入GEO：shop:geo:{typeId}，member=shopId，坐标=(经度x, 纬度y)
     * 按类型分组批量GEOADD，避免逐条网络往返
     */
    @Test
    void loadShopData() {
        // 1. 查询所有带坐标的店铺
        List<Shop> shops = shopService.query().isNotNull("x").isNotNull("y").list();
        if (shops.isEmpty()) {
            System.out.println("没有带坐标的店铺数据");
            return;
        }
        // 2. 按类型分组（一个类型一个key，查询时按类型查对应的key）
        Map<Long, List<Shop>> typeMap = shops.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId));
        // 3. 分类型批量写入GEO
        int total = 0;
        for (Map.Entry<Long, List<Shop>> entry : typeMap.entrySet()) {
            String key = SHOP_GEO_KEY + entry.getKey();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(entry.getValue().size());
            for (Shop shop : entry.getValue()) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
            total += locations.size();
            System.out.println("typeId=" + entry.getKey() + " 导入 " + locations.size() + " 家店铺");
        }
        System.out.println("GEO数据导入完成，共 " + total + " 家店铺");
    }

    /**
     * HLL实测：100万用户访问，统计UV、耗时与误差（标准误差约0.81%）
     */
    @Test
    void testHyperLogLog() {
        String key = "test:uv:hll";
        stringRedisTemplate.delete(key);
        long start = System.currentTimeMillis();
        int total = 1_000_000;
        int batch = 10_000;
        // 分批PFADD，每批1万个（一次传100万个参数会撑爆命令缓冲）
        for (int i = 0; i < total; i += batch) {
            String[] userIds = new String[batch];
            for (int j = 0; j < batch; j++) {
                userIds[j] = "user-" + (i + j);
            }
            stringRedisTemplate.opsForHyperLogLog().add(key, userIds);
        }
        long cost = System.currentTimeMillis() - start;
        Long count = stringRedisTemplate.opsForHyperLogLog().size(key);
        System.out.println("HLL写入100万用户耗时: " + cost + "ms");
        System.out.println("HLL统计UV: " + count + "，误差: "
                + String.format("%.2f", Math.abs(count - total) * 100.0 / total) + "%");
    }

    /**
     * Set对比：同样100万用户用Set存（精确但吃内存，跑完手动删除key）
     * 内存对比命令：redis-cli MEMORY USAGE test:uv:hll / test:uv:set
     */
    @Test
    void testSetCompare() {
        String key = "test:uv:set";
        stringRedisTemplate.delete(key);
        long start = System.currentTimeMillis();
        int total = 1_000_000;
        int batch = 10_000;
        for (int i = 0; i < total; i += batch) {
            String[] userIds = new String[batch];
            for (int j = 0; j < batch; j++) {
                userIds[j] = "user-" + (i + j);
            }
            stringRedisTemplate.opsForSet().add(key, userIds);
        }
        long cost = System.currentTimeMillis() - start;
        Long count = stringRedisTemplate.opsForSet().size(key);
        System.out.println("Set写入100万用户耗时: " + cost + "ms");
        System.out.println("Set统计UV(精确值): " + count);
    }
}
