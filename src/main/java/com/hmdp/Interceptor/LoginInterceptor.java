package com.hmdp.Interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.Constant.SessionConstant;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取session
        HttpSession session = request.getSession();
        // 获取session中的用户
        Object user = session.getAttribute(SessionConstant.USER);
        // 判断用户是否存在
        if (user == null) {
            // 不存在，返回401状态码
            response.setStatus(401);
            return false;
        }
        //存在，保存用户信息到ThreadLocal
        UserHolder.saveUser(BeanUtil.copyProperties((User) user, UserDTO.class));
        //放行
        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
