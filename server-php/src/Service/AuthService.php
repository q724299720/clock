<?php

declare(strict_types=1);

namespace SmartClock\Server\Service;

use PDO;
use SmartClock\Server\Support\ApiException;
use SmartClock\Server\Support\AuthContext;
use SmartClock\Server\Support\Config;
use SmartClock\Server\Support\JwtService;
use SmartClock\Server\Support\Time;

final class AuthService
{
    public function __construct(
        private readonly PDO $pdo,
        private readonly Config $config,
        private readonly JwtService $jwtService
    ) {
    }

    public function ensureAdminBootstrap(): void
    {
        if ($this->config->adminAccount === '' || $this->config->adminPassword === '') {
            return;
        }

        $user = $this->findUserByAccount($this->config->adminAccount, $this->config->adminIsEmail);
        if ($user !== null) {
            return;
        }

        $now = Time::now();
        $column = $this->config->adminIsEmail ? 'email' : 'phone';
        $stmt = $this->pdo->prepare(
            "INSERT INTO users ($column, password_hash, nickname, role, status, created_at, updated_at)
             VALUES (:account, :password_hash, :nickname, 'ADMIN', 0, :created_at, :updated_at)"
        );
        $stmt->execute([
            ':account' => $this->config->adminAccount,
            ':password_hash' => password_hash($this->config->adminPassword, PASSWORD_BCRYPT),
            ':nickname' => 'Super Admin',
            ':created_at' => Time::toDb($now),
            ':updated_at' => Time::toDb($now),
        ]);
    }

    public function register(array $payload): array
    {
        $account = trim((string) ($payload['account'] ?? ''));
        $isEmail = (bool) ($payload['isEmail'] ?? false);
        $password = (string) ($payload['password'] ?? '');
        $nickname = isset($payload['nickname']) ? trim((string) $payload['nickname']) : null;
        $clientType = $this->normalizeClientType($payload['clientType'] ?? null);

        if ($account === '' || $password === '') {
            throw new ApiException(400, 'account and password are required', 'bad_request');
        }
        if (strlen($password) < 6) {
            throw new ApiException(400, 'password must be at least 6 characters', 'bad_request');
        }
        if ($this->findUserByAccount($account, $isEmail) !== null) {
            throw new ApiException(409, 'account already exists', 'account_exists');
        }

        $now = Time::now();
        $column = $isEmail ? 'email' : 'phone';

        $this->pdo->beginTransaction();
        try {
            $stmt = $this->pdo->prepare(
                "INSERT INTO users ($column, password_hash, nickname, role, status, created_at, updated_at)
                 VALUES (:account, :password_hash, :nickname, 'USER', 0, :created_at, :updated_at)"
            );
            $stmt->execute([
                ':account' => $account,
                ':password_hash' => password_hash($password, PASSWORD_BCRYPT),
                ':nickname' => $nickname,
                ':created_at' => Time::toDb($now),
                ':updated_at' => Time::toDb($now),
            ]);

            $user = $this->findUserById((int) $this->pdo->lastInsertId());
            if ($user === null) {
                throw new ApiException(500, 'failed to create user', 'server_error');
            }

            $result = $this->issueTokens($user, $clientType);
            $this->pdo->commit();
            return $result;
        } catch (\Throwable $e) {
            $this->pdo->rollBack();
            if ($e instanceof ApiException) {
                throw $e;
            }
            throw new ApiException(500, 'failed to create user', 'server_error');
        }
    }

