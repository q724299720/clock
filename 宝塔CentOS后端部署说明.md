# SmartClock PHP 后端部署说明（CentOS + 宝塔）

## 1. 当前方案

后端已经切换为 PHP 版，目录在：

[server-php](/E:/programdata/Seafile/AI/clock/server-php)

这套部署不再依赖：

- Java 17
- Spring Boot
- systemd 跑 jar

现在改成：

- 宝塔站点：`PHP 站点`
- 管理台前端：静态文件放在同一个站点根目录
- API 后端：`server-php/public/index.php`
- 数据库：MySQL，本机 `clock`

## 2. 你的真实配置

- 域名：`your-domain.example.com`
- 数据库名：`clock`
- 数据库用户名：`clock`
- 管理员账号：`admin@example.com`
- 管理员账号类型：邮箱

你还需要自己填写的敏感信息：

- 数据库密码
- 管理员密码
- JWT 密钥
- JWT refresh pepper

## 3. 站点类型怎么选

在宝塔新建站点时，**选 PHP 站点**，不要再选 Java 项目，也不要选纯静态项目。

原因：

- 现在 API 是 PHP
- 管理台前端静态文件和 PHP 后端会共用一个站点根目录
- 同一套 Nginx/PHP 配置就能同时服务：
  - `/` 管理台页面
  - `/api/v1/...` 后端接口
  - `/actuator/health` 健康检查

## 4. 需要上传哪些文件

### 4.1 上传 PHP 后端

把整个目录上传到服务器，例如：

[server-php](/E:/programdata/Seafile/AI/clock/server-php)

建议服务器路径：

`/www/wwwroot/your-domain.example.com/`

上传后至少要有这些内容：

- `/www/wwwroot/your-domain.example.com/public/index.php`
- `/www/wwwroot/your-domain.example.com/src/...`
- `/www/wwwroot/your-domain.example.com/database/schema.sql`
- `/www/wwwroot/your-domain.example.com/.env`

### 4.2 上传管理台前端

再把管理台构建产物：

[dist](/E:/programdata/Seafile/AI/clock/admin-web/dist)

里的内容复制到：

`/www/wwwroot/your-domain.example.com/public/`

也就是最终 `public` 目录里应该同时有：

- `index.php`
- `index.html`
- `assets/`

最终结构类似：

```text
/www/wwwroot/your-domain.example.com/
  .env
  bootstrap.php
  database/
  public/
    index.php
    index.html
    assets/
  src/
```

## 5. .env 配置

把下面文件复制一份：

[.env.example](/E:/programdata/Seafile/AI/clock/server-php/.env.example)

在服务器根目录生成：

`/www/wwwroot/your-domain.example.com/.env`

内容改成这样：

```env
APP_ENV=production
APP_DEBUG=false
APP_URL=https://your-domain.example.com

DB_HOST=127.0.0.1
DB_PORT=3306
DB_DATABASE=clock
DB_USERNAME=clock
DB_PASSWORD=你的MySQL密码

JWT_ISSUER=smartclock-php
JWT_ACCESS_TOKEN_MINUTES=15
JWT_REFRESH_TOKEN_DAYS=7
JWT_SECRET=请替换成至少32字节随机密钥
JWT_REFRESH_PEPPER=请替换成另一段随机字符串

SMARTCLOCK_ADMIN_ACCOUNT=admin@example.com
SMARTCLOCK_ADMIN_PASSWORD=请替换成管理员密码
SMARTCLOCK_ADMIN_IS_EMAIL=true
```

## 6. 数据库初始化

先在宝塔创建数据库：

- 数据库名：`clock`
- 用户名：`clock`
- 密码：你自己设置

然后把：

[schema.sql](/E:/programdata/Seafile/AI/clock/server-php/database/schema.sql)

导入到数据库 `clock`。

你可以用两种方式：

### 方式 A：宝塔导入 SQL

直接在宝塔数据库管理页面导入 `schema.sql`。

### 方式 B：命令行导入

```bash
mysql -u clock -p clock < /www/wwwroot/your-domain.example.com/database/schema.sql
```

## 7. MySQL 时区

建议 MySQL 继续强制 UTC：

```ini
[mysqld]
default-time-zone = '+00:00'
```

然后重启 MySQL，并检查：

```sql
SELECT @@global.time_zone, @@session.time_zone;
```

