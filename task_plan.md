# SmartClock 任务计划

## 目标
- 维护移动端提醒功能的可用性。
- 建立可部署的远程后端与管理台，替换 Android 端 JDBC 直连 MySQL。
- 保持“本地优先、登录后同步”的业务模式不变。

## 当前阶段
- [complete] Phase 1: 完成现有 Android 工程的提醒、全屏提醒、UI 和测试修复。
- [complete] Phase 2: 建立根目录测试计划与验证流程。
- [complete] Phase 3: 搭建 `server` 模块，落 Flyway、JWT、同步 API、管理 API。
- [complete] Phase 4: 搭建 `admin-web` 管理台并完成生产构建验证。
- [complete] Phase 5: Android 远程层从 JDBC 迁移到 HTTP，同步接入 `clientUuid / logHash / accessToken / refreshToken / deviceId`。
- [complete] Phase 6: 补齐 `ops` 单机云服务器部署脚本与 nginx/systemd 模板。
- [complete] Phase 7: 运行组合验证：`:server:test`、`:app:assembleDebug`、`:app:testDebugUnitTest`。

## 约束
- 新错误先查 [错误分析.md](/E:/programdata/Seafile/AI/clock/错误分析.md)。
- Android Gradle 任务串行执行，不并行写同一 `app/build`。
- 涉及复杂文件且上下文乱码时，优先整文件重写。
- 游客模式不得被云同步误上传。

## 下一步
- 对接真实云服务器和 MySQL。
- 在真机上做“登录后云同步”的端到端联调。
- 补更多后端接口测试和管理台交互细化。
