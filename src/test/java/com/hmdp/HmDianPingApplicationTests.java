package com.hmdp;

import com.hmdp.constant.RedisConstants;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    IShopService shopService;
    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    public void loadShopData() {
        // 获取店铺信息
        List<Shop> shops = shopService.list();
        // 按照typeId进行数据分批
        Map<Long, List<Shop>> shopMap = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 分批写入Redis
        for (Map.Entry<Long, List<Shop>> entry: shopMap.entrySet()) {
            Long typeId = entry.getKey();
            List<Shop> shopList = entry.getValue();
            // 所有的店铺信息
            List<RedisGeoCommands.GeoLocation<String>> locations = shopList.stream()
                    .map(shop -> new RedisGeoCommands.GeoLocation<String>(
                            shop.getId().toString(),
                            new Point(shop.getX(), shop.getY())
                    ))
                    .toList();
            redisTemplate.opsForGeo().add(RedisConstants.SHOP_GEO_KEY + typeId, locations);
        }
    }
}
