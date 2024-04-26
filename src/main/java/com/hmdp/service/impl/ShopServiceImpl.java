package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.Constant.MessageConstant;
import com.hmdp.Constant.RedisConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    CacheClient cacheClient;

    private Shop dbQueryById(Long id) {
        return getById(id);
    }

    @Override
    @Transactional
    public Result queryById(Long id) {
        Shop shop = cacheClient.queryWithLogicalExpire(
                RedisConstants.LOGICAL_CASH_SHOP_KEY, id, Shop.class, this::dbQueryById,
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail(MessageConstant.SHOP_NOT_EXIT);
        }
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
