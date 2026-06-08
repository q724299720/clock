# 智能闹钟 App 策划方案

> 项目代号：SmartClock
> 目标设备：HUAWEI Mate 50
> 目标系统：HarmonyOS 4.2（兼容 Android，非纯血鸿蒙 NEXT）
> 文档版本：v2.0
> 编写日期：2026-06-05

---

## 一、项目概述

### 1.1 项目背景
针对 HUAWEI Mate 50（HarmonyOS 4.2，基于 AOSP 兼容层，可运行 Android 应用）开发一款功能完善、体验流畅的智能闹钟 App，覆盖日常闹钟、倒计时、纪念日提醒等多场景时间管理需求，并通过云端账号体系实现多设备数据同步。

### 1.2 产品定位
一款"全场景、强提醒、可同步"的个人时间管理工具，强调：
- **不漏提醒**：前台服务常驻 + 全屏悬浮窗强提醒
- **场景全面**：单次 / 倒计时 / 周重复 / 月重复 / 纪念日
- **数据可靠**：账号体系 + MySQL 持久化（App 直连）

### 1.3 目标用户
- 上班族、学生：日常起床、会议、上课提醒
- 健身/学习人群：倒计时（番茄钟、运动间歇）
- 重视纪念日人群：生日、结婚纪念日、节日提醒
- 多设备用户：希望闹钟数据云端同步

---

## 二、运行环境与技术约束

| 项 | 说明 |
|----|------|
| 目标机型 | HUAWEI Mate 50（屏幕 6.7" / 2700×1224 / 90Hz） |
| 系统版本 | HarmonyOS 4.2（基于 Android 12 兼容内核，API Level 31） |
| 兼容 SDK | minSdk = 26（Android 8.0），targetSdk = 31 |
| 开发 IDE | Android Studio Hedgehog / DevEco Studio 4.x（兼容模式） |
| 开发语言 | Kotlin（主） + Java（必要部分） |
| UI 框架 | Jetpack Compose + 部分 XML（兼容需要时） |
| 架构 | MVVM + Repository + Hilt 依赖注入 |
| 本地存储 | Room 数据库 + DataStore（轻量配置） |
| 云端数据库 | **MySQL 8.0（App 直连）** |
| 网络 | OkHttp + Retrofit（用于账号鉴权与远程指令） |

> ✅ 由于运行环境兼容 Android，App **可以通过 JDBC 直连 MySQL**，无需中转服务。但需注意：在公网环境下直连 DB 存在安全风险，**生产环境建议同时部署一个轻量鉴权网关**或使用 **SSH 隧道 / SSL 加密连接**。

---

## 三、功能需求清单

### 3.1 核心功能矩阵

| 编号 | 功能模块 | 优先级 | 描述 |
|------|----------|--------|------|
| F1 | 单次时间提醒 | P0 | 指定具体日期+时间，触发一次后自动失效 |
| F2 | 倒计时提醒 | P0 | 设定时长（时:分:秒），支持暂停/继续/重置 |
| F3 | 周重复闹钟 | P0 | 可勾选周一至周日任意组合 |
| F4 | 月重复闹钟 | P0 | 可勾选每月 1-31 号任意组合（自动跳过不存在日期） |
| F5 | 纪念日提醒 | P0 | 按公历/农历每年同一天提醒，支持"已过 X 周年"展示 |
| F6 | 自定义标题与备注 | P0 | 每个闹钟可设标题、备注、图标、颜色标签 |
| F7 | 账号管理系统 | P0 | 注册 / 登录 / 找回密码 / 自动登录 |
| F8 | 后台常驻 | P0 | 前台服务（Foreground Service）保证闹钟必达 |
| F9 | 悬浮窗提醒 | P0 | 闹钟到时全屏 Activity / 系统悬浮窗弹出 |
| F10 | 数据云端同步 | P0 | 增删改实时同步到 MySQL，多端一致 |
| F11 | 铃声与振动 | P1 | 内置铃声 + 自定义本地音频，振动模式可选 |
| F12 | 渐强音量 / 防睡过头 | P1 | 渐强音量、需做题/扫码关闭 |
| F13 | 节假日跳过 | P1 | 周重复闹钟可设法定节假日自动跳过 |
| F14 | 标签分组 | P2 | 工作 / 生活 / 学习 标签管理 |
| F15 | 统计报表 | P2 | 闹钟触发记录、按时起床率 |

