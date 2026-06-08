<?php

declare(strict_types=1);

namespace SmartClock\Server\Service;

use PDO;
use SmartClock\Server\Support\ApiException;
use SmartClock\Server\Support\Time;

final class AdminService
{
    public function __construct(
        private readonly PDO $pdo,
        private readonly SyncService $syncService
    ) {
    }

    public function listUsers(?string $query, int $limit): array
    {
        $limit = max(1, min($limit, 200));
        if ($query === null || trim($query) === '') {
            $stmt = $this->pdo->prepare(
                "SELECT id, phone, email, nickname, role, status FROM users ORDER BY id DESC LIMIT $limit"
            );
            $stmt->execute();
        } else {
            $keyword = '%' . trim($query) . '%';
            $stmt = $this->pdo->prepare(
                "SELECT id, phone, email, nickname, role, status
                 FROM users
                 WHERE phone LIKE :kw OR email LIKE :kw OR nickname LIKE :kw
                 ORDER BY id DESC
                 LIMIT $limit"
            );
            $stmt->execute([':kw' => $keyword]);
        }

        return array_map([$this, 'mapUserDto'], $stmt->fetchAll());
    }

    public function getUser(int $userId): array
    {
        $stmt = $this->pdo->prepare(
            'SELECT id, phone, email, nickname, role, status FROM users WHERE id = :id LIMIT 1'
        );
        $stmt->execute([':id' => $userId]);
        $record = $stmt->fetch();
        if (!$record) {
            throw new ApiException(404, 'user not found', 'not_found');
        }

        return $this->mapUserDto($record);
    }

    public function updateUserStatus(int $adminUserId, int $userId, array $payload, string $ipAddress): array
    {
        if (!array_key_exists('status', $payload)) {
            throw new ApiException(400, 'status is required', 'bad_request');
        }
        $status = (int) $payload['status'];

        $stmt = $this->pdo->prepare(
            'UPDATE users SET status = :status, updated_at = :updated_at WHERE id = :id'
        );
        $stmt->execute([
            ':status' => $status,
            ':updated_at' => Time::toDb(Time::now()),
            ':id' => $userId,
        ]);

        $this->logAudit($adminUserId, 'USER_STATUS_UPDATE', 'USER', (string) $userId, ['status' => $status], $ipAddress);
        return $this->getUser($userId);
    }

    public function listAlarms(?int $userId, ?string $query, int $limit): array
    {
        $limit = max(1, min($limit, 300));

        if ($userId !== null && ($query === null || trim($query) === '')) {
            $stmt = $this->pdo->prepare(
                "SELECT * FROM alarms WHERE user_id = :user_id ORDER BY updated_at DESC LIMIT $limit"
            );
            $stmt->execute([':user_id' => $userId]);
        } elseif ($userId !== null) {
            $keyword = '%' . trim((string) $query) . '%';
            $stmt = $this->pdo->prepare(
                "SELECT * FROM alarms
                 WHERE user_id = :user_id AND (title LIKE :kw OR client_uuid LIKE :kw)
                 ORDER BY updated_at DESC
                 LIMIT $limit"
            );
            $stmt->execute([':user_id' => $userId, ':kw' => $keyword]);
        } elseif ($query === null || trim($query) === '') {
            $stmt = $this->pdo->prepare(
                "SELECT * FROM alarms ORDER BY updated_at DESC LIMIT $limit"
            );
            $stmt->execute();
        } else {
            $keyword = '%' . trim($query) . '%';
            $stmt = $this->pdo->prepare(
                "SELECT * FROM alarms
                 WHERE title LIKE :kw OR client_uuid LIKE :kw
                 ORDER BY updated_at DESC
                 LIMIT $limit"
            );
            $stmt->execute([':kw' => $keyword]);
        }

        return array_map(fn (array $row) => $this->syncService->mapAlarm($row), $stmt->fetchAll());
    }

    public function getAlarm(int $alarmId): array
    {
        return $this->syncService->mapAlarm($this->syncService->getAlarmById($alarmId));
    }

