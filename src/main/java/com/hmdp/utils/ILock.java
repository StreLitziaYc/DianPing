package com.hmdp.utils;

public abstract class ILock {
    /**
     * 尝试获取锁
     *
     * @param timeoutSec 锁自动释放的时长（秒）
     * @return 是否成功获取到锁
     */
    abstract public boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    abstract public void unlock();
}
