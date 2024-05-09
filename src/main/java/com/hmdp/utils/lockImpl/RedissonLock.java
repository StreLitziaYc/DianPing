package com.hmdp.utils.lockImpl;

import com.hmdp.utils.BeanHelper;
import com.hmdp.utils.ILock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

public class RedissonLock extends ILock {
    private static final RedissonClient redissonClient = BeanHelper.getBean(RedissonClient.class);
    private RLock lock;

    public RedissonLock(String lockName) {
        lock = redissonClient.getLock(lockName);
    }
    @Override
    public boolean tryLock(long timeoutSec) {
        try {
            return lock.tryLock(-1, timeoutSec, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void unlock() {
        lock.unlock();
    }
}
