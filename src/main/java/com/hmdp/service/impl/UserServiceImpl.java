package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_CODE_TTL;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

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
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号输入错误");
        }
        String code = RandomUtil.randomNumbers(6);
        //将验证码存入redis中
//        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        session.setAttribute("code", code);
        //发送验证码
        log.debug("验证码是：{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式错误");
        }
        String loginFormCode = loginForm.getCode();
        Object code = session.getAttribute("code");
        if (code==null ||! code.toString().equals(loginFormCode)) {
            return Result.fail("验证码错误");
        }
        User user = query().eq("phone", loginForm.getPhone()).one();
        if (user==null) {
           user= CreateByPhone(loginForm.getPhone());
        }
        session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));
        return Result.ok();
    }

    private User CreateByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setPassword(USER_NICK_NAME_PREFIX+RandomUtil.randomNumbers(6));
        return user;
    }
}
