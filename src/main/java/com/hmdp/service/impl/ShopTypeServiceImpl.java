package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result shopTypeList() {
        String key=CACHE_SHOP_TYPE_KEY;
        String shopTypeListJson = stringRedisTemplate.opsForValue().get(key);
        if(shopTypeListJson!=null){
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeListJson, ShopType.class);
            return Result.ok(shopTypes);
        }
        List<ShopType> shopTypeList = query().list();
        if(shopTypeList==null){
            Result.fail("不存在");
        }
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shopTypeList));
        return Result.ok(shopTypeList);
    }
}
