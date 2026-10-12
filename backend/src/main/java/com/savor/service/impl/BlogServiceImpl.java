package com.savor.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.savor.dto.Result;
import com.savor.dto.ScrollResult;
import com.savor.dto.UserDTO;
import com.savor.entity.Blog;
import com.savor.entity.Follow;
import com.savor.entity.User;
import com.savor.mapper.BlogMapper;
import com.savor.mapper.FollowMapper;
import com.savor.mapper.UserMapper;
import com.savor.service.IBlogService;
import com.savor.utils.SystemConstants;
import com.savor.utils.UserHolder;
import com.savor.vo.BlogVO;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.savor.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.savor.utils.RedisConstants.FEED_KEY;

/**
 * 探店笔记服务：
 * - 点赞用 Redis ZSet（blog:liked:{blogId}，member=用户id，score=点赞时间戳）：
 *   一人一赞 + 天然按时间排序 → 排行榜 ZRANGE 取最早TOP5
 * - Feed 推模式：发布笔记后推送到所有粉丝收件箱 feed:{粉丝id}（ZSet，score=时间戳，member=笔记id）
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private FollowMapper followMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result saveBlog(Blog blog) {
        // 1. 获取登录用户并保存探店笔记
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("发布笔记失败！");
        }
        // 2. 推送到所有粉丝的收件箱：查关注了作者的人（follow_user_id = 作者）
        List<Follow> fans = followMapper.selectList(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowUserId, userId));
        // 3. 逐个推入粉丝收件箱（ZSet score=当前时间戳，滚动分页按时间倒序取）
        for (Follow fan : fans) {
            stringRedisTemplate.opsForZSet().add(
                    FEED_KEY + fan.getUserId(), blog.getId().toString(), System.currentTimeMillis());
        }
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 按点赞数降序分页
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<BlogVO> voList = page.getRecords().stream().map(blog -> {
            // 装配作者信息和"当前用户是否点赞"
            queryBlogUser(blog);
            isBlogLiked(blog);
            return BeanUtil.copyProperties(blog, BlogVO.class);
        }).toList();
        return Result.ok(voList);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(BeanUtil.copyProperties(blog, BlogVO.class));
    }

    @Override
    public Result likeBlog(Long id) {
        // 1. 获取登录用户，判断是否已点赞（ZSCORE 存在=点过）
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 2. 未点赞：数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                // 3. 记录到ZSet（score=时间戳 → 排行榜按点赞时间排序）
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4. 已点赞：取消，数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 1. 查询TOP5点赞用户（ZRANGE按score升序=最早点赞的排前面）
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2. 解析出用户id
        List<Long> ids = top5.stream().map(Long::valueOf).toList();
        // 3. 根据用户id查询用户并转DTO（stream逐个查，保持点赞顺序）
        List<UserDTO> userDTOS = ids.stream().map(uid -> {
            User user = userMapper.selectById(uid);
            return BeanUtil.copyProperties(user, UserDTO.class);
        }).toList();
        return Result.ok(userDTOS);
    }

    @Override
    public Result queryMyBlog(Integer current) {
        Long userId = UserHolder.getUser().getId();
        Page<Blog> page = query().eq("user_id", userId)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(BeanUtil.copyToList(page.getRecords(), BlogVO.class));
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2. 查询收件箱数据：ZREVRANGEBYSCORE key max 0 LIMIT offset 2（score降序=时间倒序）
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(FEED_KEY + userId, 0, max, offset, 2);
        // 3. 非空判断（没有更多数据直接返回空）
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        // 4. 解析数据：blogId列表 + minTime（本页最小时间戳）+ offset（最小时间戳出现的次数）
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            ids.add(Long.valueOf(tuple.getValue()));
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                // 同一时刻有多条数据，下次查询要跳过这些已读数据
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        // 5. 根据id查询笔记并按点赞排行顺序返回（ORDER BY FIELD 保持收件箱时间序）
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        List<BlogVO> voList = blogs.stream().map(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
            return BeanUtil.copyProperties(blog, BlogVO.class);
        }).toList();
        // 6. 封装滚动分页结果（前端下次请求原样带回minTime和offset）
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(voList);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(os);
        return Result.ok(scrollResult);
    }

    /**
     * 装配笔记作者信息（昵称、头像）
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userMapper.selectById(userId);
        if (user != null) {
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }
    }

    /**
     * 装配当前用户是否已点赞该笔记（未登录则跳过）
     */
    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, user.getId().toString());
        blog.setIsLike(score != null);
    }
}
