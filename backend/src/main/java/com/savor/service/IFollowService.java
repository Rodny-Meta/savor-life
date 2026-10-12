package com.savor.service;

import com.savor.dto.Result;
import com.savor.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注/取关用户：Follow表落库/删除，同步维护Redis关注集合（供共同关注SINTER）
     * @param followUserId 要关注/取关的用户id
     * @param isFollow true=关注 false=取关
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 是否关注了指定用户（详情页展示关注按钮状态）
     */
    Result isFollow(Long followUserId);

    /**
     * 共同关注：当前用户与目标用户关注集合的交集（SINTER）
     */
    Result followCommons(Long targetId);
}
