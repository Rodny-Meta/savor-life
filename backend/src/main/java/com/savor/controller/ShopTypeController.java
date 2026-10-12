package com.savor.controller;


import cn.hutool.core.bean.BeanUtil;
import com.savor.dto.Result;
import com.savor.service.IShopTypeService;
import com.savor.vo.ShopTypeVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    public IShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        List<ShopTypeVO> typeList = BeanUtil.copyToList(
                typeService.queryTypeList(), ShopTypeVO.class);
        return Result.ok(typeList);
    }
}
