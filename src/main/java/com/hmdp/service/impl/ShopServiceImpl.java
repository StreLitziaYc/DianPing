package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.MessageConstant;
import com.hmdp.constant.RedisConstants;
import com.hmdp.constant.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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

    @Override
    public List<Shop> queryByType(Integer typeId, Integer current, Double x, Double y) {
        // 判断是否需要分页
        // 根据类型分页查询
        if (x == null && y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return page.getRecords();
        }
        // 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 查询redis，并排序分页
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo()
                .search(
                        RedisConstants.SHOP_GEO_KEY + typeId,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 解析出id
        if (results == null) return Collections.emptyList();
        List<Long> ids = new ArrayList<>();
        Map<Long, Distance> distanceMap = new HashMap<>();
        results.getContent().stream().skip(from).forEach(result -> {
            Long shopId = Long.valueOf(result.getContent().getName());
            ids.add(shopId);
            Distance distance = result.getDistance();
            distanceMap.put(shopId, distance);
        });
        // 根据id查询shop
        if (ids.isEmpty()) return Collections.emptyList();
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = lambdaQuery()
                .in(Shop::getId, ids)
                .last("ORDER BY FIELD(id, " + idStr + ")")
                .list();
        shops.forEach(shop -> shop.setDistance(distanceMap.get(shop.getId()).getValue()));
        // 返回
        return shops;
    }
}
