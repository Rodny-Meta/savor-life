package com.savor.service;

import com.savor.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {

    /**
     * 查询全部商铺类型（按sort升序，带Redis缓存）
     * @return 类型列表
     */
    List<ShopType> queryTypeList();
}
