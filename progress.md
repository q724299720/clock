# Progress

## 2026-06-06

### Android / Reminder
- 完成首页、日历、模板、设置、提醒编辑页等 UI 和提醒链路修复。
- 完成真机提醒验证、3 分钟闹钟验证、全屏提醒验证、滑动删除验证。
- 建立 [测试计划.md](/E:/programdata/Seafile/AI/clock/测试计划.md) 并完成一轮主要功能测试。

### Backend / Admin
- 新增 `:server` Gradle 模块并接入 Spring Boot。
- 新增后端能力：
  - JWT access token
  - refresh token 持久化
  - `/api/v1/auth/*`
  - `/api/v1/sync/bootstrap`
  - `/api/v1/sync/alarms/push`
  - `/api/v1/sync/alarms/pull`
  - `/api/v1/sync/alarm-logs/batch`
  - `/api/v1/admin/users`
  - `/api/v1/admin/alarms`
  - `/api/v1/admin/alarm-logs`
  - `/api/v1/admin/audit-logs`
- 新增 Flyway 初始化脚本：
  - [V1__init.sql](/E:/programdata/Seafile/AI/clock/server/src/main/resources/db/migration/V1__init.sql)

### Admin Web
- 新增 [admin-web/package.json](/E:/programdata/Seafile/AI/clock/admin-web/package.json) 和 Vue 3 + Vite + Element Plus 管理台骨架。
- 执行：
  - `npm install`
  - `npm run build`
- `admin-web/dist` 已成功生成。

### Android Remote Migration
- 移除移动端 MySQL JDBC / Hikari 依赖。
- 新增 HTTP API 客户端：
  - [ApiClient.kt](/E:/programdata/Seafile/AI/clock/app/src/main/java/com/smartclock/data/remote/api/ApiClient.kt)
- 本地模型新增：
  - `clientUuid`
  - `logHash`
  - `deviceId`
  - encrypted `accessToken / refreshToken`
- Room 升级到 v4，并新增迁移：
  - [DatabaseMigrations.kt](/E:/programdata/Seafile/AI/clock/app/src/main/java/com/smartclock/data/local/DatabaseMigrations.kt)

### Ops
- 新增部署与运维脚本：
  - [setup.sh](/E:/programdata/Seafile/AI/clock/ops/setup.sh)
  - [deploy.sh](/E:/programdata/Seafile/AI/clock/ops/deploy.sh)
  - [backup.sh](/E:/programdata/Seafile/AI/clock/ops/backup.sh)
  - [healthcheck.sh](/E:/programdata/Seafile/AI/clock/ops/healthcheck.sh)
- 新增 nginx / systemd 模板：
  - [smartclock.conf](/E:/programdata/Seafile/AI/clock/ops/nginx/smartclock.conf)
  - [smartclock-server.service](/E:/programdata/Seafile/AI/clock/ops/systemd/smartclock-server.service)

### Verification
- `.\gradlew.bat :server:test` passed
- `.\gradlew.bat assembleDebug testDebugUnitTest` passed
- `.\gradlew.bat :server:test :app:assembleDebug :app:testDebugUnitTest` passed
