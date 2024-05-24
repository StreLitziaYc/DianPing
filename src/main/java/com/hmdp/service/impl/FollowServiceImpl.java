package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.MessageConstant;
import com.hmdp.constant.RedisConstants;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.exception.MySqlException;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    IUserService userService;

    @Override
    public void follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FOLLOW_KEY + userId;
        if (isFollow) {
            // 关注用户
            Follow follow = Follow.builder()
                    .userId(userId)
                    .followUserId(followUserId)
                    .build();
            boolean success = save(follow);
            // 写入Redis
            if (success) {
                redisTemplate.opsForSet().add(key, followUserId.toString());
            } else {
                throw new MySqlException(MessageConstant.FOLLOW_FAILED);
            }
        } else {
            // 取消关注，从数据库中删除数据
            boolean success = remove(new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId));
            // 从Redis移除
            if (success) {
                redisTemplate.opsForSet().remove(key, followUserId.toString());
            } else {
                throw new MySqlException(MessageConstant.UNFOLLOW_FAILED);
            }
        }
    }

    @Override
    public Boolean isFollow(Long followUserId) {
        // 查询是否关注
        Long count = lambdaQuery().eq(Follow::getFollowUserId, followUserId)
                .eq(Follow::getUserId, UserHolder.getUser().getId())
                .count();
        return count > 0;
    }

    @Override
    public List<UserDTO> commonFollow(Long targetId) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key1 = RedisConstants.FOLLOW_KEY + userId;
        // 求交集
        String key2 = RedisConstants.FOLLOW_KEY + targetId;
        Set<String> intersect = redisTemplate.opsForSet().intersect(key1, key2);
        // 解析id集合
        if (intersect == null || intersect.isEmpty()) return Collections.emptyList();
        List<Long> ids = intersect.stream().map(Long::valueOf).toList();
        // 查询用户
        List<UserDTO> userDTOS = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();
        return userDTOS;
    }
}
