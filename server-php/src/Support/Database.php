<?php

declare(strict_types=1);

namespace SmartClock\Server\Support;

use PDO;

final class Database
{
    public static function connect(Config $config): PDO
    {
        return new PDO(
            $config->dsn(),
            $config->dbUsername,
            $config->dbPassword,
            [
                PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                PDO::ATTR_EMULATE_PREPARES => false,
            ]
        );
    }
}
