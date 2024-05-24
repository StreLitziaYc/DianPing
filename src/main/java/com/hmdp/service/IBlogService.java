package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Blog queryById(Long id);

    List<Blog> queryByCurrent(Integer current);

    Result likeBlog(Long blogId);

    List<UserDTO> queryByLikes(Long id);
}