---

## 四、详细功能设计

### 4.1 单次时间提醒（F1）
- **输入**：年-月-日 时:分，标题，备注，铃声，振动开关
- **校验**：触发时间必须晚于当前时间
- **行为**：触发后状态置为"已完成"，列表中归档；用户可手动复制为新闹钟
- **实现要点**：使用 `AlarmManager.setExactAndAllowWhileIdle()` 注册精准闹钟，确保 Doze 模式下也能触发

### 4.2 倒计时提醒（F2）
- **输入**：时(0-99) : 分(0-59) : 秒(0-59)，可保存为"快捷倒计时"
- **运行态**：前台显示大字号剩余时间，支持暂停/继续/+1分钟/重置
- **后台**：进入后台后由 ForegroundService + 通知栏常驻
- **结束**：响铃 + 振动 + 全屏 Activity，文案"倒计时结束"
- **实现**：基于绝对结束时间戳，避免后台被系统暂停导致计时漂移

### 4.3 周重复闹钟（F3）
- **输入**：时:分，星期勾选（多选），生效起止日期（可选）
- **示例**：每周一/三/五 07:30，2026-09-01 ~ 2027-01-15
- **算法**：触发后立即计算下一个最近的周几时刻，调用 AlarmManager 重新注册
- **存储**：`repeat_weekdays` 用 7-bit 位运算（bit0=周日…bit6=周六）

### 4.4 月重复闹钟（F4）
- **输入**：时:分，月份内日期勾选（1-31 多选）
- **边界**：选择 31 号时，2/4/6/9/11 月自动跳过
- **可选项**：是否"月末自动落到最后一天"（如选 1/31 → 2 月触发 2/28 或 2/29）
- **存储**：`repeat_month_days` 用 32-bit 位运算（bit1=每月1号…bit31=每月31号）

### 4.5 纪念日提醒（F5）
- **输入**：日期类型（公历/农历），首发日期，标题（如"结婚纪念日"）
- **展示**：列表中显示"距今 X 天 / 已过 N 周年"
- **提前提醒**：可设置提前 1/3/7/30 天预提醒
- **农历换算**：引入 `cn.6tail:lunar` Java 库，离线计算

### 4.6 账号管理（F7）

#### 4.6.1 账号体系
| 项 | 方案 |
|----|------|
| 注册方式 | 手机号 + 短信验证码 / 邮箱 + 密码 |
| 鉴权 | App 端本地校验 + MySQL 用户表比对（bcrypt） |
| 密码 | 前端 SHA-256 预哈希 → 入库 bcrypt(cost=12) |
| 会话保持 | DataStore 加密存储 `user_id` + `token`（启动自动登录） |
| 找回密码 | 短信/邮箱验证码（短信网关可选阿里云/腾讯云） |
| 注销账号 | 软删除，30 天后清除数据 |

#### 4.6.2 安全注意
- **MySQL 账号权限**：App 使用的数据库账号仅授予 `users / alarms / alarm_logs` 表的 `SELECT/INSERT/UPDATE/DELETE`，禁止 DDL、跨库
- **连接加密**：MySQL 强制开启 SSL（`require_secure_transport = ON`），App 端使用 `useSSL=true&verifyServerCertificate=true`
- **凭证存储**：DB 连接串通过 BuildConfig 注入，使用 `Android Keystore` 加密本地缓存
- **防注入**：所有 SQL 走 PreparedStatement（Room/JDBC 参数化查询）

### 4.7 后台常驻（F8）

Android/HarmonyOS 兼容模式下的后台保活组合拳：

