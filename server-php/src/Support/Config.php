<?php

declare(strict_types=1);

namespace SmartClock\Server\Support;

final class Config
{
    public function __construct(
        public readonly string $appEnv,
        public readonly bool $appDebug,
        public readonly string $appUrl,
        public readonly string $dbHost,
        public readonly int $dbPort,
        public readonly string $dbDatabase,
        public readonly string $dbUsername,
        public readonly string $dbPassword,
        public readonly string $jwtIssuer,
        public readonly int $jwtAccessTokenMinutes,
        public readonly int $jwtRefreshTokenDays,
        public readonly int $jwtAppRefreshTokenDays,
        public readonly string $jwtSecret,
        public readonly string $jwtRefreshPepper,
        public readonly string $adminAccount,
        public readonly string $adminPassword,
        public readonly bool $adminIsEmail
    ) {
    }

    public static function fromEnv(): self
    {
        return new self(
            appEnv: self::env('APP_ENV', 'local'),
            appDebug: self::envBool('APP_DEBUG', false),
            appUrl: self::env('APP_URL', 'http://localhost'),
            dbHost: self::env('DB_HOST', '127.0.0.1'),
            dbPort: self::envInt('DB_PORT', 3306),
            dbDatabase: self::env('DB_DATABASE', 'clock'),
            dbUsername: self::env('DB_USERNAME', 'clock'),
            dbPassword: self::env('DB_PASSWORD', ''),
            jwtIssuer: self::env('JWT_ISSUER', 'smartclock-php'),
            jwtAccessTokenMinutes: self::envInt('JWT_ACCESS_TOKEN_MINUTES', 15),
            jwtRefreshTokenDays: self::envInt('JWT_REFRESH_TOKEN_DAYS', 7),
            jwtAppRefreshTokenDays: self::envInt('JWT_APP_REFRESH_TOKEN_DAYS', 36500),
            jwtSecret: self::env('JWT_SECRET', 'change-me-change-me-change-me-change-me'),
            jwtRefreshPepper: self::env('JWT_REFRESH_PEPPER', 'change-me-refresh-pepper'),
            adminAccount: self::env('SMARTCLOCK_ADMIN_ACCOUNT', ''),
            adminPassword: self::env('SMARTCLOCK_ADMIN_PASSWORD', ''),
            adminIsEmail: self::envBool('SMARTCLOCK_ADMIN_IS_EMAIL', true),
        );
    }

    public function dsn(): string
    {
        return sprintf(
            'mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4',
            $this->dbHost,
            $this->dbPort,
            $this->dbDatabase
        );
    }

    private static function env(string $key, string $default): string
    {
        $value = $_ENV[$key] ?? $_SERVER[$key] ?? getenv($key);
        if ($value === false || $value === null || $value === '') {
            return $default;
        }

        return (string) $value;
    }

    private static function envInt(string $key, int $default): int
    {
        return (int) self::env($key, (string) $default);
    }

    private static function envBool(string $key, bool $default): bool
    {
        $value = strtolower(self::env($key, $default ? 'true' : 'false'));
        return in_array($value, ['1', 'true', 'yes', 'on'], true);
    }
}
