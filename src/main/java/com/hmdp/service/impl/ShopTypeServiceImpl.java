package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.Constant.RedisConstants;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.CacheClient;
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

    @Autowired
    CacheClient cacheClient;

    private List<ShopType> dbQueryList() {
        return query().orderByAsc("sort").list();
    }

    @Override
    public List<ShopType> queryList() {
        //返回数据
        return cacheClient.query(RedisConstants.CACHE_SHOP_TYPE_KEY, List.class, this::dbQueryList, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
    }
}
