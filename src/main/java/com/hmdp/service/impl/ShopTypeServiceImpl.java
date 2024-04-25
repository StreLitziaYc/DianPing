package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.Constant.RedisConstants;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    StringRedisTemplate redisTemplate;

    @Override
    public List<ShopType> queryList() {
        //查询缓存
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        List<String> typeStringList = redisTemplate.opsForList().range(key, 0L, -1L);
        //判断是否命中
        if (typeStringList != null && !typeStringList.isEmpty()) {
            //命中
            return typeStringList.stream()
                    .map((typeString) -> JSONUtil.toBean(typeString, ShopType.class))
                    .collect(Collectors.toList());
        }
        //未命中，进行查询
        List<ShopType> typeList = query().orderByAsc("sort").list();
        //存入缓存
        typeStringList = typeList.stream()
                .map((shopType) -> JSONUtil.toJsonStr(shopType))
                .collect(Collectors.toList());
        redisTemplate.opsForList().leftPushAll(key, typeStringList);
        //N+n防止缓存雪崩
        redisTemplate.expire(key,
                RedisConstants.CACHE_SHOP_TTL + RandomUtil.randomLong(RedisConstants.CACHE_SHOP_TTL),
                TimeUnit.MINUTES);
        //返回数据
        return typeList;
    }
}
