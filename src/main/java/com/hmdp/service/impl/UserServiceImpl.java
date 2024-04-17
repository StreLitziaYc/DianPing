package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.Constant.MessageConstant;
import com.hmdp.Constant.SessionConstant;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.Constant.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //判断手机号是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            //不符合则返回错误信息
            return Result.fail(MessageConstant.PHONG_ERROR);
        }
        //符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到session
        session.setAttribute(SessionConstant.CODE, code);
        //发送验证码
        log.info("发送验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //不符合则返回错误信息
            return Result.fail(MessageConstant.PHONG_ERROR);
        }
        //校验验证码
        Object cacheCode = session.getAttribute(SessionConstant.CODE);
        String code = loginForm.getCode();
        //不一致报错
        if (cacheCode == null || !((String) cacheCode).equals(code)) {
            return Result.fail(MessageConstant.CODE_ERROR);
        }
        //一致，根据手机号查询用户
        User user = lambdaQuery().eq(User::getPhone, phone).one();
        //不存在创建新用户
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        //保存用户信息到session
        session.setAttribute(SessionConstant.USER, user);
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = User.builder()
                .phone(phone)
                .nickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10))
                .build();
        save(user);
        return user;
    }
}