| 方案 | 用途 | 关键 API |
|------|------|----------|
| **AlarmManager** | 注册闹钟到时唤醒 | `setExactAndAllowWhileIdle` / `setAlarmClock` |
| **Foreground Service** | 倒计时运行中常驻 | `startForeground()` + 持续通知 |
| **WorkManager** | 数据周期同步、补偿调度 | `PeriodicWorkRequest` |
| **BootReceiver** | 开机自启，重新注册所有闹钟 | `RECEIVE_BOOT_COMPLETED` |

**关键策略**：
1. 用 `setAlarmClock()` 注册的闹钟享受最高优先级，可穿透 Doze 模式
2. App 启动时从 Room 读取所有启用的闹钟，重新 schedule 到 AlarmManager
3. 接收 `BOOT_COMPLETED` 广播，重启后自动恢复所有闹钟
4. 引导用户关闭"省电策略"、加入"电池白名单"（华为 Mate 50 特别重要）

### 4.8 悬浮窗提醒（F9）

**两套方案并行**：

| 方案 | 触发条件 | 实现 |
|------|----------|------|
| **全屏 Intent Activity** | 屏幕锁定 / App 在后台 | `Notification.Builder.setFullScreenIntent(pendingIntent, true)` |
| **系统悬浮窗** | App 在前台 / 已授权 SYSTEM_ALERT_WINDOW | `WindowManager.addView(view, layoutParams)` |

- **权限**：`SYSTEM_ALERT_WINDOW`（用户需手动到设置开启）+ `USE_FULL_SCREEN_INTENT`（Android 14+ 默认开启）
- **样式**：全屏深色遮罩 + 大字标题 + 时间 + "关闭"/"稍后 5/10/30 分钟" 按钮
- **唤醒屏幕**：`PowerManager.WakeLock`（FULL_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP）+ `KeyguardManager` 在锁屏上显示
- **降级**：若用户拒绝悬浮窗权限，降级为高优先级通知 + 强振铃声

### 4.9 数据云端同步（F10）

**架构**：本地 Room 作为离线缓存，MySQL 作为权威数据源。

```
用户操作 → ViewModel
            ↓
       Room（本地立即写）   ←→   同步队列（SyncWorker）
            ↓                       ↓
        UI 立即刷新               JDBC 写入 MySQL
                                    ↓
                                成功 → 标记 synced=1
                                失败 → 重试（指数退避）
```

- **离线可用**：所有写操作先写 Room，UI 立刻响应
- **同步策略**：WorkManager 周期任务（每 5 分钟）+ 应用启动时 + 实时变更触发
- **冲突解决**：基于 `updated_at` 时间戳 LWW（Last-Write-Wins）
- **多端拉取**：启动时 `SELECT * FROM alarms WHERE user_id=? AND updated_at > ?` 增量拉取

---

## 五、系统架构

### 5.1 总体架构（无中间后端）

```
┌─────────────────────────────────────────────────┐
│           HUAWEI Mate 50 (HarmonyOS 4.2)        │
│  ┌────────────────────────────────────────────┐ │
│  │ UI 层（Jetpack Compose）                   │ │
│  │  Login / List / Edit / Countdown / Alarm   │ │
│  └────────────────┬───────────────────────────┘ │
│                   │                              │
│  ┌────────────────▼───────────────────────────┐ │
│  │ ViewModel（MVVM + StateFlow）              │ │
│  └────────────────┬───────────────────────────┘ │
│                   │                              │
│  ┌────────────────▼───────────────────────────┐ │
│  │ Repository                                 │ │
│  └──────┬──────────────────────────┬──────────┘ │
│         │                          │            │
│  ┌──────▼──────┐           ┌──────▼──────┐     │
│  │  Room (本地) │           │ MySQL Client│     │
│  │  SQLite     │           │ (JDBC/SSL)  │     │
│  └─────────────┘           └──────┬──────┘     │
│                                   │            │
│  ┌────────────────────────────────┼─────────┐  │
│  │ 系统服务                        │          │  │
│  │  AlarmManager / ForegroundSvc  │          │  │
│  │  WorkManager / BootReceiver    │          │  │
│  └────────────────────────────────┘          │  │
└───────────────────────────────────┼──────────┘
                                    │ SSL/TLS
                                    │ TCP 3306
                          ┌─────────▼─────────┐
                          │   MySQL 8.0       │
                          │ (云服务器/自建)    │
                          └───────────────────┘
```

