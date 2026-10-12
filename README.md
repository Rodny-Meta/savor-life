# 知味生活（savor-life）

基于 Spring Boot 3.2 + Redis + MySQL 8.4 构建的本地生活探店平台，覆盖商户查询、探店笔记、优惠券秒杀、签到与排行榜等核心业务，前后端独立完成。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.2 · Spring MVC · Spring AOP · MyBatis-Plus |
| 数据 | MySQL 8.4（utf8mb4） · Redis 7（Docker） |
| 前端 | Vue 3 · Nginx 部署 |
| 工程 | Docker Compose · JMeter · JUnit · Maven（阿里云镜像） |

## 核心技术点

### 优惠券秒杀：超卖治理三阶段演进

以 JMeter 压测数据驱动方案演进（压测脚本与结果见 `backend/scripts/`）：

| 阶段 | 方案 | 问题暴露 |
|---|---|---|
| 1 | 无锁「校验→扣减」 | 200 并发下订单与库存失衡——经典超卖 |
| 2 | 数据库条件更新 `stock=stock-1 where stock>0` | 单机超卖解决，进入一人一单问题 |
| 3 | synchronized + `AopContext.currentProxy()` | 集群环境下 JVM 锁失效 |
| 4 | Redis 分布式锁（setIfAbsent 原子加锁） | 锁误删风险 |
| 5 | Lua 脚本原子「校验线程标识 + 删除」 | 双实例 Nginx 轮询压测：单用户仅 1 单、无锁残留 ✅ |

### 缓存体系（旁路模式）

- 读路径：查 Redis → 未命中查库回填；写路径：先更新数据库再删除缓存
- **击穿**：互斥锁与逻辑过期双方案，逻辑过期用独立 key 前缀 `cache:shop:logic:` 存储包装结构避免解析冲突
- **雪崩**：TTL 加随机值；**穿透**：空值标记
- 热点商户接口实测：764ms → 14ms

### 其他

- **全局 ID 生成器**：64 位结构（1 符号位 + 31 位时间戳 + 32 位按天分段 Redis INCR 序列号），天然支持当日发号量统计
- **Redis 会话登录**：token 25 天有效期 + 活跃刷新 + ThreadLocal 传递当前用户，验证码 2 分钟过期
- **分布式锁规范**：`setIfAbsent(key, "1", 10s)` 原子命令完成加锁+过期，避免分步 setnx+expire 的永久死锁窗口

## 质量数据

| 指标 | 数值 |
|---|---|
| 秒杀终验（200 并发） | 订单与库存精确对齐，错误率 0% |
| 商户接口压测（100 并发 × 10 循环） | 吞吐 959.7/s · 平均响应 25ms · 错误率 0% |
| 缓存优化 | 764ms → 14ms |
| 自动化测试 | 10 用例全绿（线程隔离 / 字段注入防护 / 登录态验证） |

## 文档

- [技术方案](docs/技术方案.md)
- [开发进度](docs/开发进度.md)

压测原始数据（jmx 脚本 + jtl 结果）归档于 `backend/scripts/`。

## 本地运行

```bash
# 1. 启动 MySQL(3307) + Redis(6380)
docker compose up -d

# 2. 初始化数据库：执行 backend/src/main/resources/db/savor_life.sql

# 3. 启动后端（默认 8081）
cd backend && mvn spring-boot:run

# 4. 前端：nginx 部署 frontend/nginx-1.18.0/html/savor
```

> 本仓库为学习实践项目，参考了黑马点评等课程的设计思路，核心代码与压测验证均独立完成。
