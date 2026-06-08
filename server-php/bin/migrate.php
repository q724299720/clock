<?php

declare(strict_types=1);

use SmartClock\Server\Support\Config;
use SmartClock\Server\Support\Database;

require dirname(__DIR__) . '/bootstrap.php';

$root = dirname(__DIR__);

$config = Config::fromEnv();
$pdo = Database::connect($config);
$schema = file_get_contents($root . '/database/schema.sql');

if ($schema === false) {
    fwrite(STDERR, "Unable to read schema.sql\n");
    exit(1);
}

$pdo->exec($schema);
fwrite(STDOUT, "Schema migrated successfully.\n");
