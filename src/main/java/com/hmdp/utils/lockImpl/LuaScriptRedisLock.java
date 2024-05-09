package com.hmdp.utils.lockImpl;

import cn.hutool.core.util.BooleanUtil;
import com.hmdp.utils.ILock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class LuaScriptRedisLock extends ILock {
    private static final String LOCK_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString();
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("script/unlock.lua"));//脚本相对于class path的路径地址
        UNLOCK_SCRIPT.setResultType(Long.class);//指定返回值类型
    }
    private final String lockName;
    StringRedisTemplate redisTemplate;

    public LuaScriptRedisLock(String lockName, StringRedisTemplate redisTemplate) {
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
        redisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(LOCK_PREFIX + lockName), //锁的key
                ID_PREFIX + "-" + Thread.currentThread().getId() //锁的线程标识
        );
    }
}
