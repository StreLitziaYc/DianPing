package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.LockConstant;
import com.hmdp.constant.MessageConstant;
import com.hmdp.constant.RedisConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.exception.LuaScriptException;
import com.hmdp.exception.MySqlException;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.ILock;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.lockImpl.RedissonLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    //线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("script/seckill_MQ.lua"));//脚本相对于class path的路径地址
        SECKILL_SCRIPT.setResultType(Long.class);//指定返回值类型
    }

    @Autowired
    ISeckillVoucherService seckillVoucherService;
    @Autowired
    RedisIdWorker redisIdWorker;
    @Autowired
    StringRedisTemplate redisTemplate;
    // 阻塞队列
    private BlockingQueue<VoucherOrder> orders = new ArrayBlockingQueue<>(1024 * 1024);
    //代理对象
    private IVoucherOrderService proxyService;

    /**
     * 项目启动的时候，就将订单生成任务交给线程池
     */
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new orderHandlerWithMq());
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否已经开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail(MessageConstant.SECKILL_NOT_BEGIN);
        }
        //判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail(MessageConstant.SECKILL_END);
        }
        //判断库存是是否充足
        if (voucher.getStock() < 1) {
            return Result.fail(MessageConstant.STOCK_NOT_ENOUGH);
        }
        String lockName = LockConstant.LOCK + UserHolder.getUser().getId().toString();
        ILock lock = new RedissonLock(lockName); // 悲观锁
        if (!lock.tryLock(LockConstant.LOCK_TTL)) {
            //未成功获取锁
            return Result.fail(MessageConstant.REPEAT_PURCHASE);
        }
        try { //悲观锁
            //获取代理对象
            //若直接调用这个方法，事务会失效。因为事务是通过Spring对当前对象的代理实现的，直接调用是访问对象本身而不是代理
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucher);
        } finally {
            //释放锁
            lock.unlock();
        }
    }

    @Transactional
    public Result createVoucherOrder(SeckillVoucher voucher) {
        Long voucherId = voucher.getVoucherId();
        //一人一单
        Long userId = UserHolder.getUser().getId();

        Long count = lambdaQuery()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId)
                .count();
        if (count > 0) {
            return Result.fail(MessageConstant.REPEAT_PURCHASE);
        }
        //扣减库存
        boolean success = seckillVoucherService.lambdaUpdate()
                .setSql("stock = stock - 1")
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .gt(SeckillVoucher::getStock, 0) //乐观锁
                .update();
        if (!success) {
            //扣减失败
            return Result.fail(MessageConstant.STOCK_NOT_ENOUGH);
        }
        //生成订单
        VoucherOrder voucherOrder = VoucherOrder.builder()
                .id(redisIdWorker.nextId(RedisConstants.ORDER_ID_PREFIX))
                .userId(userId)
                .voucherId(voucherId)
                .build();
        save(voucherOrder);
        return Result.ok(voucherOrder.getId());
    }

    /**
     * 使用阻塞队列实现异步秒杀
     *
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucherAsync(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 使用Lua脚本实现在Redis中的查询
        // 执行Lua脚本
        Long result = redisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        // 判断结果
        if (result == null) throw new LuaScriptException();
        switch (result.intValue()) {
            case 1 -> {
                log.warn("库存不足");
                return Result.fail(MessageConstant.STOCK_NOT_ENOUGH);
            }
            case 2 -> {
                log.warn("重复购买");
                return Result.fail(MessageConstant.REPEAT_PURCHASE);
            }
        }
        long orderId = redisIdWorker.nextId(RedisConstants.ORDER_ID_PREFIX);
        //生成订单
        VoucherOrder voucherOrder = VoucherOrder.builder()
                .id(redisIdWorker.nextId(RedisConstants.ORDER_ID_PREFIX))
                .userId(userId)
                .voucherId(voucherId)
                .build();
        orders.add(voucherOrder);
        proxyService = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

    /**
     * 异步创建订单
     *
     * @param order
     */
    @Override
    @Transactional
    public void createOrderAsync(VoucherOrder order) {
        Long voucherId = order.getVoucherId();
        //一人一单
        Long userId = order.getUserId();
        Long count = lambdaQuery()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId)
                .count();
        if (count > 0) {
            throw new MySqlException(MessageConstant.REPEAT_PURCHASE);
        }
        //扣减库存
        boolean success = seckillVoucherService.lambdaUpdate()
                .setSql("stock = stock - 1")
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .gt(SeckillVoucher::getStock, 0) //乐观锁
                .update();
        if (!success) {
            //扣减失败
            throw new MySqlException(MessageConstant.STOCK_NOT_ENOUGH);
        }
        // 生成订单
        save(order);
    }

    /**
     * 基于消息队列实现异步秒杀
     *
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucherAsyncWithMq(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long orderId = redisIdWorker.nextId(RedisConstants.ORDER_ID_PREFIX);
        // 使用Lua脚本实现在Redis中的查询
        // 执行Lua脚本
        Long result = redisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                orderId.toString()
        );
        // 判断结果
        if (result == null) throw new LuaScriptException();
        switch (result.intValue()) {
            case 1 -> {
                log.warn("库存不足");
                return Result.fail(MessageConstant.STOCK_NOT_ENOUGH);
            }
            case 2 -> {
                log.warn("重复购买");
                return Result.fail(MessageConstant.REPEAT_PURCHASE);
            }
        }
        proxyService = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

    private class orderHandlerWithMq implements Runnable {

        private final String streamName = "stream.orders";
        private final String groupName = "g1", consumerName = "c1";
        @Override
        public void run() {
            while (true) {
                try {
                    // 获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders
                    List<MapRecord<String, Object, Object>> list = redisTemplate.opsForStream().read(
                            Consumer.from(groupName, consumerName),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(streamName, ReadOffset.lastConsumed())
                    );
                    // 判断消息是否获取成功
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    // 解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 写入订单
                    proxyService.createOrderAsync(voucherOrder);
                    // ACK确认 SACK stream.orders g1 id
                    redisTemplate.opsForStream().acknowledge(streamName, groupName, record.getId());
                } catch (Exception e) {
                    log.error("订单处理异常:", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 获取消息队列中的订单信息 XPENDING stream.orders g1 - + 1
                    PendingMessages pendingMessages = redisTemplate.opsForStream().pending(
                            streamName,
                            groupName,
                            Range.unbounded(),
                            1
                    );
                    // 判断消息是否获取成功
                    if (pendingMessages.isEmpty()) {
                        continue;
                    }
                    // 解析消息中的订单信息
                    // 将当前消息重新分配给消费者
                    List<MapRecord<String, Object, Object>> list = redisTemplate.opsForStream().claim(
                            streamName,
                            groupName,
                            consumerName,
                            Duration.ofMillis(1),
                            pendingMessages.get(0).getId()
                    );
                    Map<Object, Object> values = list.get(0).getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 写入订单
                    proxyService.createOrderAsync(voucherOrder);
                    // ACK确认 SACK stream.orders g1 id
                    redisTemplate.opsForStream().acknowledge(streamName, groupName, list.get(0).getId());
                } catch (Exception e) {
                    log.error("订单处理异常:", e);
                    // 若处理不成功，则等一下再尝试
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    private class orderHandlerWithBlockingQueue implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    //获取订单
                    VoucherOrder order = orders.take();
                    //插入数据库
                    proxyService.createOrderAsync(order);
                } catch (Exception e) {
                    log.error("订单处理异常:", e);
                }
            }
        }
    }

}
