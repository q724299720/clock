# 智能闹钟 App 开发技术文档

> 项目代号：SmartClock
> 配套文档：[闹钟App策划方案.md](闹钟App策划方案.md)
> 目标设备：HUAWEI Mate 50 / HarmonyOS 4.2（兼容 Android，minSdk 26 / targetSdk 31）
> 技术栈：Kotlin + Jetpack Compose + MVVM + Room + MySQL(JDBC 直连)
> 构建方式：**GitHub 托管 + GitHub Actions 云端编译，产出签名 Release APK**
> 文档版本：v1.1
> 编写日期：2026-06-05

---

## 阅读说明

本文档是**开发执行手册**，把策划方案拆解为可落地的开发步骤。整体顺序：

> **Step 0 环境准备 → Step 1 UI 设计（先行）→ Step 2 工程脚手架 → Step 3 数据层 → Step 4 账号系统 → Step 5 核心闹钟 → Step 6 后台与提醒 → Step 7 同步 → Step 8 设置与扩展 → Step 9 测试 → Step 10 GitHub Actions 云端编译与发布**

每个 Step 包含：目标、任务清单、关键产物、验收标准。可作为任务看板逐项推进。

### 构建方式说明（重要）
本项目代码托管在 **GitHub**，编译在 **GitHub Actions 云端**完成，无需依赖本地固定机器：
- 推送代码或打 tag 后，Actions 自动用 Gradle 构建并产出**签名 Release APK**
- 敏感信息（MySQL 凭证、签名 keystore）全部存入 **GitHub Secrets**，编译时注入，**绝不入库**
- 本地开发用占位配置文件（不提交），保证仓库干净
- 相关配置贯穿 Step 0（仓库初始化）、Step 2（gradle 读 Secrets）、Step 10（CI 工作流）

---

## Step 0：开发环境与 GitHub 仓库准备

### 目标
搭好本地可调试 + GitHub 可云端编译的双环境。

### 0.1 本地环境
- [ ] 安装 Android Studio Hedgehog+（或 DevEco Studio 兼容模式）
- [ ] 配置 JDK 17、Gradle 8.x、Android Gradle Plugin 8.x
- [ ] HUAWEI Mate 50 开启「开发者模式 + USB 调试」，连接真机
- [ ] 准备 MySQL 8.0 测试实例（本地 Docker 或云 RDS），开启 SSL
- [ ] 建库 `smartclock`，导入策划方案第六章的 4 张表 DDL
- [ ] 创建业务专用 MySQL 账号（仅 4 表 DML 权限）

### 0.2 GitHub 仓库初始化
- [ ] 在 GitHub 新建私有仓库 `smartclock`（私有，避免凭证策略外泄）
- [ ] `git init` 并推送骨架
- [ ] 编写 `.gitignore`（见下，确保密钥/产物不入库）
- [ ] 提交占位配置模板 `local.defaults.properties.example`
- [ ] 规划 GitHub Secrets（见 0.4），由仓库管理员在 Settings → Secrets 配置

### 0.3 `.gitignore` 关键条目
```gitignore
# 构建产物
/build/
/app/build/
*.apk
*.aab

# 本地配置与密钥（绝不入库）
local.properties
local.defaults.properties
keystore.jks
*.keystore
key.properties

# IDE
.idea/
.gradle/
*.iml
```

### 0.4 GitHub Secrets 规划（编译时注入）
| Secret 名 | 用途 |
|-----------|------|
| `DB_HOST` | MySQL 地址 |
| `DB_USER` | 业务库账号 |
| `DB_PWD_ENC` | 加密后的库密码 |
| `SIGNING_KEYSTORE_BASE64` | keystore.jks 的 Base64 编码 |
| `SIGNING_STORE_PASSWORD` | keystore 密码 |
| `SIGNING_KEY_ALIAS` | 签名别名 |
| `SIGNING_KEY_PASSWORD` | 别名密码 |

> 生成 keystore：`keytool -genkeypair -v -keystore keystore.jks -alias smartclock -keyalg RSA -keysize 2048 -validity 36500`
> 转 Base64 存 Secret：`base64 -w0 keystore.jks`（Windows 用 `certutil -encode`）

### 关键产物
- 可运行的「Hello World」APK 部署到 Mate 50
- GitHub 私有仓库 + `.gitignore` + 配置模板就绪
- 7 个 Secrets 配置完成
- MySQL 测试库可用客户端 SSL 连接

