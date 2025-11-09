package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
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

import static com.hmdp.utils.RedisConstants.*;

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
        //防止缓存穿透
//        Shop shop = queryWithPassThrough(id);
        //缓存击穿
        Shop shop = getCacheWithMutexForHotKey(id);
        if (shop == null) {
            Result.fail("商户不存在");
        }
        return Result.ok(shop);

    }
    //防止缓存击穿
    public Shop getCacheWithMutexForHotKey(Long id) {
        Shop shop=null;
        String key=CACHE_SHOP_KEY+id;
        //从Redis中查询是否存在商户信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            //如果命中则直接返回数据
             shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断是否命中的是空值，经过上一轮判断能到这个if的要么是null,要么是"";
        if (shopJson!=null){
            //返回错误信息
            return null;
        }
        String lockKey="shop:lock:"+id;
        Boolean lock = tryLock(lockKey);
        try {

            if (!lock) {
                Thread.sleep(50);
                return getCacheWithMutexForHotKey(id);
            }
            shop = getById(id);
            //模拟重建的时间
            Thread.sleep(200);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), 30, TimeUnit.SECONDS);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
        finally {
            unlock(lockKey);
        }
        return shop;
    }

    public  Shop queryWithPassThrough(Long id) {
        String key=CACHE_SHOP_KEY+id;
        //从Redis中查询是否存在商户信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            //如果命中则直接返回数据
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断是否命中的是空值，经过上一轮判断能到这个if的要么是null,要么是"";
        if (shopJson!=null){
            //返回错误信息
            return null;
        }
        //如果未命中则根据id查询数据库中的数据
        Shop shop = getById(id);
        //判断该用户是否存在，如果存在则将数据存入redis中，如果不存在直接返回不存在
        if (shop==null) {

            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            Result.fail("该商户不存在");
        }
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }
    //获取锁
    public Boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }
    //释放锁
    public void unlock(String key) {
        stringRedisTemplate.delete(key);
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
