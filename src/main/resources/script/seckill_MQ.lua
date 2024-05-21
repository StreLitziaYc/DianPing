---
--- Created by Strelitzia.
--- DateTime: 2024/5/18 20:43
---
---
--- Created by Strelitzia.
--- DateTime: 2024/5/12 20:33
---
-- 参数列表
local voucherId = ARGV[1] -- 优惠券id
local userId = ARGV[2] -- 用户id
local orderId = ARGV[3] -- 订单id

-- 数据key
local stockKey = 'seckill:stock:' .. voucherId -- 库存key
local orderKey = 'seckill:order:' .. voucherId -- 订单key

-- 脚本业务
-- 判断库存是否充足 get stockKey
if (tonumber(redis.call('get', stockKey)) <= 0) then
	-- 库存不足，返回1
	return 1
end
-- 判断用户是否下单 sismember orderKey userId
if (redis.call('sismember', orderKey, userId) == 1) then
	-- 存在，说明是重复下单
	return 2
end
-- 扣减库存 incrby stockKey -1
redis.call('incrby', stockKey, -1)
-- 下单 sadd orderKey userId
redis.call('sadd', orderKey, userId)
-- 发送消息到队列 xadd stream.orders * k1 v1 k2 v2 k3 v3
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0