<?php

declare(strict_types=1);

namespace SmartClock\Server\Support;

use DateTimeImmutable;
use DateTimeInterface;
use DateTimeZone;

final class Time
{
    private static ?DateTimeZone $utc = null;

    public static function utc(): DateTimeZone
    {
        return self::$utc ??= new DateTimeZone('UTC');
    }

    public static function now(): DateTimeImmutable
    {
        return new DateTimeImmutable('now', self::utc());
    }

    public static function parseIso(?string $value): ?DateTimeImmutable
    {
        if ($value === null || $value === '') {
            return null;
        }

        return (new DateTimeImmutable($value))->setTimezone(self::utc());
    }

    public static function parseDb(?string $value): ?DateTimeImmutable
    {
        if ($value === null || $value === '') {
            return null;
        }

        return new DateTimeImmutable($value, self::utc());
    }

    public static function toIso(?DateTimeInterface $value): ?string
    {
        if ($value === null) {
            return null;
        }

        $utc = DateTimeImmutable::createFromInterface($value)->setTimezone(self::utc());
        return substr($utc->format('Y-m-d\\TH:i:s.u'), 0, 23) . 'Z';
    }

    public static function toDb(?DateTimeInterface $value): ?string
    {
        if ($value === null) {
            return null;
        }

        return DateTimeImmutable::createFromInterface($value)
            ->setTimezone(self::utc())
            ->format('Y-m-d H:i:s.u');
    }
}
