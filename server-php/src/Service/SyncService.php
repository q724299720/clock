<?php

declare(strict_types=1);

namespace SmartClock\Server\Service;

use PDO;
use SmartClock\Server\Support\ApiException;
use SmartClock\Server\Support\Time;

final class SyncService
{
    public function __construct(
        private readonly PDO $pdo
    ) {
    }

    public function bootstrap(array $user): array
    {
        $stmt = $this->pdo->prepare(
            'SELECT * FROM alarms WHERE user_id = :user_id ORDER BY updated_at ASC'
        );
        $stmt->execute([':user_id' => (int) $user['id']]);

        return [
            'user' => [
                'id' => (int) $user['id'],
                'phone' => $user['phone'],
                'email' => $user['email'],
                'nickname' => $user['nickname'],
                'role' => $user['role'],
                'status' => (int) $user['status'],
            ],
            'alarms' => array_map(fn (array $row) => $this->mapAlarm($row), $stmt->fetchAll()),
            'serverTime' => Time::toIso(Time::now()),
        ];
    }

    public function pushAlarms(int $userId, array $payload): array
    {
        $alarms = $payload['alarms'] ?? null;
        if (!is_array($alarms) || $alarms === []) {
            throw new ApiException(400, 'alarms is required', 'bad_request');
        }

        $result = [];
        $errors = [];

        foreach ($alarms as $alarm) {
            if (!is_array($alarm)) {
                $errors[] = [
                    'clientUuid' => null,
                    'message' => 'alarm payload is invalid',
                ];
                continue;
            }

            $clientUuid = trim((string) ($alarm['clientUuid'] ?? ''));
            try {
                $result[] = $this->upsertAlarm($userId, $alarm);
            } catch (\Throwable $e) {
                $message = trim($e->getMessage()) !== '' ? $e->getMessage() : 'push alarm failed';
                $errors[] = [
                    'clientUuid' => $clientUuid !== '' ? $clientUuid : null,
                    'message' => $message,
                ];
                error_log(sprintf(
                    '[SmartClock PHP] push alarm failed user=%d client=%s %s in %s:%d',
                    $userId,
                    $clientUuid !== '' ? $clientUuid : '-',
                    $message,
                    $e->getFile(),
                    $e->getLine()
                ));
            }
        }

        if ($result === []) {
            $firstError = $errors[0]['message'] ?? 'push alarms failed';
            throw new ApiException(500, $firstError, 'server_error');
        }

        return [
            'alarms' => $result,
            'errors' => $errors,
            'serverTime' => Time::toIso(Time::now()),
        ];
    }

    public function pullAlarms(int $userId, ?string $since): array
    {
        if ($since === null || $since === '') {
            $stmt = $this->pdo->prepare(
                'SELECT * FROM alarms WHERE user_id = :user_id ORDER BY updated_at ASC'
            );
            $stmt->execute([':user_id' => $userId]);
        } else {
            $stmt = $this->pdo->prepare(
                'SELECT * FROM alarms WHERE user_id = :user_id AND updated_at > :since ORDER BY updated_at ASC'
            );
            $stmt->execute([
                ':user_id' => $userId,
                ':since' => Time::toDb(Time::parseIso($since)),
            ]);
        }

        return [
            'alarms' => array_map(fn (array $row) => $this->mapAlarm($row), $stmt->fetchAll()),
            'serverTime' => Time::toIso(Time::now()),
        ];
    }