### 验收标准
真机能跑空白 App；MySQL 客户端可 SSL 连接；`git status` 不含任何密钥文件；Secrets 已就位。

---

## Step 1：UI 设计（第一步 / 先行）

> **本步骤为开发第一步**。先确定全部界面与交互，产出高保真原型 + Compose 组件清单，后续编码直接对照实现。

### 1.1 设计目标
- 适配 Mate 50（2700×1224，曲面屏左右留 16dp 安全边距）
- Material 3 + 华为视觉融合，主色 `#007DFF`，圆角 16dp
- 支持深色模式、动态字体

### 1.2 页面清单与设计任务

| 页面 | 路由 | 核心元素 | 设计任务 |
|------|------|----------|----------|
| 启动页 Splash | `/splash` | Logo 动画、自动登录检测 | 1.5s 动画稿 |
| 登录/注册 Auth | `/auth` | 手机号+短信 / 邮箱+密码 Tab | 输入态、错误态、加载态 |
| 闹钟列表 List | `/home` | 顶部 Tab(闹钟/倒计时/纪念日)、FAB、列表卡片 | 空态、列表态、左右滑操作 |
| 闹钟编辑 Edit | `/edit?type=&id=` | 分类型动态表单 | 5 类闹钟各一套表单 |
| 倒计时 Countdown | `/countdown` | 大数字、进度环、操作条 | 待机/运行/暂停/结束 4 态 |
| 纪念日 Anniversary | `/anniversary` | 距今天数、周年数 | 卡片态 |
| 全屏提醒 FullScreen | `Activity` | 大标题、时间、关闭/稍后 | 锁屏 + 解锁两种背景 |
| 设置 Settings | `/settings` | 账号、铃声、标签、权限自检 | 列表项规范 |

### 1.3 可复用组件清单（Compose）

| 组件 | 用途 | 输入参数 |
|------|------|----------|
| `AlarmCard` | 列表项 | alarm, onToggle, onClick, onDelete |
| `WeekChipRow` | 周几多选 | selectedBits: Int, onChange |
| `MonthDateGrid` | 月内日期多选(1-31) | selectedBits: Int, onChange |
| `TimeWheelPicker` | 时:分选择 | hour, minute, onChange |
| `DurationPicker` | 时:分:秒(倒计时) | h, m, s, onChange |
| `LabelChip` | 标签色块 | label, color |
| `PermissionGuideCard` | 权限引导 | permission, granted, onFix |
| `EmptyState` | 空列表占位 | text, icon |

### 1.4 交互规范
- 列表项：左滑启用/禁用，右滑删除（删除后 Snackbar 可撤销）
- 关键破坏操作二次确认
- 时间选择用 Material TimePicker
- 全屏提醒：长按"稍后"展开 5/10/30 分钟

### 1.5 设计系统（Theme）
```
// 在 ui/theme/ 中定义
Color.kt    : Primary=#007DFF, 各标签色, 深浅色两套
Type.kt     : 标题/正文/数字大字 三档字号
Shape.kt    : small=8dp, medium=16dp, large=24dp
Theme.kt    : SmartClockTheme(darkTheme, dynamicColor)
```

### 关键产物
- Figma/Axure 高保真原型（8 个页面 + 各状态）
- `ui/theme/` 主题文件
- 组件库占位实现（先静态，后接数据）

### 验收标准
原型评审通过；主题与 8 个组件在 Compose Preview 中可正常渲染。

---

## Step 2：工程脚手架与架构

### 目标
搭建 MVVM + Repository + Hilt 的工程骨架与目录结构。

### 2.1 目录结构（对照策划方案 5.2）
```
app/src/main/java/com/smartclock/
├── data/   (local / remote / repository / sync)
├── domain/ (model / usecase)
├── ui/     (auth / alarm / countdown / anniversary / settings / theme / component)
├── service/(AlarmReceiver / AlarmService / BootReceiver / FullScreenActivity)
├── util/   (LunarUtil / CryptoUtil / ScheduleUtil)
└── di/
```

