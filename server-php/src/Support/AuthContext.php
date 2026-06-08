<?php

declare(strict_types=1);

namespace SmartClock\Server\Support;

final class AuthContext
{
    public function __construct(
        public readonly int $userId,
        public readonly string $role
    ) {
    }
}