    public function login(array $payload): array
    {
        $account = trim((string) ($payload['account'] ?? ''));
        $isEmail = (bool) ($payload['isEmail'] ?? false);
        $password = (string) ($payload['password'] ?? '');
        $clientType = $this->normalizeClientType($payload['clientType'] ?? null);
        if ($account === '' || $password === '') {
            throw new ApiException(400, 'account and password are required', 'bad_request');
        }

        $user = $this->findUserWithPasswordByAccount($account, $isEmail);
        if ($user === null || !password_verify($password, (string) $user['password_hash'])) {
            throw new ApiException(401, 'invalid credentials', 'unauthorized');
        }
        if ((int) $user['status'] !== 0) {
            throw new ApiException(403, 'user is disabled', 'forbidden');
        }

        $now = Time::now();
        $stmt = $this->pdo->prepare(
            'UPDATE users SET last_login_at = :last_login_at, updated_at = :updated_at WHERE id = :id'
        );
        $stmt->execute([
            ':last_login_at' => Time::toDb($now),
            ':updated_at' => Time::toDb($now),
            ':id' => (int) $user['id'],
        ]);

        return $this->issueTokens($this->mapUser($user), $clientType);
    }

    public function refresh(array $payload): array
    {
        $refreshToken = trim((string) ($payload['refreshToken'] ?? ''));
        $clientType = $this->normalizeClientType($payload['clientType'] ?? null);
        if ($refreshToken === '') {
            throw new ApiException(400, 'refreshToken is required', 'bad_request');
        }

        $stmt = $this->pdo->prepare(
            'SELECT rt.id AS refresh_id, rt.user_id, rt.expires_at, rt.revoked_at,
                    u.id, u.phone, u.email, u.nickname, u.role, u.status
             FROM refresh_tokens rt
             JOIN users u ON u.id = rt.user_id
             WHERE rt.token_hash = :token_hash
             LIMIT 1'
        );
        $stmt->execute([
            ':token_hash' => $this->hashRefreshToken($refreshToken),
        ]);
        $record = $stmt->fetch();
        if (!$record) {
            throw new ApiException(401, 'invalid refresh token', 'unauthorized');
        }

        $now = Time::now();
        $expiresAt = Time::parseDb((string) $record['expires_at']);
        if ($record['revoked_at'] !== null || ($expiresAt !== null && $expiresAt < $now)) {
            throw new ApiException(401, 'refresh token expired', 'unauthorized');
        }

        $user = $this->mapUser($record);
        if ((int) $user['status'] !== 0) {
            throw new ApiException(403, 'user is disabled', 'forbidden');
        }

        $this->pdo->beginTransaction();
        try {
            $revoke = $this->pdo->prepare(
                'UPDATE refresh_tokens SET revoked_at = :revoked_at, last_used_at = :last_used_at WHERE id = :id'
            );
            $revoke->execute([
                ':revoked_at' => Time::toDb($now),
                ':last_used_at' => Time::toDb($now),
                ':id' => (int) $record['refresh_id'],
            ]);

            $result = $this->issueTokens($user, $clientType);
            $this->pdo->commit();
            return $result;
        } catch (\Throwable $e) {
            $this->pdo->rollBack();
            if ($e instanceof ApiException) {
                throw $e;
            }
            throw new ApiException(500, 'refresh failed', 'server_error');
        }
    }

    public function logout(AuthContext $context, array $payload): array
    {
        $refreshToken = trim((string) ($payload['refreshToken'] ?? ''));
        if ($refreshToken === '') {
            throw new ApiException(400, 'refreshToken is required', 'bad_request');
        }

        $stmt = $this->pdo->prepare(
            'UPDATE refresh_tokens SET revoked_at = :revoked_at
             WHERE user_id = :user_id AND token_hash = :token_hash AND revoked_at IS NULL'
        );
        $stmt->execute([
            ':revoked_at' => Time::toDb(Time::now()),
            ':user_id' => $context->userId,
            ':token_hash' => $this->hashRefreshToken($refreshToken),
        ]);

        return ['message' => 'ok'];
    }

    public function me(AuthContext $context): array
    {
        $user = $this->findUserById($context->userId);
        if ($user === null) {
            throw new ApiException(404, 'user not found', 'not_found');
        }

        return $this->toUserDto($user);
    }