### 2.2 任务清单
- [ ] 配置 `build.gradle`：Compose、Hilt、Room、Coroutines、WorkManager、mysql-connector-j、lunar
- [ ] 配置 `BuildConfig`：从**环境变量(CI) 或 占位文件(本地)** 读取 DB_HOST / DB_USER / DB_PWD_ENC
- [ ] 配置签名：从环境变量读取 keystore（CI）或本地 key.properties
- [ ] 搭建 Hilt 模块（`di/`）：DatabaseModule、NetworkModule、RepositoryModule
- [ ] 配置 Navigation（Compose Navigation）与路由表
- [ ] 配置 `AndroidManifest.xml` 权限（对照策划方案第八章）

### 2.2.1 配置注入逻辑（兼容本地与 CI）
`app/build.gradle.kts` 中统一读取，优先环境变量（CI 由 Secrets 注入），回退本地占位文件：
```kotlin
import java.util.Properties

// 本地开发：读 local.defaults.properties（不入库）；CI：读环境变量
val localProps = Properties().apply {
    val f = rootProject.file("local.defaults.properties")
    if (f.exists()) load(f.inputStream())
}
fun cfg(key: String, default: String = ""): String =
    System.getenv(key) ?: localProps.getProperty(key) ?: default

android {
    defaultConfig {
        buildConfigField("String", "DB_HOST", "\"${cfg("DB_HOST")}\"")
        buildConfigField("String", "DB_USER", "\"${cfg("DB_USER")}\"")
        buildConfigField("String", "DB_PWD_ENC", "\"${cfg("DB_PWD_ENC")}\"")
    }
    signingConfigs {
        create("release") {
            // CI 中 keystore 由工作流解码到此路径；本地用自己的 jks
            storeFile = file(System.getenv("SIGNING_STORE_FILE") ?: "../keystore.jks")
            storePassword = cfg("SIGNING_STORE_PASSWORD")
            keyAlias = cfg("SIGNING_KEY_ALIAS")
            keyPassword = cfg("SIGNING_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { buildConfig = true }
}
```

### 2.2.2 本地占位文件 `local.defaults.properties.example`（提交到仓库）
开发者复制为 `local.defaults.properties`（已被 .gitignore 忽略）后填真实值：
```properties
DB_HOST=10.0.0.1
DB_USER=app
DB_PWD_ENC=填加密后的密码
SIGNING_STORE_PASSWORD=
SIGNING_KEY_ALIAS=smartclock
SIGNING_KEY_PASSWORD=
```

### 2.3 关键依赖（build.gradle 摘要）
```kotlin
// Compose
implementation(platform("androidx.compose:compose-bom:2024.06.00"))
implementation("androidx.compose.material3:material3")
// 架构
implementation("com.google.dagger:hilt-android:2.50")
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
implementation("androidx.work:work-runtime-ktx:2.9.0")
implementation("androidx.datastore:datastore-preferences:1.1.1")
// MySQL 直连
implementation("com.mysql:mysql-connector-j:8.4.0")
implementation("com.zaxxer:HikariCP:5.1.0")
// 农历
implementation("cn.6tail:lunar:1.6.13")
```

### 验收标准
项目可编译；Hilt 注入跑通；Navigation 在页面间可跳转（空白页）。

---

## Step 3：数据层（拆解自策划方案 4.9 / 5.3 / 六 / 七）

### 目标
打通本地 Room + 远程 MySQL 双存储，建立 Repository。

### 3.1 任务拆解

| 子任务 | 对应方案 | 产物 |
|--------|----------|------|
| Room 实体 + DAO + Database | 第七章 | `AlarmEntity`、`UserEntity`、`AlarmDao` |
| MySQL JDBC 封装 | 5.3 | `MySQLDataSource`、`AlarmRemoteDataSource` |
| SQL 模板（参数化） | 6.x | 增删改查 PreparedStatement |
| Repository 整合 | 5.1 | `AlarmRepository`、`UserRepository` |
| 凭证加密 | 4.6.2 / 十二章 | `CryptoUtil`(Keystore) |

### 3.2 关键约束
- 所有远程 SQL 必须 `PreparedStatement` 参数化，杜绝注入
- 所有查询强制带 `user_id=?` 条件，防越权
- JDBC 调用必须在 `Dispatchers.IO` 协程中执行
- Room 实体新增 `syncStatus`（0=已同步 1=待上传 2=待删除）

### 3.3 Repository 读写策略
```
写：先写 Room(立即) → 标记 syncStatus=1 → 触发同步
读：优先 Room → 后台拉取 MySQL 增量 → 合并刷新
```

### 验收标准
单元测试：CRUD 能正确落 Room；JDBC 能正确读写 MySQL；断网时本地读写不报错。