## 8. 宝塔站点根目录

站点根目录建议直接设为：

`/www/wwwroot/your-domain.example.com/public`

原因：

- 浏览器访问根路径时直接拿到前端 `index.html`
- `/api/...` 会交给同目录下的 `index.php`
- 不会把 `.env` 和 `src/` 暴露在 Web 根目录

## 9. 宝塔 PHP 版本

站点 PHP 版本建议：

- `PHP 8.2` 或 `PHP 8.3`

不要用 PHP 7.x。

## 10. 宝塔伪静态 / Nginx 配置

因为这是“前端静态页面 + PHP API 共站点”的结构，Nginx 里要保证：

1. 静态资源优先
2. `/api/` 和 `/actuator/health` 走 `index.php`
3. 前端 SPA 刷新回退到 `index.html`

核心配置建议如下：

```nginx
limit_req_zone $binary_remote_addr zone=smartclock_auth:10m rate=5r/m;
limit_req_zone $binary_remote_addr zone=smartclock_register:10m rate=3r/m;
limit_req_zone $binary_remote_addr zone=smartclock_api:10m rate=60r/m;

server {
    listen 80;
    listen 443 ssl http2;
    server_name your-domain.example.com;

    root /www/wwwroot/your-domain.example.com/public;
    index index.php index.html;
    client_max_body_size 10m;

    location /assets/ {
        try_files $uri =404;
        expires 7d;
    }

    location = /api/v1/auth/login {
        limit_req zone=smartclock_auth burst=10 nodelay;
        try_files $uri /index.php?$query_string;
    }

    location = /api/v1/auth/register {
        limit_req zone=smartclock_register burst=6 nodelay;
        try_files $uri /index.php?$query_string;
    }

    location /api/ {
        limit_req zone=smartclock_api burst=60 nodelay;
        try_files $uri /index.php?$query_string;
    }

    location = /actuator/health {
        try_files $uri /index.php?$query_string;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }

    location ~ \.php$ {
        fastcgi_pass unix:/tmp/php-cgi-83.sock;
        fastcgi_index index.php;
        include fastcgi_params;
        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;
        fastcgi_param HTTP_X_REAL_IP $remote_addr;
        fastcgi_param HTTP_X_FORWARDED_FOR $proxy_add_x_forwarded_for;
        fastcgi_param HTTP_X_FORWARDED_PROTO $scheme;
    }
}
```

注意：

- `fastcgi_pass` 里的 sock 路径要按你宝塔实际 PHP 版本改。
- 例如宝塔 PHP 8.2/8.3 的 sock 路径可能不同，先在宝塔默认站点配置里抄一份现成的。

## 11. 启动后验证

先验证前端首页：

```text
https://your-domain.example.com/
```

再验证健康检查：

```text
https://your-domain.example.com/actuator/health
```

预期返回：

```json
{"status":"UP"}
```

再验证未登录接口：

```text
https://your-domain.example.com/api/v1/me
```

预期返回 `401`。

## 12. Android 端地址

Android 示例地址已经改成真实域名：

[local.defaults.properties.example](/E:/programdata/Seafile/AI/clock/local.defaults.properties.example)

核心值是：

```properties
API_BASE_URL=https://your-domain.example.com
```

发版前，把它复制成 `local.defaults.properties` 再重新打包 APK。

## 13. 部署后第一次后台登录

管理员账号固定是：

`admin@example.com`

密码是你在 `.env` 里设置的：

`SMARTCLOCK_ADMIN_PASSWORD`

首次访问：

`https://your-domain.example.com/`

进入的就是后台管理台登录页。

## 14. 后续升级

以后升级 PHP 后端时，只需要覆盖这些内容：

- `server-php/public/index.php`
- `server-php/src/`
- `server-php/bootstrap.php`
- `server-php/database/schema.sql` 如果有结构变更

前端改版时，只需要重新覆盖：

- `admin-web/dist/index.html`
- `admin-web/dist/assets/`

## 15. 最容易踩的坑

1. 站点根目录没有指到 `public/`，导致暴露 `.env` 和源码。
2. 忘了把管理台 `dist` 内容复制进 `public/`。
3. 宝塔 Nginx 没把 `/api/` 交给 `index.php`。
4. `.env` 的数据库密码、JWT 密钥没填。
5. PHP 版本太低，不是 8.2/8.3。