    public function uploadLogs(int $userId, array $payload): array
    {
        $alarmLogs = $payload['alarmLogs'] ?? null;
        if (!is_array($alarmLogs) || $alarmLogs === []) {
            throw new ApiException(400, 'alarmLogs is required', 'bad_request');
        }

        $stmt = $this->pdo->prepare(
            'INSERT IGNORE INTO alarm_logs
             (alarm_id, user_id, fired_at, action, device_id, log_hash, created_at)
             VALUES (:alarm_id, :user_id, :fired_at, :action, :device_id, :log_hash, :created_at)'
        );

        $inserted = 0;
        $ignored = 0;
        $errors = [];
        $now = Time::toDb(Time::now());

        foreach ($alarmLogs as $log) {
            if (!is_array($log)) {
                $ignored++;
                $errors[] = [
                    'alarmId' => null,
                    'message' => 'alarm log payload is invalid',
                ];
                continue;
            }
            $alarmId = (int) ($log['alarmId'] ?? 0);
            $firedAt = (string) ($log['firedAt'] ?? '');
            $deviceId = trim((string) ($log['deviceId'] ?? ''));
            $logHash = trim((string) ($log['logHash'] ?? ''));

            if ($alarmId <= 0 || $firedAt === '' || $deviceId === '' || $logHash === '') {
                $ignored++;
                $errors[] = [
                    'alarmId' => $alarmId > 0 ? $alarmId : null,
                    'message' => 'invalid alarm log payload',
                ];
                continue;
            }

            try {
                $stmt->execute([
                    ':alarm_id' => $alarmId,
                    ':user_id' => $userId,
                    ':fired_at' => Time::toDb(Time::parseIso($firedAt)),
                    ':action' => (int) ($log['action'] ?? 0),
                    ':device_id' => $deviceId,
                    ':log_hash' => $logHash,
                    ':created_at' => $now,
                ]);

                if ($stmt->rowCount() > 0) {
                    $inserted++;
                } else {
                    $ignored++;
                }
            } catch (\Throwable $e) {
                $ignored++;
                $message = trim($e->getMessage()) !== '' ? $e->getMessage() : 'insert alarm log failed';
                $errors[] = [
                    'alarmId' => $alarmId,
                    'message' => $message,
                ];
                error_log(sprintf(
                    '[SmartClock PHP] upload alarm log failed user=%d alarm=%d %s in %s:%d',
                    $userId,
                    $alarmId,
                    $message,
                    $e->getFile(),
                    $e->getLine()
                ));
            }
        }

        return [
            'inserted' => $inserted,
            'ignored' => $ignored,
            'errors' => $errors,
            'serverTime' => Time::toIso(Time::now()),
        ];
    }

    public function getAlarmById(int $alarmId): array
    {
        $stmt = $this->pdo->prepare('SELECT * FROM alarms WHERE id = :id LIMIT 1');
        $stmt->execute([':id' => $alarmId]);
        $record = $stmt->fetch();
        if (!$record) {
            throw new ApiException(404, 'alarm not found', 'not_found');
        }

        return $record;
    }

    public function mapAlarm(array $record): array
    {
        return [
            'id' => isset($record['id']) ? (int) $record['id'] : null,
            'clientUuid' => (string) $record['client_uuid'],
            'type' => (int) $record['type'],
            'title' => (string) $record['title'],
            'note' => $record['note'] !== null ? (string) $record['note'] : null,
            'triggerTime' => Time::toIso(Time::parseDb($record['trigger_time'] ?? null)),
            'durationSec' => isset($record['duration_sec']) ? (int) $record['duration_sec'] : null,
            'repeatWeekdays' => (int) ($record['repeat_weekdays'] ?? 0),
            'repeatMonthDays' => (int) ($record['repeat_month_days'] ?? 0),
            'anniversaryCalendar' => (int) ($record['anniversary_calendar'] ?? 0),
            'advanceNotifyDays' => $record['advance_notify_days'] !== null ? (string) $record['advance_notify_days'] : null,
            'ringtone' => $record['ringtone'] !== null ? (string) $record['ringtone'] : null,
            'vibrate' => (int) ($record['vibrate'] ?? 0) === 1,
            'volumeFade' => (int) ($record['volume_fade'] ?? 0) === 1,
            'snoozeMinutes' => (int) ($record['snooze_minutes'] ?? 5),
            'label' => $record['label'] !== null ? (string) $record['label'] : null,
            'color' => $record['color'] !== null ? (string) $record['color'] : null,
            'enabled' => (int) ($record['enabled'] ?? 0) === 1,
            'startDate' => Time::toIso(Time::parseDb($record['start_date'] ?? null)),
            'endDate' => Time::toIso(Time::parseDb($record['end_date'] ?? null)),
            'scheduleMode' => (int) ($record['schedule_mode'] ?? 0),
            'alertPolicy' => (int) ($record['alert_policy'] ?? 0),
            'timeAnchorMode' => (int) ($record['time_anchor_mode'] ?? 0),
            'intervalMonths' => (int) ($record['interval_months'] ?? 1),
            'intervalYears' => (int) ($record['interval_years'] ?? 1),
            'nextOverrideMode' => (int) ($record['next_override_mode'] ?? 0),
            'nextOverrideAnchorDate' => $record['next_override_anchor_date'] !== null ? (string) $record['next_override_anchor_date'] : null,
            'nextOverrideAnchorTriggerAt' => Time::toIso(Time::parseDb($record['next_override_anchor_trigger_at'] ?? null)),
            'nextOverrideTriggerAt' => Time::toIso(Time::parseDb($record['next_override_trigger_at'] ?? null)),
            'templateId' => $record['template_id'] !== null ? (string) $record['template_id'] : null,
            'status' => (int) ($record['status'] ?? 0),
            'createdAt' => Time::toIso(Time::parseDb($record['created_at'] ?? null)),
            'updatedAt' => Time::toIso(Time::parseDb($record['updated_at'] ?? null)),
        ];
    }

