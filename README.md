# SmartClock

SmartClock 是一个面向中文生活场景的智能提醒项目，当前主线是：

- Android 客户端：本地优先的提醒应用
- PHP 后端：登录后的账号、同步、日志与后台管理
- 管理台前端：用户、提醒、日志、审计的 Web 管理界面

项目已经从早期的 Android 端 `JDBC 直连 MySQL` 方案迁移到 `HTTP API + PHP 后端`，并保留游客模式。

## 当前能力

Android 客户端当前已实现：

- 一次性提醒、每周/每月/每年提醒
- 农历纪念日
- 倒计时运行态
- 全屏提醒、稍后提醒、关闭提醒
- 本地模式与登录后同步
- 提醒日志、本地数据库迁移与备份
- 桌面组件基础能力

后端当前已实现：

- 用户注册、登录、刷新 token、退出登录
- `bootstrap / push / pull` 同步接口
- 提醒日志批量上传
- 管理员后台登录
- 用户、提醒、日志、审计查询与管理

## 仓库结构

```text
app/            Android 客户端（Kotlin + Compose + Room + Hilt）
server-php/     当前启用的 PHP 后端
admin-web/      后台管理台前端（Vue 3 + Vite + Element Plus）
ops/            部署脚本与 nginx/systemd 模板
server/         旧的 Spring Boot 后端方案，现为历史参考
pic/            设计参考图与页面素材（已移除测试截图）
```

## 技术栈

### Android

- Kotlin
- Jetpack Compose
- Room
- Hilt
- WorkManager
- DataStore
- OkHttp + Gson

### 后端

- PHP 8.2+
- PDO + MySQL
- 自定义 JWT / refresh token 逻辑

### 管理台

- Vue 3
- Vite
- Element Plus

## 推荐主线

当前默认使用下面这条链路：

1. Android 客户端 `app/`
2. PHP 后端 `server-php/`
3. 管理台 `admin-web/`
4. 宝塔 / Nginx / MySQL 部署

`server/` 目录中的 Spring Boot 版本不是当前部署主线，只保留作历史方案和接口参考。

## 本地开发

### 1. Android 客户端

复制示例配置：

```text
local.defaults.properties.example -> local.defaults.properties
```

至少填写：

```properties
API_BASE_URL=https://your-domain.example.com
```

构建命令：

```bash
./gradlew assembleDebug testDebugUnitTest
```

Windows：

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
```

### 2. PHP 后端

复制配置：

```text
server-php/.env.example -> server-php/.env
```

初始化数据库：

- 创建 MySQL 数据库
- 导入 [server-php/database/schema.sql](server-php/database/schema.sql)

本地运行可按你自己的 PHP 环境处理，线上推荐走宝塔 + Nginx。

### 3. 管理台

安装并构建：

```bash
cd admin-web
npm install
npm run build
```

## 部署

当前线上推荐部署方式：

- 服务器系统：CentOS
- 面板：宝塔
- 站点类型：PHP 站点
- Web 服务：Nginx
- 数据库：MySQL

部署说明见：

- [宝塔CentOS后端部署说明.md](宝塔CentOS后端部署说明.md)
- [ops/nginx/smartclock.conf](ops/nginx/smartclock.conf)

## 测试与文档

仓库内保留了项目执行与测试文档：

- [测试计划.md](测试计划.md)
- [task_plan.md](task_plan.md)
- [progress.md](progress.md)
- [findings.md](findings.md)
- [错误分析.md](错误分析.md)

其中：

- `测试计划.md`：当前测试项与结果
- `错误分析.md`：已踩过的问题、根因和处理方式

## 安全说明

仓库中不应提交以下文件：

- `local.properties`
- `local.defaults.properties`
- `.env`
- keystore / key 文件
- 本地数据库
- 测试截图、临时导出 UI 文件

这些内容已经通过 `.gitignore` 进行约束，但在提交前仍建议执行：

```bash
git status
```

确认没有把本地敏感文件带入提交。

## 当前状态

项目目前已经完成：

- Android 客户端主功能可用
- 远程同步链路已接 PHP 后端
- 后台管理台已可登录与管理
- GitHub 仓库已初始化并可正常推送

后续工作重点：

- 继续完成测试计划中的剩余手工项
- 收口 UI 与模板体验
- 视需要决定是否清洗已公开的 Git 历史
