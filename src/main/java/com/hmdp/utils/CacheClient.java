package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.Constant.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
@Slf4j
public class CacheClient {
    //线程池
    private static final ExecutorService CACHE_EXECUTOR = Executors.newSingleThreadExecutor();
    @Autowired
    StringRedisTemplate redisTemplate;

    /**
     * 普通写入缓存
     *
     * @param key   缓存的key值
     * @param value 要缓存的对象
     * @param time  缓存的持续时间
     * @param unit  持续时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        //设置N+n缓存，防止缓存雪崩
        time += RandomUtil.randomLong(time);
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 逻辑过期写入缓存,防止缓存击穿
     *
     * @param key   缓存的key值
     * @param value 要缓存的对象
     * @param time  缓存的持续时间
     * @param unit  持续时间单位
     */

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 不需要id的查询
     *
     * @param key        key
     * @param type       返回值的类型
     * @param dbFallBack 查询函数
     * @param time       缓存时间
     * @param unit       时间单位
     * @param <T>        实体类型
     * @return 查询结果
     */
    public <T> T query(String key, Class<T> type, Supplier<T> dbFallBack, Long time, TimeUnit unit) {
        //查询redis中是否有需要的数据
        String resJson = redisTemplate.opsForValue().get(key);
        //判断缓存是否为空
        if (StrUtil.isNotBlank(resJson)) {
            //存在直接返回
            return JSONUtil.toBean(resJson, type, false);
        }
        //判断是否是空对象
        if (resJson != null) {
            log.warn("命中空对象");
            return null;
        }
        //不存在查询数据库
        T res = dbFallBack.get();
        //判断数据库是否有数据
        if (res == null) {
            //缓存空对象
            redisTemplate.opsForValue().set(key, RedisConstants.NULL_VALUE, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在则将数据写入redis
        set(key, res, time, unit);
        //返回
        return res;
    }


    /**
     * 普通缓存查询，使用存储空对象的方法防止缓存穿透
     *
     * @param keyPrefix  key的前缀
     * @param id         要查询的id
     * @param type       查询的实体类型
     * @param dbFallBack 数据库查询方法
     * @param time       新缓存的过期时间
     * @param unit       时间单位
     * @param <T>        实体类型
     * @param <ID>       id类型
     * @return 查询到的结果
     */
    public <T, ID> T queryById(String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallBack, Long time, TimeUnit unit) {
        //查询redis中是否有需要的数据
        String key = keyPrefix + id;
        String resJson = redisTemplate.opsForValue().get(key);
        //判断缓存是否为空
        if (StrUtil.isNotBlank(resJson)) {
            //存在直接返回
            return JSONUtil.toBean(resJson, type, false);
        }
        //判断是否是空对象
        if (resJson != null) {
            log.warn("命中空对象");
            return null;
        }
        //不存在查询数据库
        log.warn("未命中缓存，访问数据库！");
        T res = dbFallBack.apply(id);
        //判断数据库是否有数据
        if (res == null) {
            //缓存空对象
            redisTemplate.opsForValue().set(key, RedisConstants.NULL_VALUE, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在则将数据写入redis
        set(key, res, time, unit);
        //返回
        return res;
    }

    private <T> boolean checkLogicalExpire(String key, Class<T> type, AtomicReference<T> dataHolder) {
        String redisDataJson = redisTemplate.opsForValue().get(key);
        //命中，判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class, false);
        LocalDateTime expireTime = redisData.getExpireTime();
        T data = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        dataHolder.set(data);
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期
            return false;
        }
        return true;
    }

    /**
     * 逻辑过期缓存查询，防止缓存击穿
     *
     * @param keyPrefix  key的前缀
     * @param id         要查询的id
     * @param type       查询的实体类型
     * @param dbFallBack 数据库查询方法
     * @param time       新缓存的过期时间
     * @param unit       时间单位
     * @param <T>        实体类型
     * @param <ID>       id类型
     * @return 查询到的结果
     */
    public <T, ID> T queryWithLogicalExpire(String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallBack, Long time, TimeUnit unit) {
        //从redis查询缓存数据
        String key = keyPrefix + id;
        //判断缓存是否存在
        String redisDataJson = redisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(redisDataJson)) {
            //未命中
            log.warn("逻辑过期缓存不存在！");
            return null;
        }
        AtomicReference<T> dataHolder = new AtomicReference<>();
        boolean isExpire = checkLogicalExpire(key, type, dataHolder);
        T data = dataHolder.get();
        if (!isExpire) {
            //未过期
            return data;
        }
        //过期，进行缓存重建
        String lockKey = RedisConstants.LOCK_KEY + key;
        //尝试获取互斥锁
        if (tryLock(lockKey)) {
            //获取锁成功，开启新线程来重建缓存
            //double check
            isExpire = checkLogicalExpire(key, type, dataHolder);
            data = dataHolder.get();
            if (!isExpire) {
                //未过期
                return data;
            }
            CACHE_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    log.warn("重建逻辑过期缓存！");
                    T value = dbFallBack.apply(id);
                    setWithLogicalExpire(key, value, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //返回当前缓存信息
        return data;
    }

    /**
     * 使用互斥锁来防止缓存击穿
     *
     * @param keyPrefix  key的前缀
     * @param id         查询id
     * @param type       实体类型
     * @param dbFallBack 查询方法
     * @param time       缓存时间
     * @param unit       时间单位
     * @param <T>        实体类型
     * @param <ID>       id类型
     * @return 查询数据
     */
    public <T, ID> T queryWithMutex(String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallBack, Long time, TimeUnit unit) {
        // 获取缓存
        String key = keyPrefix + id;
        String resJson = redisTemplate.opsForValue().get(key);
        // 判断受否命中
        if (StrUtil.isNotBlank(resJson)) {
            // 命中则直接返回
            return JSONUtil.toBean(resJson, type, false);
        }
        if (Objects.equals(resJson, RedisConstants.NULL_VALUE)) {
            // 命中空对象
            return null;
        }
        String lockKey = null;
        T res;
        try {
            lockKey = RedisConstants.LOCK_KEY + key;
            while (!tryLock(lockKey)) {
                Thread.sleep(10);
            }
            //获取到互斥锁
            //重建缓存
            //double check
            resJson = redisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(resJson)) {
                // 命中则直接返回
                return JSONUtil.toBean(resJson, type, false);
            }
            if (Objects.equals(resJson, RedisConstants.NULL_VALUE)) {
                // 命中空对象
                return null;
            }
            res = dbFallBack.apply(id);
            set(key, res, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unLock(lockKey);
        }
        return res;
    }

    /**
     * 尝试获取互斥锁
     *
     * @param lockKey 互斥锁的键名
     * @return 获取锁是否成功
     */
    private boolean tryLock(String lockKey) {
        Boolean isLock = redisTemplate.opsForValue().setIfAbsent(lockKey, RedisConstants.LOCK_VALUE);
        redisTemplate.expire(lockKey, RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(isLock);
    }

    /**
     * 释放互斥锁
     *
     * @param lockKey 互斥锁的键名
     */
    private void unLock(String lockKey) {
        redisTemplate.delete(lockKey);
    }
}
