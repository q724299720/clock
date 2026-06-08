SET time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone VARCHAR(32) NULL UNIQUE,
    email VARCHAR(128) NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    nickname VARCHAR(64) NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'USER',
    status TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    last_login_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS alarms (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    client_uuid CHAR(36) NOT NULL,
    type INT NOT NULL,
    title VARCHAR(128) NOT NULL,
    note TEXT NULL,
    trigger_time DATETIME(3) NULL,
    duration_sec INT NULL,
    repeat_weekdays INT NOT NULL DEFAULT 0,
    repeat_month_days INT NOT NULL DEFAULT 0,
    anniversary_calendar INT NOT NULL DEFAULT 0,
    advance_notify_days VARCHAR(128) NULL,
    ringtone VARCHAR(255) NULL,
    vibrate TINYINT NOT NULL DEFAULT 1,
    volume_fade TINYINT NOT NULL DEFAULT 0,
    snooze_minutes INT NOT NULL DEFAULT 5,
    label VARCHAR(128) NULL,
    color VARCHAR(32) NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    start_date DATETIME(3) NULL,
    end_date DATETIME(3) NULL,
    schedule_mode INT NOT NULL DEFAULT 0,
    alert_policy INT NOT NULL DEFAULT 0,
    time_anchor_mode INT NOT NULL DEFAULT 0,
    interval_months INT NOT NULL DEFAULT 1,
    interval_years INT NOT NULL DEFAULT 1,
    next_override_mode INT NOT NULL DEFAULT 0,
    next_override_anchor_date VARCHAR(32) NULL,
    next_override_anchor_trigger_at DATETIME(3) NULL,
    next_override_trigger_at DATETIME(3) NULL,
    template_id VARCHAR(64) NULL,
    status INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_alarms_client_uuid UNIQUE (client_uuid),
    CONSTRAINT fk_alarms_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_alarms_user_updated (user_id, updated_at),
    INDEX idx_alarms_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS alarm_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    alarm_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    fired_at DATETIME(3) NOT NULL,
    action INT NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    log_hash CHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_alarm_logs_hash UNIQUE (log_hash),
    CONSTRAINT fk_alarm_logs_alarm FOREIGN KEY (alarm_id) REFERENCES alarms(id),
    CONSTRAINT fk_alarm_logs_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_alarm_logs_user_fired (user_id, fired_at DESC),
    INDEX idx_alarm_logs_alarm_fired (alarm_id, fired_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    last_used_at DATETIME(3) NULL,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_refresh_tokens_user (user_id),
    INDEX idx_refresh_tokens_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    admin_user_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(64) NULL,
    detail_json LONGTEXT NULL,
    ip_address VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_admin_audit_logs_user FOREIGN KEY (admin_user_id) REFERENCES users(id),
    INDEX idx_admin_audit_logs_created (created_at DESC),
    INDEX idx_admin_audit_logs_admin (admin_user_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
