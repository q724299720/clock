<?php

declare(strict_types=1);

namespace SmartClock\Server\Support;

use RuntimeException;

final class ApiException extends RuntimeException
{
    public function __construct(
        public readonly int $status,
        string $message,
        public readonly ?string $errorCode = null
    ) {
        parent::__construct($message);
    }
}
