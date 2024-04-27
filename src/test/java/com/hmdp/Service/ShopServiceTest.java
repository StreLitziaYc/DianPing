package com.hmdp.Service;

import com.hmdp.constant.RedisConstants;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

@SpringBootTest
public class ShopServiceTest {
    @Autowired
    IShopService shopService;
    @Autowired
    CacheClient cacheClient;
    @Test
    public void preload() {
        Shop shop = shopService.getById(1);
        cacheClient.setWithLogicalExpire(RedisConstants.LOGICAL_CASH_SHOP_KEY + 1, shop, 2L, TimeUnit.SECONDS);
    }
}