    public function updateAlarm(int $adminUserId, int $alarmId, array $payload, string $ipAddress): array
    {
        $existing = $this->syncService->getAlarmById($alarmId);
        $now = Time::toDb(Time::now());

        $stmt = $this->pdo->prepare(
            'UPDATE alarms SET
              title = :title, note = :note, enabled = :enabled, status = :status, trigger_time = :trigger_time,
              start_date = :start_date, end_date = :end_date, schedule_mode = :schedule_mode, alert_policy = :alert_policy,
              time_anchor_mode = :time_anchor_mode, interval_months = :interval_months, interval_years = :interval_years,
              template_id = :template_id, updated_at = :updated_at
             WHERE id = :id'
        );

        $stmt->execute([
            ':title' => array_key_exists('title', $payload) && $payload['title'] !== null
                ? $this->limitText((string) $payload['title'], 128, true)
                : $existing['title'],
            ':note' => array_key_exists('note', $payload) && $payload['note'] !== null ? (string) $payload['note'] : $existing['note'],
            ':enabled' => array_key_exists('enabled', $payload) && $payload['enabled'] !== null ? ((bool) $payload['enabled'] ? 1 : 0) : (int) $existing['enabled'],
            ':status' => array_key_exists('status', $payload) && $payload['status'] !== null ? (int) $payload['status'] : (int) $existing['status'],
            ':trigger_time' => array_key_exists('triggerTime', $payload) && $payload['triggerTime'] !== null ? Time::toDb(Time::parseIso((string) $payload['triggerTime'])) : $existing['trigger_time'],
            ':start_date' => array_key_exists('startDate', $payload) && $payload['startDate'] !== null ? Time::toDb(Time::parseIso((string) $payload['startDate'])) : $existing['start_date'],
            ':end_date' => array_key_exists('endDate', $payload) && $payload['endDate'] !== null ? Time::toDb(Time::parseIso((string) $payload['endDate'])) : $existing['end_date'],
            ':schedule_mode' => array_key_exists('scheduleMode', $payload) && $payload['scheduleMode'] !== null ? (int) $payload['scheduleMode'] : (int) $existing['schedule_mode'],
            ':alert_policy' => array_key_exists('alertPolicy', $payload) && $payload['alertPolicy'] !== null ? (int) $payload['alertPolicy'] : (int) $existing['alert_policy'],
            ':time_anchor_mode' => array_key_exists('timeAnchorMode', $payload) && $payload['timeAnchorMode'] !== null ? (int) $payload['timeAnchorMode'] : (int) $existing['time_anchor_mode'],
            ':interval_months' => array_key_exists('intervalMonths', $payload) && $payload['intervalMonths'] !== null ? max(1, (int) $payload['intervalMonths']) : (int) $existing['interval_months'],
            ':interval_years' => array_key_exists('intervalYears', $payload) && $payload['intervalYears'] !== null ? max(1, (int) $payload['intervalYears']) : (int) $existing['interval_years'],
            ':template_id' => array_key_exists('templateId', $payload) && $payload['templateId'] !== null
                ? $this->limitText((string) $payload['templateId'], 64, true)
                : $existing['template_id'],
            ':updated_at' => $now,
            ':id' => $alarmId,
        ]);

        $this->logAudit($adminUserId, 'ALARM_UPDATE', 'ALARM', (string) $alarmId, $payload, $ipAddress);
        return $this->getAlarm($alarmId);
    }

    public function softDeleteAlarm(int $adminUserId, int $alarmId, string $ipAddress): array
    {
        $stmt = $this->pdo->prepare(
            'UPDATE alarms SET status = 1, updated_at = :updated_at WHERE id = :id'
        );
        $stmt->execute([
            ':updated_at' => Time::toDb(Time::now()),
            ':id' => $alarmId,
        ]);

        $this->logAudit($adminUserId, 'ALARM_SOFT_DELETE', 'ALARM', (string) $alarmId, ['status' => 1], $ipAddress);
        return ['message' => 'ok'];
    }

    public function listAlarmLogs(?int $userId, int $limit): array
    {
        $limit = max(1, min($limit, 500));
        if ($userId === null) {
            $stmt = $this->pdo->prepare(
                "SELECT * FROM alarm_logs ORDER BY fired_at DESC LIMIT $limit"
            );
            $stmt->execute();
        } else {
            $stmt = $this->pdo->prepare(
                "SELECT * FROM alarm_logs WHERE user_id = :user_id ORDER BY fired_at DESC LIMIT $limit"
            );
            $stmt->execute([':user_id' => $userId]);
        }

        return array_map(function (array $row): array {
            return [
                'id' => (int) $row['id'],
                'alarmId' => (int) $row['alarm_id'],
                'userId' => (int) $row['user_id'],
                'firedAt' => Time::toIso(Time::parseDb($row['fired_at'] ?? null)),
                'action' => (int) $row['action'],
                'deviceId' => (string) $row['device_id'],
                'logHash' => (string) $row['log_hash'],
            ];
        }, $stmt->fetchAll());
    }

    public function listAuditLogs(int $limit): array
    {
        $limit = max(1, min($limit, 500));
        $stmt = $this->pdo->prepare(
            "SELECT * FROM admin_audit_logs ORDER BY created_at DESC LIMIT $limit"
        );
        $stmt->execute();

        return array_map(function (array $row): array {
            return [
                'id' => (int) $row['id'],
                'adminUserId' => (int) $row['admin_user_id'],
                'action' => (string) $row['action'],
                'targetType' => (string) $row['target_type'],
                'targetId' => $row['target_id'] !== null ? (string) $row['target_id'] : null,
                'detailJson' => $row['detail_json'] !== null ? (string) $row['detail_json'] : null,
                'ipAddress' => $row['ip_address'] !== null ? (string) $row['ip_address'] : null,
                'createdAt' => Time::toIso(Time::parseDb($row['created_at'] ?? null)),
            ];
        }, $stmt->fetchAll());
    }

    private function logAudit(int $adminUserId, string $action, string $targetType, ?string $targetId, mixed $detail, string $ipAddress): void
    {
        $stmt = $this->pdo->prepare(
            'INSERT INTO admin_audit_logs
             (admin_user_id, action, target_type, target_id, detail_json, ip_address, created_at)
             VALUES (:admin_user_id, :action, :target_type, :target_id, :detail_json, :ip_address, :created_at)'
        );
        $stmt->execute([
            ':admin_user_id' => $adminUserId,
            ':action' => $action,
            ':target_type' => $targetType,
            ':target_id' => $targetId,
            ':detail_json' => json_encode($detail, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
            ':ip_address' => $ipAddress,
            ':created_at' => Time::toDb(Time::now()),
        ]);
    }

    private function mapUserDto(array $record): array
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

    private function limitText(?string $value, int $maxLength, bool $trim = false): ?string
    {
        if ($value === null) {
            return null;
        }

        $normalized = $trim ? trim($value) : $value;
        if ($normalized === '') {
            return $normalized;
        }

        if (function_exists('mb_substr')) {
            return mb_substr($normalized, 0, $maxLength, 'UTF-8');
        }

        if (preg_match_all('/./us', $normalized, $matches) === 1) {
            return implode('', array_slice($matches[0], 0, $maxLength));
        }

        return substr($normalized, 0, $maxLength);
    }
}
