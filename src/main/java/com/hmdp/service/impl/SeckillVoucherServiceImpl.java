package com.hmdp.service.impl;

import com.hmdp.constant.RedisConstants;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    @Transactional
    public void save2Redis(SeckillVoucher seckillVoucher) {
        Long voucherId = seckillVoucher.getVoucherId();
        redisTemplate.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY + voucherId, seckillVoucher.getStock().toString());}
}