    private function upsertAlarm(int $userId, array $dto): array
    {
        $clientUuid = trim((string) ($dto['clientUuid'] ?? ''));
        $title = $this->limitText((string) ($dto['title'] ?? ''), 128, true);
        if ($clientUuid === '' || $title === '') {
            throw new ApiException(400, 'clientUuid and title are required', 'bad_request');
        }

        $existingStmt = $this->pdo->prepare(
            'SELECT id, created_at FROM alarms WHERE user_id = :user_id AND client_uuid = :client_uuid LIMIT 1'
        );
        $existingStmt->execute([
            ':user_id' => $userId,
            ':client_uuid' => $clientUuid,
        ]);
        $existing = $existingStmt->fetch();

        $now = Time::now();
        $createdAt = Time::parseIso(isset($dto['createdAt']) ? (string) $dto['createdAt'] : null) ?? $now;
        $fields = [
            ':user_id' => $userId,
            ':client_uuid' => $clientUuid,
            ':type' => (int) ($dto['type'] ?? 0),
            ':title' => $title,
            ':note' => isset($dto['note']) ? (string) $dto['note'] : null,
            ':trigger_time' => Time::toDb(Time::parseIso(isset($dto['triggerTime']) ? (string) $dto['triggerTime'] : null)),
            ':duration_sec' => isset($dto['durationSec']) ? (int) $dto['durationSec'] : null,
            ':repeat_weekdays' => (int) ($dto['repeatWeekdays'] ?? 0),
            ':repeat_month_days' => (int) ($dto['repeatMonthDays'] ?? 0),
            ':anniversary_calendar' => (int) ($dto['anniversaryCalendar'] ?? 0),
            ':advance_notify_days' => $this->limitText(
                isset($dto['advanceNotifyDays']) ? (string) $dto['advanceNotifyDays'] : null,
                128,
                true
            ),
            ':ringtone' => $this->limitText(
                isset($dto['ringtone']) ? (string) $dto['ringtone'] : null,
                255,
                true
            ),
            ':vibrate' => !empty($dto['vibrate']) ? 1 : 0,
            ':volume_fade' => !empty($dto['volumeFade']) ? 1 : 0,
            ':snooze_minutes' => (int) ($dto['snoozeMinutes'] ?? 5),
            ':label' => $this->limitText(
                isset($dto['label']) ? (string) $dto['label'] : null,
                128,
                true
            ),
            ':color' => $this->limitText(
                isset($dto['color']) ? (string) $dto['color'] : null,
                32,
                true
            ),
            ':enabled' => !empty($dto['enabled']) ? 1 : 0,
            ':start_date' => Time::toDb(Time::parseIso(isset($dto['startDate']) ? (string) $dto['startDate'] : null)),
            ':end_date' => Time::toDb(Time::parseIso(isset($dto['endDate']) ? (string) $dto['endDate'] : null)),
            ':schedule_mode' => (int) ($dto['scheduleMode'] ?? 0),
            ':alert_policy' => (int) ($dto['alertPolicy'] ?? 0),
            ':time_anchor_mode' => (int) ($dto['timeAnchorMode'] ?? 0),
            ':interval_months' => max(1, (int) ($dto['intervalMonths'] ?? 1)),
            ':interval_years' => max(1, (int) ($dto['intervalYears'] ?? 1)),
            ':next_override_mode' => (int) ($dto['nextOverrideMode'] ?? 0),
            ':next_override_anchor_date' => $this->limitText(
                isset($dto['nextOverrideAnchorDate']) ? (string) $dto['nextOverrideAnchorDate'] : null,
                32,
                true
            ),
            ':next_override_anchor_trigger_at' => Time::toDb(Time::parseIso(isset($dto['nextOverrideAnchorTriggerAt']) ? (string) $dto['nextOverrideAnchorTriggerAt'] : null)),
            ':next_override_trigger_at' => Time::toDb(Time::parseIso(isset($dto['nextOverrideTriggerAt']) ? (string) $dto['nextOverrideTriggerAt'] : null)),
            ':template_id' => $this->limitText(
                isset($dto['templateId']) ? (string) $dto['templateId'] : null,
                64,
                true
            ),
            ':status' => (int) ($dto['status'] ?? 0),
            ':created_at' => Time::toDb($createdAt),
            ':updated_at' => Time::toDb($now),
        ];

        if (!$existing) {
            $stmt = $this->pdo->prepare(
                'INSERT INTO alarms
                (user_id, client_uuid, type, title, note, trigger_time, duration_sec, repeat_weekdays,
                 repeat_month_days, anniversary_calendar, advance_notify_days, ringtone, vibrate,
                 volume_fade, snooze_minutes, label, color, enabled, start_date, end_date,
                 schedule_mode, alert_policy, time_anchor_mode, interval_months, interval_years,
                 next_override_mode, next_override_anchor_date, next_override_anchor_trigger_at, next_override_trigger_at,
                 template_id, status, created_at, updated_at)
                 VALUES
                (:user_id, :client_uuid, :type, :title, :note, :trigger_time, :duration_sec, :repeat_weekdays,
                 :repeat_month_days, :anniversary_calendar, :advance_notify_days, :ringtone, :vibrate,
                 :volume_fade, :snooze_minutes, :label, :color, :enabled, :start_date, :end_date,
                 :schedule_mode, :alert_policy, :time_anchor_mode, :interval_months, :interval_years,
                 :next_override_mode, :next_override_anchor_date, :next_override_anchor_trigger_at, :next_override_trigger_at,
                 :template_id, :status, :created_at, :updated_at)'
            );
            $stmt->execute($fields);
            return $this->mapAlarm($this->getAlarmById((int) $this->pdo->lastInsertId()));
        }

        $stmt = $this->pdo->prepare(
            'UPDATE alarms SET
              type = :type, title = :title, note = :note, trigger_time = :trigger_time, duration_sec = :duration_sec,
              repeat_weekdays = :repeat_weekdays, repeat_month_days = :repeat_month_days, anniversary_calendar = :anniversary_calendar,
              advance_notify_days = :advance_notify_days, ringtone = :ringtone, vibrate = :vibrate, volume_fade = :volume_fade,
              snooze_minutes = :snooze_minutes, label = :label, color = :color, enabled = :enabled,
              start_date = :start_date, end_date = :end_date, schedule_mode = :schedule_mode, alert_policy = :alert_policy,
              time_anchor_mode = :time_anchor_mode, interval_months = :interval_months, interval_years = :interval_years,
              next_override_mode = :next_override_mode, next_override_anchor_date = :next_override_anchor_date,
              next_override_anchor_trigger_at = :next_override_anchor_trigger_at, next_override_trigger_at = :next_override_trigger_at,
              template_id = :template_id, status = :status, updated_at = :updated_at
             WHERE id = :id AND user_id = :user_id'
        );
        $stmt->execute($fields + [':id' => (int) $existing['id']]);

        return $this->mapAlarm($this->getAlarmById((int) $existing['id']));
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