### 5.2 模块划分

```
app/
├── data/
│   ├── local/          # Room：Entity、DAO、Database
│   ├── remote/         # MySQL JDBC 封装、SQL 模板
│   ├── repository/     # AlarmRepository、UserRepository
│   └── sync/           # SyncWorker、ConflictResolver
├── domain/
│   ├── model/          # 业务模型
│   └── usecase/        # 业务用例（AddAlarm、TriggerAlarm…）
├── ui/
│   ├── auth/           # 登录注册
│   ├── alarm/          # 闹钟列表/编辑
│   ├── countdown/      # 倒计时
│   ├── anniversary/    # 纪念日
│   └── settings/       # 设置
├── service/
│   ├── AlarmReceiver       # BroadcastReceiver
│   ├── AlarmService        # 前台服务
│   ├── BootReceiver        # 开机自启
│   └── FullScreenActivity  # 全屏闹钟页
├── util/
│   ├── LunarUtil       # 农历计算
│   ├── CryptoUtil      # 加解密
│   └── ScheduleUtil    # 下次触发时间计算
└── di/                 # Hilt 模块
```

### 5.3 JDBC 直连关键代码示意

```kotlin
// MySQLDataSource.kt
object MySQLDataSource {
    private val config = HikariConfig().apply {
        jdbcUrl = "jdbc:mysql://${BuildConfig.DB_HOST}:3306/smartclock" +
                  "?useSSL=true&requireSSL=true&verifyServerCertificate=true" +
                  "&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8"
        username = BuildConfig.DB_USER
        password = CryptoUtil.decrypt(BuildConfig.DB_PWD_ENC)
        maximumPoolSize = 3      // 移动端连接池小一些
        minimumIdle = 1
        connectionTimeout = 5_000
        idleTimeout = 60_000
    }
    val dataSource: DataSource by lazy { HikariDataSource(config) }
}
```

> 移动端使用 HikariCP 时需注意：包体积较大（~150KB），可改用更轻量的 `BoneCP` 或直接 `DriverManager`（无连接池，按需创建）。

---

## 六、数据库设计（MySQL）

### 6.1 用户表 `users`