---

## Step 4：账号系统（拆解自策划方案 4.6）

### 目标
完成注册 / 登录 / 自动登录 / 找回密码闭环。

### 4.1 任务拆解
- [ ] 注册：手机号+短信 / 邮箱+密码，密码 SHA-256 预哈希 → bcrypt 入库
- [ ] 登录：查 `users` 表比对 bcrypt
- [ ] 自动登录：DataStore 加密存 `user_id`+token，启动检测
- [ ] 找回密码：短信/邮箱验证码（接入网关，写 `sms_codes` 表）
- [ ] 注销：软删除（status=2）

### 4.2 安全要点（方案 4.6.2 / 十二章）
- bcrypt cost=12；密码全程不出明文
- DataStore 用 Keystore 加密会话凭证
- 登录失败错误信息不区分"用户不存在/密码错误"

### 4.3 关键产物
`AuthViewModel`、`UserRepository.login/register`、`AuthScreen`（对接 Step 1 UI）

### 验收标准
能注册新用户写入 MySQL；重启 App 自动登录；错误密码被拒。

---

## Step 5：核心闹钟功能（拆解自策划方案 4.1-4.5）

> 五类闹钟分 5 个子任务，逐个交付。

### 5.1 单次提醒 F1（方案 4.1）
- 表单：日期+时间、标题、备注、铃声、振动
- 校验：触发时间 > 当前
- 注册：`AlarmManager.setExactAndAllowWhileIdle()`
- 触发后状态置「已完成」归档

### 5.2 倒计时 F2（方案 4.2）
- DurationPicker 设定时长，可存快捷倒计时
- 基于**绝对结束时间戳**计算，避免后台漂移
- 运行中 ForegroundService + 通知栏
- 支持暂停/继续/+1分钟/重置

### 5.3 周重复 F3（方案 4.3）
- WeekChipRow 多选，存 `repeat_weekdays`(7-bit)
- `ScheduleUtil.nextWeeklyTrigger()` 计算下次时刻
- 触发后立即重注册下一次

### 5.4 月重复 F4（方案 4.4）
- MonthDateGrid 多选(1-31)，存 `repeat_month_days`(32-bit)
- 31 号在小月自动跳过；可选"月末落到最后一天"
- `ScheduleUtil.nextMonthlyTrigger()`

### 5.5 纪念日 F5（方案 4.5）
- 公历/农历选择，`LunarUtil` 离线换算
- 列表显示"距今 X 天 / 已过 N 周年"
- 提前 1/3/7/30 天预提醒（`advance_notify_days`）

### 5.6 公共：自定义与标签 F6/F14
- 标题、备注、图标、颜色、标签（工作/生活/学习）

### 关键产物
`AlarmViewModel`、`ScheduleUtil`、`LunarUtil`、`AlarmEditScreen`（5 类动态表单）

### 验收标准
5 类闹钟均能创建、编辑、删除、准点触发；位运算存储正确；农历换算准确。

---

## Step 6：后台常驻与强提醒（拆解自策划方案 4.7 / 4.8）

### 目标
保证闹钟"必达"——App 被杀、息屏、重启后仍能响。

### 6.1 后台保活 F8（方案 4.7）
- [ ] `AlarmReceiver`(BroadcastReceiver) 接收闹钟触发
- [ ] `AlarmService`(ForegroundService) 倒计时常驻
- [ ] `BootReceiver` 监听 `BOOT_COMPLETED`，重启后重注册全部闹钟
- [ ] 用 `setAlarmClock()` 获最高优先级，穿透 Doze
- [ ] App 启动从 Room 读取启用闹钟，全量重 schedule

### 6.2 悬浮窗/全屏提醒 F9（方案 4.8）
- [ ] `FullScreenActivity`：`setFullScreenIntent` 锁屏/后台弹出
- [ ] 前台/已授权时用 `WindowManager` 系统悬浮窗
- [ ] `WakeLock` + `KeyguardManager` 唤醒并显示在锁屏
- [ ] 铃声 + 振动 + 渐强；关闭/稍后(5/10/30分)
- [ ] 未授权悬浮窗 → 降级高优先级通知

### 6.3 Mate 50 省电适配（方案 8.1）
- [ ] 引导页：自启动、电池白名单、悬浮窗、锁屏通知
- [ ] 设置页「权限自检」一键跳转系统设置

