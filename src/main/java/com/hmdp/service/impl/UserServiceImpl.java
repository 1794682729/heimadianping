package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
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
        String key = LOGIN_CODE_KEY + phone;
        //将验证码存入redis中
        stringRedisTemplate.opsForValue().set(key,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
//        session.setAttribute("code", code);
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
//        Object code = session.getAttribute("code");
        String key = LOGIN_CODE_KEY + loginForm.getPhone();
        //从redis中获取验证码并校验
        String code = stringRedisTemplate.opsForValue().get(key);
        if (code==null ||! code.equals(loginFormCode)) {
            return Result.fail("验证码错误");
        }
        //如果验证码验证成功，则立即删除此验证码防止验证码重复使用
        stringRedisTemplate.delete(key);
        User user = query().eq("phone", loginForm.getPhone()).one();
        if (user==null) {
            //如果用户不存在，创建新用户并保存
           user= CreateByPhone(loginForm.getPhone());
        }
        //生成随机token,作为登录令牌
        String token = UUID.randomUUID().toString();
        //将user转为hash存储
        UserDTO userDTO=BeanUtil.copyProperties(user,UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //存储
        String tokenKey =LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);
//        session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));
        return Result.ok(token);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        String token= request.getHeader("authorization");
        String key = LOGIN_USER_KEY+token;
        stringRedisTemplate.delete(key);

        return Result.ok();
    }

    private User CreateByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomNumbers(6));
        save(user);
        return user;
    }
}
