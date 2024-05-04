package com.hmdp.utils.lockImpl;

import cn.hutool.core.util.BooleanUtil;
import com.hmdp.utils.ILock;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock extends ILock {
    private static final String LOCK_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString();
    private final String lockName;
    StringRedisTemplate redisTemplate;

    public SimpleRedisLock(String lockName, StringRedisTemplate redisTemplate) {
        this.lockName = lockName;
        this.redisTemplate = redisTemplate;
    }

    public boolean tryLock(long timeoutSec) {
        //获取线程标识
        String threadId = ID_PREFIX + "-" + Thread.currentThread().getId();
        //尝试获取锁
        Boolean success = redisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + lockName, threadId, timeoutSec, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(success);
    }


    public void unlock() {
        String key = LOCK_PREFIX + lockName;
        //获取线程标识
        String threadId = ID_PREFIX + "-" + Thread.currentThread().getId();
        //判断是否是对应的线程
        if (threadId.equals(redisTemplate.opsForValue().get(key))) {
            redisTemplate.delete(key);
        }
    }
}