```sql
CREATE TABLE users (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone           VARCHAR(20)  UNIQUE,
    email           VARCHAR(128) UNIQUE,
    password_hash   VARCHAR(128) NOT NULL,
    nickname        VARCHAR(64),
    avatar_url      VARCHAR(256),
    status          TINYINT DEFAULT 0 COMMENT '0=正常 1=禁用 2=注销中',
    last_login_at   DATETIME,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_phone (phone),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 6.2 闹钟表 `alarms`

```sql
CREATE TABLE alarms (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id               BIGINT NOT NULL,
    type                  TINYINT NOT NULL COMMENT '1=单次 2=倒计时 3=周重复 4=月重复 5=纪念日',
    title                 VARCHAR(128) NOT NULL,
    note                  VARCHAR(512),
    trigger_time          DATETIME COMMENT '单次/纪念日首次触发时间',
    duration_sec          INT COMMENT '倒计时时长（秒）',
    repeat_weekdays       TINYINT COMMENT '位运算 bit0=周日…bit6=周六',
    repeat_month_days     INT COMMENT '位运算 bit1=1号…bit31=31号',
    anniversary_calendar  TINYINT COMMENT '0=公历 1=农历',
    advance_notify_days   VARCHAR(32) COMMENT '提前提醒天数，如 "1,3,7"',
    ringtone              VARCHAR(256),
    vibrate               TINYINT DEFAULT 1,
    volume_fade           TINYINT DEFAULT 0,
    snooze_minutes        TINYINT DEFAULT 5,
    label                 VARCHAR(32),
    color                 VARCHAR(16),
    enabled               TINYINT DEFAULT 1,
    start_date            DATE,
    end_date              DATE,
    status                TINYINT DEFAULT 0 COMMENT '0=正常 1=已删除',
    created_at            DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_enabled (user_id, enabled, status),
    INDEX idx_updated (user_id, updated_at),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 6.3 触发记录表 `alarm_logs`

```sql
CREATE TABLE alarm_logs (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    alarm_id    BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    fired_at    DATETIME NOT NULL,
    action      TINYINT COMMENT '1=关闭 2=稍后 3=未操作',
    device_id   VARCHAR(64),
    INDEX idx_user_fired (user_id, fired_at),
    INDEX idx_alarm (alarm_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 6.4 短信验证码表 `sms_codes`（可选）

```sql
CREATE TABLE sms_codes (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone       VARCHAR(20) NOT NULL,
    code        VARCHAR(8)  NOT NULL,
    purpose     TINYINT COMMENT '1=注册 2=登录 3=找回',
    expires_at  DATETIME NOT NULL,
    used        TINYINT DEFAULT 0,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_phone_purpose (phone, purpose)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 七、本地数据库（Room）

与 MySQL 表结构对齐，新增同步状态字段：

```kotlin
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey val id: Long,           // 与 MySQL 一致，本地新建用负数临时占位
    val userId: Long,
    val type: Int,
    val title: String,
    val note: String?,
    val triggerTime: Long?,             // epoch millis
    val durationSec: Int?,
    val repeatWeekdays: Int?,
    val repeatMonthDays: Int?,
    val anniversaryCalendar: Int?,
    val advanceNotifyDays: String?,
    val ringtone: String?,
    val vibrate: Int,
    val volumeFade: Int,
    val snoozeMinutes: Int,
    val label: String?,
    val color: String?,
    val enabled: Int,
    val startDate: Long?,
    val endDate: Long?,
    val status: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: Int = 0             // 0=已同步 1=待上传 2=待删除
)
```

---

## 八、权限清单（AndroidManifest.xml）

| 权限 | 用途 | 说明 |
|------|------|------|
| `INTERNET` | MySQL 网络连接 | 普通权限 |
| `ACCESS_NETWORK_STATE` | 检查网络可用 | 普通权限 |
| `SCHEDULE_EXACT_ALARM` | 精确闹钟（API 31+） | 用户需在设置中允许 |
| `USE_EXACT_ALARM` | 精确闹钟（API 33+ 替代） | 安装即授予 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启重注册闹钟 | 普通权限 |
| `FOREGROUND_SERVICE` | 倒计时前台服务 | 普通权限 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | 倒计时特殊用途 | API 34+ |
| `SYSTEM_ALERT_WINDOW` | 悬浮窗 | 用户需手动授权 |
| `USE_FULL_SCREEN_INTENT` | 全屏闹钟页 | API 29+ |
| `WAKE_LOCK` | 唤醒屏幕 | 普通权限 |
| `VIBRATE` | 振动 | 普通权限 |
| `POST_NOTIFICATIONS` | 通知（API 33+） | 用户需授权 |
| `READ_MEDIA_AUDIO` | 自定义铃声 | API 33+ |

### 8.1 华为 Mate 50 特别处理
HarmonyOS 4.2 的省电策略比原生 Android 更激进，需引导用户：
1. **应用启动管理**：手动管理 → 允许自启动、关联启动、后台活动
2. **电池优化**：将 App 加入白名单（忽略电池优化）
3. **悬浮窗权限**：设置 → 应用 → SmartClock → 显示在其他应用上层
4. **锁屏通知**：允许锁屏显示通知 + 横幅显示

App 首次启动时通过引导页 + 一键跳转设置帮助用户完成。

---

## 九、UI / UX 设计要点

### 9.1 主要页面
1. **启动页**：1.5s 品牌动画 + 自动登录检测
2. **登录/注册页**：手机号+短信 或 邮箱+密码
3. **首页（闹钟列表）**：顶部 Tab "闹钟 / 倒计时 / 纪念日"，FAB 新建
4. **编辑页**：分类型动态表单（单次/周/月/纪念日字段差异化）
5. **倒计时页**：大数字 + 进度环 + 操作条
6. **全屏提醒页**：深色背景 + 大标题 + 时间 + 关闭/稍后按钮
7. **我的**：账号信息、铃声管理、标签、节假日、权限自检、关于

### 9.2 设计规范
- 适配 Mate 50（2700×1224，曲面屏左右 16dp 安全边距）
- Material 3 + 华为视觉风格融合：主色 #007DFF，圆角 16dp
- 支持系统深色模式、动态字体
- 关键操作（删除、关闭闹钟）需二次确认或 Snackbar 撤销

### 9.3 关键交互
- 列表项左滑：快速启用/禁用，右滑：删除
- 时间选择：Material TimePicker
- 重复设置：自定义 WeekChip / MonthDateGrid 组件
- 全屏提醒页：长按"稍后"展开 5/10/30 分钟选项

---

## 十、非功能性需求

| 项 | 指标 |
|----|------|
| 启动时间 | 冷启动 ≤ 1.5s |
| 闹钟准点率 | ≥ 99.5%（AlarmManager.setAlarmClock） |
| 同步延迟 | 在线时 ≤ 2s |
| 续航影响 | 日均耗电 ≤ 1.5%（含 MySQL 心跳） |
| 离线可用 | 不依赖网络即可设置/触发本地闹钟 |
| 数据安全 | SSL 强制 + bcrypt + 最小权限 DB 账号 |
| 包体积 | APK ≤ 25 MB（含 MySQL Connector ~2MB） |
| 兼容性 | HarmonyOS 4.0+ / Android 8.0+ |

---

## 十一、关键技术难点与方案

| 难点 | 方案 |
|------|------|
| App 被强杀后闹钟仍能响 | `setAlarmClock()` 注册的闹钟由系统管理，不依赖进程 |
| Mate 50 厂商省电策略激进 | 引导加入电池白名单 + 自启动 + 关联启动；启动时检测并提示 |
| 倒计时息屏后停摆 | ForegroundService + 基于绝对时间戳计算，息屏不影响 |
| 悬浮窗权限被拒 | 降级为 setFullScreenIntent 全屏 Activity（仍可强提醒） |
| 直连 MySQL 安全风险 | 强制 SSL、最小权限账号、密码加密、IP 白名单（可选） |
| 移动网络波动断连 | HikariCP 自动重连 + 同步失败本地排队重试 |
| 农历计算 | `cn.6tail:lunar:1.6.x` Java 库，离线 |
| 多设备数据冲突 | LWW + `updated_at`，删除用软删 + tombstone |
| MySQL Connector 包体积 | 使用 `mysql-connector-j` 8.x slim 版，或裁剪未用类 |

---

## 十二、安全方案

### 12.1 数据库直连风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| DB 凭证被逆向 | 凭证加密后写入 BuildConfig；运行时用 Android Keystore 解密 |
| 中间人攻击 | 强制 SSL/TLS，校验服务端证书 |
| SQL 注入 | 所有 SQL 使用 PreparedStatement 参数化查询 |
| 越权访问他人数据 | App 端账号仅有数据表权限，所有查询带 `user_id=?` 条件 |
| DDoS / 滥用连接 | MySQL 端配置 `max_user_connections`；服务器侧 fail2ban |
| 公网 DB 暴露 | 建议仅开放 3306 给特定区域 IP；高安全场景改用 VPN |

### 12.2 推荐生产部署
- **MySQL** 部署在云服务器（阿里云 RDS / 华为云 RDS），开启 SSL
- **App 账号**：业务专用账号，`GRANT SELECT, INSERT, UPDATE, DELETE ON smartclock.* TO 'app'@'%' REQUIRE SSL;`
- **备份**：每日自动备份，保留 30 天
- **监控**：慢查询日志、连接数告警

---

## 十三、开发计划（里程碑）

| 阶段 | 周期 | 产出 |
|------|------|------|
| M1 需求与原型 | 第 1-2 周 | PRD、Figma 原型、UI 视觉稿 |
| M2 数据库与环境 | 第 3 周 | MySQL 表、云服务器、SSL 配置、测试数据 |
| M3 App 框架 | 第 3-4 周 | 工程脚手架、Hilt、Room、MySQL JDBC 封装 |
| M4 账号系统 | 第 4-5 周 | 注册、登录、自动登录、找回密码 |
| M5 核心闹钟 | 第 5-8 周 | 单次/周/月/倒计时/纪念日 + AlarmManager 集成 |
| M6 后台与提醒 | 第 8-9 周 | ForegroundService、BootReceiver、悬浮窗、全屏页 |
| M7 同步与离线 | 第 9-10 周 | Room 缓存、SyncWorker、冲突解决 |
| M8 测试与优化 | 第 10-11 周 | 兼容性、稳定性、电量、Mate 50 实测 |
| M9 上架 | 第 12 周 | 华为应用市场提审、灰度发布 |

---

## 十四、测试计划

1. **功能测试**：每类闹钟 ×（启用/禁用/编辑/删除/触发/稍后）
2. **场景测试**：
   - 关机重启后闹钟是否仍响
   - App 被任务管理器清理后是否响
   - 飞行模式下本地闹钟是否响
   - 时区/时间被修改后表现
   - 网络断开后能否正常使用并补同步
3. **安全测试**：SQL 注入、连接泄漏、越权查询
4. **极限测试**：单用户 500 条闹钟、连续 30 天后台运行
5. **兼容测试**：Mate 50 / Mate 50 Pro / Mate 50 RS、不同 HarmonyOS 小版本
6. **网络测试**：弱网、丢包、DNS 异常下的连接重试

---

## 十五、风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| 华为后续 OTA 收紧后台限制 | 闹钟不准 | 及时跟进 API，引导用户配置 |
| MySQL 连接被运营商封锁 3306 | 同步失败 | 改用非标准端口 + SSL 隧道 |
| DB 凭证被破解 | 数据泄漏 | 凭证加密 + Keystore + 定期轮换 |
| 移动端 JDBC 兼容问题 | 闪退 | 使用 mysql-connector-j 8.x，避开 Java SE only API |
| 用户跨多设备数据混乱 | 体验差 | 严格 LWW + 同步日志可视化 |

---

## 十六、后续迭代方向

- v1.1：智能闹钟（根据睡眠数据唤醒）
- v1.2：日历联动，会议自动建闹钟
- v1.3：桌面小组件、HarmonyOS 服务卡片、手表端
- v1.4：家庭共享闹钟（家人互相提醒）
- v1.5：AI 助手生成闹钟（自然语言："明天 7 点叫我"）
- v2.0：可选切换"直连模式 / 网关模式"，方便规模化运营时引入后端

---

## 附录 A：技术选型清单

| 类别 | 选型 |
|------|------|
| 语言 | Kotlin 1.9 + Java 11 |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Repository + Hilt |
| 异步 | Kotlin Coroutines + Flow |
| 本地存储 | Room 2.6 + DataStore 1.1 |
| MySQL Driver | mysql-connector-j 8.4.x |
| 连接池 | HikariCP 5.x（或 DriverManager 直连） |
| 网络（短信网关） | OkHttp 4.x + Retrofit 2.x |
| 加密 | Android Keystore + Tink |
| 农历库 | cn.6tail:lunar:1.6.x |
| 后台调度 | WorkManager 2.9 |
| 依赖注入 | Hilt 2.50 |
| 日志 | Timber + LogCat 文件落盘 |

## 附录 B：术语表

- **AlarmManager.setAlarmClock**：Android 系统级闹钟 API，享受最高优先级，可穿透 Doze
- **Foreground Service**：前台服务，持有通知栏图标，系统不易回收
- **Doze 模式**：Android 6.0+ 的待机省电模式，会暂停大部分后台任务
- **LWW**：Last-Write-Wins，以最后写入时间为准的冲突解决策略
- **HarmonyOS 4.2**：华为基于 AOSP 兼容层的版本，可运行 Android APK，区别于纯血鸿蒙 NEXT

---

**文档结束**
