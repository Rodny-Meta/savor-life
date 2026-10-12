package com.savor.controller;


import cn.hutool.core.bean.BeanUtil;
import com.savor.dto.BlogForm;
import com.savor.dto.Result;
import com.savor.entity.Blog;
import com.savor.service.IBlogService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    /**
     * 发布探店笔记（入参只收用户可填字段，liked/comments由数据库默认值兜底，防止字段注入）
     */
    @PostMapping
    public Result saveBlog(@RequestBody BlogForm form) {
        Blog blog = BeanUtil.copyProperties(form, Blog.class);
        return blogService.saveBlog(blog);
    }

    /**
     * 点赞/取消点赞（一人一赞，ZSet记录点赞时间）
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /**
     * 点赞排行榜（最早点赞的TOP5用户）
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }

    /**
     * 查询当前用户的笔记
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryMyBlog(current);
    }

    /**
     * 查询热门笔记（按点赞数降序）
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    /**
     * 查询笔记详情
     */
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    /**
     * 查询关注的人的笔记（收件箱滚动分页）
     * @param lastId 上次查询的最小时间戳（首次传当前时间戳）
     * @param offset 上次结果中与最小时间戳重复的条数
     */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(@RequestParam("lastId") Long lastId,
                                    @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        return blogService.queryBlogOfFollow(lastId, offset);
    }
}
