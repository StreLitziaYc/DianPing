package com.hmdp.Service;

import com.hmdp.service.IShopTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ShopTypeServiceTest {
    @Autowired
    IShopTypeService shopTypeService;

    @Test
    public void ListTest() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            shopTypeService.queryList();
        }
        long end = System.currentTimeMillis();
        System.out.println("计时 Redis: " + (end - start));
        start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            shopTypeService.query().orderByAsc("sort").list();
        }
        end = System.currentTimeMillis();
        System.out.println("计时 MySql: " + (end - start));
    }
}
