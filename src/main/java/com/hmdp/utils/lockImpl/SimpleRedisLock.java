package com.hmdp.utils.lockImpl;

import cn.hutool.core.util.BooleanUtil;
import com.hmdp.utils.ILock;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    @Resource
    StringRedisTemplate redisTemplate;

    private String lockName;
    private static final String LOCK_PREFIX = "lock:";

    public SimpleRedisLock(String lockName, StringRedisTemplate redisTemplate) {
        this.lockName = lockName;
        this.redisTemplate = redisTemplate;
    }
    @Override
    public boolean tryLock(long timeoutSec) {
        String value = String.valueOf(Thread.currentThread().getId());
        Boolean success = redisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + lockName, value, timeoutSec, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unlock() {
        redisTemplate.delete(LOCK_PREFIX + lockName);
    }
}