### 验收标准
关机重启后闹钟仍响；App 被清理后仍响；锁屏下全屏弹出并响铃；飞行模式下本地闹钟正常。

---

## Step 7：数据同步（拆解自策划方案 4.9 / 十一）

### 目标
本地与 MySQL 多端一致，离线可用、联网补同步。

### 7.1 任务拆解
- [ ] `SyncWorker`(WorkManager 周期 5 分钟) + 启动时同步 + 变更触发
- [ ] 上行：扫描 `syncStatus!=0` 的记录写入 MySQL
- [ ] 下行：`updated_at > lastSync` 增量拉取
- [ ] `ConflictResolver`：LWW（以 updated_at 最新为准）
- [ ] 失败指数退避重试；本地临时 id（负数）→ 服务端 id 映射

### 验收标准
A 设备改动 5 分钟内同步到 B 设备；断网期间操作在恢复后成功补同步；冲突按 LWW 正确解决。

---

## Step 8：设置与 P1/P2 扩展（拆解自策划方案 F11-F15）

### 任务清单
- [ ] F11 铃声与振动：内置铃声 + 自定义本地音频（`READ_MEDIA_AUDIO`）
- [ ] F12 防睡过头：渐强音量、做题/扫码关闭
- [ ] F13 节假日跳过：周重复可设法定节假日跳过
- [ ] F14 标签分组管理
- [ ] F15 统计报表：触发记录(`alarm_logs`)、按时起床率
- [ ] 设置页：账号信息、铃声管理、权限自检、关于

### 验收标准
P1 功能可用；统计页能读 `alarm_logs` 展示数据。

---

## Step 9：测试（拆解自策划方案 十四）

| 类型 | 用例要点 |
|------|----------|
| 功能 | 每类闹钟 ×（启用/禁用/编辑/删除/触发/稍后） |
| 场景 | 重启后、被清理后、飞行模式、改系统时间、断网补同步 |
| 安全 | SQL 注入、连接泄漏、越权查询 |
| 极限 | 单用户 500 条闹钟、连续 30 天后台 |
| 兼容 | Mate 50 / Pro / RS、不同 HarmonyOS 小版本 |
| 网络 | 弱网、丢包、DNS 异常重试 |

### 验收标准
核心场景用例全过；闹钟准点率 ≥ 99.5%；无连接泄漏。

---

## Step 10：GitHub Actions 云端编译与发布（拆解自策划方案 十三 M9）

### 目标
代码推送到 GitHub 后，由 Actions 云端自动构建**签名 Release APK**，并上传到 Artifacts / Releases；最后提交华为应用市场。

### 10.1 任务清单
- [ ] 编写 `.github/workflows/android-release.yml`
- [ ] CI 中从 Secrets 还原 keystore、注入 DB 凭证
- [ ] 混淆规则保留 JDBC/反射类（`proguard-rules.pro`）
- [ ] 包体积优化（裁剪 mysql-connector 未用类，目标 APK ≤ 25MB）
- [ ] 打 tag（如 `v1.0.0`）触发构建并自动创建 GitHub Release
- [ ] 下载产物，华为应用市场素材：截图、描述、隐私政策
- [ ] 灰度发布 → 全量

### 10.2 触发策略
| 事件 | 行为 |
|------|------|
| push 到 `main` / PR | 跑编译 + 单元测试（debug，校验是否可构建） |
| push tag `v*` | 构建签名 Release APK，上传 Artifacts + 创建 Release |

