package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.MessageConstant;
import com.hmdp.constant.RedisConstants;
import com.hmdp.constant.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.exception.MySqlException;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    IUserService userService;
    @Autowired
    StringRedisTemplate redisTemplate;

    @Override
    public Blog queryById(Long id) {
        Blog blog = getById(id);
        queryBlogUser(blog);
        setIsLiked(blog);
        return blog;
    }

    private void setIsLiked(Blog blog) {
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        // 处理用户未登录的情况
        if (UserHolder.getUser() == null) return;
        Long userId = UserHolder.getUser().getId();
        Boolean isLiked = redisTemplate.opsForSet().isMember(key, userId.toString());
        blog.setIsLike(isLiked);
    }
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public List<Blog> queryByCurrent(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            setIsLiked(blog);
        });
        return records;
    }

    @Override
    @Transactional
    public Result likeBlog(Long blogId) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 判断是否已点赞
        String key = RedisConstants.BLOG_LIKED_KEY + blogId;
        Boolean isLiked = redisTemplate.opsForSet().isMember(key, userId.toString());
        if (BooleanUtil.isFalse(isLiked)) {
            // 数据库+1
            boolean success = lambdaUpdate().setSql("liked = liked + 1").eq(Blog::getId, blogId).update();
            // 更新Redis
            if (success) {
                redisTemplate.opsForSet().add(key, userId.toString());
            } else {
                throw new MySqlException(MessageConstant.LIKE_FAILED);
            }
        } else {
            // 数据库-1
            boolean success = lambdaUpdate().setSql("liked = liked - 1").eq(Blog::getId, blogId).update();
            // 更新Redis
            if (success) {
                redisTemplate.opsForSet().remove(key, userId.toString());
            } else {
                throw new MySqlException(MessageConstant.LIKE_FAILED);
            }
        }
        return Result.ok();
    }
}
