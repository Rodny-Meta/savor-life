package com.savor.service;

import com.savor.dto.Result;
import com.savor.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 发布探店笔记：落库 + 推送到所有粉丝收件箱（Feed推模式）
     * @param blog 笔记（已过滤用户可填字段，userId由服务端填充）
     * @return 笔记id
     */
    Result saveBlog(Blog blog);

    /**
     * 查询热门笔记（按点赞数降序，装配作者信息+当前用户是否点赞）
     * @param current 页码
     */
    Result queryHotBlog(Integer current);

    /**
     * 根据id查询笔记详情（装配作者信息+是否点赞）
     */
    Result queryBlogById(Long id);

    /**
     * 点赞/取消点赞：ZSet记录点赞用户（score=时间戳），数据库liked数同步增减
     */
    Result likeBlog(Long id);

    /**
     * 点赞排行榜：按点赞时间最早的TOP5用户
     */
    Result queryBlogLikes(Long id);

    /**
     * 查询当前用户的笔记
     */
    Result queryMyBlog(Integer current);

    /**
     * 查询关注的人的笔记（收件箱滚动分页：游标=上次最小时间戳+偏移量）
     * @param max 本次查询的最大时间戳（上次的minTime，首次传当前时间戳）
     * @param offset 上次结果中与最小时间戳重复的条数（跳过已读同分数据）
     */
    Result queryBlogOfFollow(Long max, Integer offset);
}