### 10.3 工作流文件 `.github/workflows/android-release.yml`
```yaml
name: Android Release Build

on:
  push:
    branches: [ main ]
    tags: [ 'v*' ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle

      - name: Grant gradlew permission
        run: chmod +x ./gradlew

      # PR / main 推送：仅验证可编译 + 跑测试
      - name: Build Debug & Test
        if: ${{ !startsWith(github.ref, 'refs/tags/') }}
        env:
          DB_HOST: ${{ secrets.DB_HOST }}
          DB_USER: ${{ secrets.DB_USER }}
          DB_PWD_ENC: ${{ secrets.DB_PWD_ENC }}
        run: ./gradlew assembleDebug testDebugUnitTest

      # 打 tag：还原 keystore，构建签名 Release
      - name: Decode Keystore
        if: ${{ startsWith(github.ref, 'refs/tags/') }}
        run: echo "${{ secrets.SIGNING_KEYSTORE_BASE64 }}" | base64 -d > keystore.jks

      - name: Build Signed Release APK
        if: ${{ startsWith(github.ref, 'refs/tags/') }}
        env:
          DB_HOST: ${{ secrets.DB_HOST }}
          DB_USER: ${{ secrets.DB_USER }}
          DB_PWD_ENC: ${{ secrets.DB_PWD_ENC }}
          SIGNING_STORE_FILE: ${{ github.workspace }}/keystore.jks
          SIGNING_STORE_PASSWORD: ${{ secrets.SIGNING_STORE_PASSWORD }}
          SIGNING_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.SIGNING_KEY_PASSWORD }}
        run: ./gradlew assembleRelease

      - name: Upload APK to Artifacts
        if: ${{ startsWith(github.ref, 'refs/tags/') }}
        uses: actions/upload-artifact@v4
        with:
          name: smartclock-release
          path: app/build/outputs/apk/release/*.apk

      - name: Create GitHub Release
        if: ${{ startsWith(github.ref, 'refs/tags/') }}
        uses: softprops/action-gh-release@v2
        with:
          files: app/build/outputs/apk/release/*.apk
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - name: Cleanup keystore
        if: always()
        run: rm -f keystore.jks
```

### 10.4 混淆规则要点 `proguard-rules.pro`
```proguard
# MySQL Connector / JDBC 大量反射，需保留
-keep class com.mysql.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.mysql.**
-dontwarn java.sql.**
-dontwarn javax.**
# HikariCP
-keep class com.zaxxer.hikari.** { *; }
# Room 实体
-keep class com.smartclock.data.local.** { *; }
```

### 10.5 安全注意
- keystore 与所有密码**只存在 Secrets**，CI 用完即 `rm`，日志不打印
- 私有仓库 + 限制 Actions 对 fork PR 的 Secrets 访问（默认不暴露给 fork）
- `GITHUB_TOKEN` 由 Actions 自动提供，无需手动配置

### 关键产物
- `.github/workflows/android-release.yml`
- 每次打 tag 自动生成的签名 Release APK（Artifacts + Release 页可下载）

### 验收标准
push tag 后 Actions 绿灯；产物为**已签名** Release APK，真机安装稳定运行；仓库无任何密钥泄漏；通过华为应用市场审核。

---

## 附录 A：开发顺序依赖图

```
Step0 环境 + GitHub仓库初始化
  └─ Step1 UI设计（先行）
       └─ Step2 脚手架（gradle 读 Secrets/占位）
            └─ Step3 数据层
                 ├─ Step4 账号系统
                 └─ Step5 核心闹钟
                      └─ Step6 后台与提醒
                           └─ Step7 同步
                                └─ Step8 扩展
                                     └─ Step9 测试
                                          └─ Step10 GitHub Actions 云端编译与发布
```
> GitHub/CI 配置贯穿三处：Step0（仓库+Secrets+.gitignore）、Step2（gradle 注入逻辑）、Step10（Actions 工作流）。

## 附录 B：功能 → 步骤 映射表

| 功能 | 对应 Step | 方案章节 |
|------|-----------|----------|
| F1 单次 | Step5.1 | 4.1 |
| F2 倒计时 | Step5.2 | 4.2 |
| F3 周重复 | Step5.3 | 4.3 |
| F4 月重复 | Step5.4 | 4.4 |
| F5 纪念日 | Step5.5 | 4.5 |
| F6 自定义 | Step5.6 | — |
| F7 账号 | Step4 | 4.6 |
| F8 后台常驻 | Step6.1 | 4.7 |
| F9 悬浮提醒 | Step6.2 | 4.8 |
| F10 同步 | Step7 | 4.9 |
| F11-F15 | Step8 | F11-F15 |

## 附录 C：里程碑对照（与策划方案十三章）

| 本文档 Step | 策划方案里程碑 | 周期 |
|-------------|----------------|------|
| Step0-1 | M1 需求与原型 | 第1-2周 |
| Step2-3 | M2/M3 环境与框架 | 第3-4周 |
| Step4 | M4 账号系统 | 第4-5周 |
| Step5 | M5 核心闹钟 | 第5-8周 |
| Step6 | M6 后台与提醒 | 第8-9周 |
| Step7 | M7 同步与离线 | 第9-10周 |
| Step8-9 | M8 测试优化 | 第10-11周 |
| Step10 | M9 GitHub Actions 编译 + 上架 | 第12周 |

---

**文档结束**
