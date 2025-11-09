package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result selectById(Long id) {
        String key=CACHE_SHOP_KEY+id;
        //从Redis中查询是否存在商户信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (shopJson!=null) {
            //如果命中则直接返回数据
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //如果未命中则根据id查询数据库中的数据
        Shop shop = getById(id);
        //判断该用户是否存在，如果存在则将数据存入redis中，如果不存在直接返回不存在
        if (shop==null) {
            Result.fail("该商户不存在");
        }
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回商铺信息
        return Result.ok(shop);

    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id==null){
            return Result.fail("id不存在");
        }
        String key=CACHE_SHOP_KEY+id;
        //先更新数据库
        updateById(shop);
        //更新数据库后删除缓存
        stringRedisTemplate.delete(key);
        return Result.ok();
    }
}
