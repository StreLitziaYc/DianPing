## 项目介绍
基于 Spring Boot + Redis 的店铺点评，实现了找店铺 => 秒杀商品 => 写点评 => 看热评 => 点赞关注 => 关注 Feed 流 的完整业务流程

## 技术选型（后端）
Spring 相关
- Spring Boot 2.x
- Spring MVC

数据存储层
- MySQL：存储数据
- MyBatis Plus：数据访问框架

Redis 相关
- spring-data-redis：操作 Redis
- Lettuce：操作 Redis 的高级客户端
- Apache Commons Pool：用于实现 Redis 连接池
- Redisson：基于 Redis 的分布式数据网格

工具库
- HuTool：工具库合集
- Lombok：注解式代码生成工具
