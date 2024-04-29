package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.LockConstant;
import com.hmdp.constant.MessageConstant;
import com.hmdp.constant.RedisConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.ILock;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.lockImpl.SimpleRedisLock;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    ISeckillVoucherService seckillVoucherService;
    @Autowired
    RedisIdWorker redisIdWorker;
    @Autowired
    StringRedisTemplate redisTemplate;

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
        ILock lock = new SimpleRedisLock(lockName, redisTemplate); // 悲观锁
        if (!lock.tryLock(LockConstant.LOCK_TTL)) {
            //未成功获取锁

            return Result.fail(MessageConstant.REPEAT_PURCHASE);
        }
        try { //悲观锁
            //获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucher);
        }finally {
            //释放锁
            lock.unlock();
        }
    }

    @Transactional
    public Result createVoucherOrder(SeckillVoucher voucher) {
        Long voucherId = voucher.getVoucherId();
        //一人一单
        Long userId = UserHolder.getUser().getId();
        {
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
    }
}