    public function meEntity(int $userId): array
    {
        $user = $this->findUserById($userId);
        if ($user === null) {
            throw new ApiException(404, 'user not found', 'not_found');
        }

        return $user;
    }

    private function issueTokens(array $user, string $clientType = 'web'): array
    {
        $now = Time::now();
        $access = $this->jwtService->createAccessToken((int) $user['id'], (string) $user['role']);
        $refreshToken = bin2hex(random_bytes(32));
        $refreshExpiresAt = $now->modify(sprintf('+%d days', $this->refreshTokenDaysForClient($clientType)));

        $stmt = $this->pdo->prepare(
            'INSERT INTO refresh_tokens (user_id, token_hash, expires_at, revoked_at, created_at, last_used_at)
             VALUES (:user_id, :token_hash, :expires_at, NULL, :created_at, :last_used_at)'
        );
        $stmt->execute([
            ':user_id' => (int) $user['id'],
            ':token_hash' => $this->hashRefreshToken($refreshToken),
            ':expires_at' => Time::toDb($refreshExpiresAt),
            ':created_at' => Time::toDb($now),
            ':last_used_at' => Time::toDb($now),
        ]);

        return [
            'user' => $this->toUserDto($user),
            'accessToken' => $access['token'],
            'refreshToken' => $refreshToken,
            'accessTokenExpiresAt' => Time::toIso($access['expiresAt']),
            'refreshTokenExpiresAt' => Time::toIso($refreshExpiresAt),
        ];
    }

    private function hashRefreshToken(string $token): string
    {
        return hash('sha256', $this->config->jwtRefreshPepper . ':' . $token);
    }

    private function normalizeClientType(mixed $value): string
    {
        return strtolower(trim((string) $value)) === 'app' ? 'app' : 'web';
    }

    private function refreshTokenDaysForClient(string $clientType): int
    {
        $days = $clientType === 'app'
            ? $this->config->jwtAppRefreshTokenDays
            : $this->config->jwtRefreshTokenDays;

        return max(1, $days);
    }

    private function findUserByAccount(string $account, bool $isEmail): ?array
    {
        $column = $isEmail ? 'email' : 'phone';
        $stmt = $this->pdo->prepare(
            "SELECT id, phone, email, nickname, role, status FROM users WHERE $column = :account LIMIT 1"
        );
        $stmt->execute([':account' => $account]);
        $record = $stmt->fetch();

        return $record ? $this->mapUser($record) : null;
    }

    private function findUserWithPasswordByAccount(string $account, bool $isEmail): ?array
    {
        $column = $isEmail ? 'email' : 'phone';
        $stmt = $this->pdo->prepare(
            "SELECT id, phone, email, nickname, role, status, password_hash FROM users WHERE $column = :account LIMIT 1"
        );
        $stmt->execute([':account' => $account]);
        $record = $stmt->fetch();

        return $record ? $this->mapUser($record) + ['password_hash' => $record['password_hash']] : null;
    }

    private function findUserById(int $userId): ?array
    {
        $stmt = $this->pdo->prepare(
            'SELECT id, phone, email, nickname, role, status FROM users WHERE id = :id LIMIT 1'
        );
        $stmt->execute([':id' => $userId]);
        $record = $stmt->fetch();

        return $record ? $this->mapUser($record) : null;
    }

    private function mapUser(array $record): array
    {
        return [
            'id' => (int) $record['id'],
            'phone' => $record['phone'] !== null ? (string) $record['phone'] : null,
            'email' => $record['email'] !== null ? (string) $record['email'] : null,
            'nickname' => $record['nickname'] !== null ? (string) $record['nickname'] : null,
            'role' => (string) $record['role'],
            'status' => (int) $record['status'],
        ];
    }

    private function toUserDto(array $user): array
    {
        return [
            'id' => (int) $user['id'],
            'phone' => $user['phone'],
            'email' => $user['email'],
            'nickname' => $user['nickname'],
            'role' => $user['role'],
            'status' => (int) $user['status'],
        ];
    }
}
