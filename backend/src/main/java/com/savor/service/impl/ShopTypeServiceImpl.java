package com.savor.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.savor.entity.ShopType;
import com.savor.mapper.ShopTypeMapper;
import com.savor.service.IShopTypeService;
import com.savor.utils.CacheClient;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.savor.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.savor.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public List<ShopType> queryTypeList() {
        // 1. 查Redis缓存（String存整个列表的JSON）
        String typeJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        if (StrUtil.isNotBlank(typeJson)) {
            return JSONUtil.toList(typeJson, ShopType.class);
        }
        // 2. 缓存未命中，查数据库（按sort升序）
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if (typeList.isEmpty()) {
            return typeList;
        }
        // 3. 回填缓存（统一走 CacheClient.set：TTL 30min + 0~6min 随机，防雪崩）
        cacheClient.set(CACHE_SHOP_TYPE_KEY, typeList, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return typeList;
    }
}
