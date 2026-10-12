package com.savor.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.savor.dto.Result;
import com.savor.entity.Shop;
import com.savor.service.IShopService;
import com.savor.utils.SystemConstants;
import com.savor.vo.ShopVO;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    /**
     * 根据id查询商铺信息（互斥锁防击穿 + 空值缓存防穿透）
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        Shop shop = shopService.queryById(id);
        if (shop == null) {
            return Result.fail("商铺不存在");
        }
        return Result.ok(BeanUtil.copyProperties(shop, ShopVO.class));
    }

    /**
     * 根据id查询商铺信息（逻辑过期版防击穿，对比/测试用；依赖预热）
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/logical/{id}")
    public Result queryShopByIdLogical(@PathVariable("id") Long id) {
        Shop shop = shopService.queryByIdWithLogicalExpire(id);
        if (shop == null) {
            return Result.fail("商铺不存在或未预热");
        }
        return Result.ok(BeanUtil.copyProperties(shop, ShopVO.class));
    }

    /**
     * 缓存预热（逻辑过期方案配套）：查库并以逻辑过期方式写入缓存
     * @param id 商铺id
     * @param expire 逻辑过期时间（秒）
     * @return 无
     */
    @PostMapping("/preheat/{id}")
    public Result preheatShop(@PathVariable("id") Long id, @RequestParam("expire") Long expire) {
        shopService.saveShop2Redis(id, expire);
        return Result.ok();
    }

    /**
     * 新增商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        // 返回店铺id
        return Result.ok(shop.getId());
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("商铺id不能为空");
        }
        // 先更新数据库
        shopService.updateById(shop);
        // 再删除缓存（旁路模式标准写顺序：更新库后删缓存，下次查询回填新数据）
        shopService.deleteCache(shop.getId());
        return Result.ok();
    }

    /**
     * 根据商铺类型分页查询商铺信息（带坐标时按距离排序，走Redis GEO）
     * @param typeId 商铺类型
     * @param current 页码
     * @param x 当前位置经度（可选）
     * @param y 当前位置纬度（可选）
     * @return 商铺列表（带坐标时含distance字段，单位米）
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ) {
        List<Shop> shops = shopService.queryShopByType(typeId, current, x, y);
        return Result.ok(BeanUtil.copyToList(shops, ShopVO.class));
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(BeanUtil.copyToList(page.getRecords(), ShopVO.class));
    }
}
