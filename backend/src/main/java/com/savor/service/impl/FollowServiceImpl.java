package com.savor.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.savor.dto.Result;
import com.savor.dto.UserDTO;
import com.savor.entity.Follow;
import com.savor.entity.User;
import com.savor.mapper.FollowMapper;
import com.savor.mapper.UserMapper;
import com.savor.service.IFollowService;
import com.savor.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.savor.utils.RedisConstants.FOLLOW_KEY;

/**
 * 好友关注服务：
 * - Follow表是关系的事实来源（DB），Redis Set（follows:{userId}）存"我关注的人"的镜像，
 *   专门为共同关注（SINTER 交集）提供 O(N) 内存计算，避免数据库自连接查询
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOW_KEY + userId;
        // 2. 判断关注还是取关
        if (Boolean.TRUE.equals(isFollow)) {
            // 3. 关注：新增关系记录
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                // 同步维护Redis关注集合（共同关注要用）
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            // 4. 取关：删除关系记录
            boolean isSuccess = remove(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId));
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        // 查询是否关注（count>0 即关注了）
        Long count = lambdaQuery()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId)
                .count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long targetId) {
        // 1. 求交集：我关注的人 ∩ 对方关注的人
        Long userId = UserHolder.getUser().getId();
        String key1 = FOLLOW_KEY + userId;
        String key2 = FOLLOW_KEY + targetId;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2. 解析出用户id
        List<Long> ids = intersect.stream().map(Long::valueOf).toList();
        // 3. 查询用户转DTO
        List<UserDTO> users = ids.stream().map(id -> {
            User user = userMapper.selectById(id);
            return BeanUtil.copyProperties(user, UserDTO.class);
        }).toList();
        return Result.ok(users);
    }
}
