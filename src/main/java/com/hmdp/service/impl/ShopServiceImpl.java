package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.Constant.MessageConstant;
import com.hmdp.Constant.RedisConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    StringRedisTemplate redisTemplate;

    @Override
    public Result queryById(Long id) {
        //查询redis中是否有需要的数据
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        Map<Object, Object> shopMap = redisTemplate.opsForHash().entries(key);
        //判断是否是空对象
        if (shopMap.containsKey(RedisConstants.NULL_KEY)) {
            log.warn("命中空对象");
            return Result.fail(MessageConstant.SHOP_NOT_EXIT);
        }
        //判断缓存是否为空
        if (!shopMap.isEmpty()) {
            //存在直接返回
            Shop shop = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
            return Result.ok(shop);
        }
        //不存在查询数据库
        Shop shop = getById(id);
        //判断数据库是否有数据
        if (shop == null) {
            //缓存空对象
            HashMap<String, String> nullObject = new HashMap<>();
            nullObject.put(RedisConstants.NULL_KEY, RedisConstants.NULL_VALUE);
            redisTemplate.opsForHash().putAll(key, nullObject);
            redisTemplate.expire(key, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail(MessageConstant.SHOP_NOT_EXIT);
        }
        //存在则将数据写入redis
        Map<String, Object> newShopMap = BeanUtil.beanToMap(shop, new HashMap<>(), CopyOptions.create()
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue == null ? "" : fieldValue.toString()));
        redisTemplate.opsForHash().putAll(key, newShopMap);
        //设置N+n缓存，防止缓存雪崩
        redisTemplate.expire(key, RedisConstants.CACHE_SHOP_TTL + RandomUtil.randomLong(RedisConstants.CACHE_SHOP_TTL), TimeUnit.MINUTES);
        //返回
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        //判断id是否存在
        if (id == null) {
            return Result.fail(MessageConstant.SHOP_ID_EMPTY);
        }
        //更新数据库
        updateById(shop);
        //清空缓存
        redisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
