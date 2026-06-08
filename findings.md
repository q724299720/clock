# Findings

## Android
- 当前移动端已经是“本地优先”架构，Room 与调度链路可以保留，不需要为了上云重写提醒逻辑。
- 游客模式和登录模式共存时，同步队列必须按 `userId` 过滤，否则游客数据会误上传。
- `clientUuid` 是服务端幂等 UPSERT 的核心，不能只依赖本地负数临时 ID。
- `alarm_logs` 若不上 `deviceId + logHash`，重复上传很容易在服务端产生重复记录。
- 当前提醒首页和模板页都没有“新建倒计时”入口，导致倒计时功能只能编辑/运行，无法通过正式 UI 链路创建。

## Backend
- 对当前业务来说，单体 Spring Boot + MySQL 足够，不需要先拆微服务。
- 软删除继续沿用 `alarms.status`，这样 Android 和服务端字段映射最简单。
- 用户禁用接受最长 15 分钟 access token 延迟生效，能避免每请求查库。
- 服务端 UTC 一致化必须同时覆盖：
  - MySQL
  - JVM `-Duser.timezone=UTC`
  - REST API ISO 8601 带时区

## Admin Web
- 用单页管理台就足够覆盖 v1 用户、提醒、日志、审计场景。
- Element Plus 首包较大，当前 build 有 chunk size warning，但不阻塞功能落地。

## Verification Summary
- 后端模块已可通过 `:server:test`。
- Android 模块已可通过 `assembleDebug testDebugUnitTest`。
- 管理台已可完成 `npm run build`。
