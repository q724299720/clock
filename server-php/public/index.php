<?php

declare(strict_types=1);

use SmartClock\Server\Service\AdminService;
use SmartClock\Server\Service\AuthService;
use SmartClock\Server\Service\SyncService;
use SmartClock\Server\Support\ApiException;
use SmartClock\Server\Support\Config;
use SmartClock\Server\Support\Database;
use SmartClock\Server\Support\JsonResponse;
use SmartClock\Server\Support\Request;
use SmartClock\Server\Support\Router;
use SmartClock\Server\Support\JwtService;

require dirname(__DIR__) . '/bootstrap.php';

$request = Request::fromGlobals();

if (PHP_SAPI === 'cli-server') {
    $staticFile = __DIR__ . $request->path;
    if ($request->path !== '/' && is_file($staticFile)) {
        return false;
    }
}

if ($request->path === '/actuator/health') {
    JsonResponse::send(['status' => 'UP']);
}

if ($request->method === 'GET' && !str_starts_with($request->path, '/api/') && $request->path !== '/actuator/health') {
    $indexFile = __DIR__ . '/index.html';
    if (is_file($indexFile)) {
        header('Content-Type: text/html; charset=utf-8');
        readfile($indexFile);
        exit;
    }
}

try {
    $config = Config::fromEnv();
    $pdo = Database::connect($config);
    $jwtService = new JwtService($config);
    $authService = new AuthService($pdo, $config, $jwtService);
    $syncService = new SyncService($pdo);
    $adminService = new AdminService($pdo, $syncService);

    $authService->ensureAdminBootstrap();

    $router = new Router();
    $authResolver = static function (string $requiredRole) use ($request, $jwtService) {
        $token = $request->bearerToken();
        if ($token === null) {
            throw new ApiException(401, 'missing bearer token', 'unauthorized');
        }
        $context = $jwtService->parse($token);
        if ($requiredRole === 'ADMIN' && $context->role !== 'ADMIN') {
            throw new ApiException(403, 'admin only', 'forbidden');
        }
        return $context;
    };

    $router->add('POST', '/api/v1/auth/register', static fn (Request $req) => $authService->register($req->json()));
    $router->add('POST', '/api/v1/auth/login', static fn (Request $req) => $authService->login($req->json()));
    $router->add('POST', '/api/v1/auth/refresh', static fn (Request $req) => $authService->refresh($req->json()));
    $router->add('POST', '/api/v1/auth/logout', static fn (Request $req, $ctx) => $authService->logout($ctx, $req->json()), 'USER');
    $router->add('GET', '/api/v1/me', static fn (Request $req, $ctx) => $authService->me($ctx), 'USER');

    $router->add('GET', '/api/v1/sync/bootstrap', static fn (Request $req, $ctx) => $syncService->bootstrap($authService->meEntity($ctx->userId)), 'USER');
    $router->add('POST', '/api/v1/sync/alarms/push', static fn (Request $req, $ctx) => $syncService->pushAlarms($ctx->userId, $req->json()), 'USER');
    $router->add('GET', '/api/v1/sync/alarms/pull', static fn (Request $req, $ctx) => $syncService->pullAlarms($ctx->userId, $req->query['since'] ?? null), 'USER');
    $router->add('POST', '/api/v1/sync/alarm-logs/batch', static fn (Request $req, $ctx) => $syncService->uploadLogs($ctx->userId, $req->json()), 'USER');

    $router->add('GET', '/api/v1/admin/users', static fn (Request $req) => $adminService->listUsers($req->query['q'] ?? null, (int) ($req->query['limit'] ?? 100)), 'ADMIN');
    $router->add('GET', '/api/v1/admin/users/{id}', static fn (Request $req, $ctx, string $id) => $adminService->getUser((int) $id), 'ADMIN');
    $router->add('PATCH', '/api/v1/admin/users/{id}/status', static fn (Request $req, $ctx, string $id) => $adminService->updateUserStatus($ctx->userId, (int) $id, $req->json(), $req->clientIp), 'ADMIN');
    $router->add('GET', '/api/v1/admin/alarms', static fn (Request $req) => $adminService->listAlarms(isset($req->query['userId']) ? (int) $req->query['userId'] : null, $req->query['q'] ?? null, (int) ($req->query['limit'] ?? 100)), 'ADMIN');
    $router->add('GET', '/api/v1/admin/alarms/{id}', static fn (Request $req, $ctx, string $id) => $adminService->getAlarm((int) $id), 'ADMIN');
    $router->add('PATCH', '/api/v1/admin/alarms/{id}', static fn (Request $req, $ctx, string $id) => $adminService->updateAlarm($ctx->userId, (int) $id, $req->json(), $req->clientIp), 'ADMIN');
    $router->add('DELETE', '/api/v1/admin/alarms/{id}', static fn (Request $req, $ctx, string $id) => $adminService->softDeleteAlarm($ctx->userId, (int) $id, $req->clientIp), 'ADMIN');
    $router->add('GET', '/api/v1/admin/alarm-logs', static fn (Request $req) => $adminService->listAlarmLogs(isset($req->query['userId']) ? (int) $req->query['userId'] : null, (int) ($req->query['limit'] ?? 100)), 'ADMIN');
    $router->add('GET', '/api/v1/admin/audit-logs', static fn (Request $req) => $adminService->listAuditLogs((int) ($req->query['limit'] ?? 100)), 'ADMIN');

    $result = $router->dispatch($request, $authResolver);
    JsonResponse::send($result);
} catch (ApiException $e) {
    JsonResponse::send([
        'code' => $e->errorCode,
        'message' => $e->getMessage(),
    ], $e->status);
} catch (Throwable $e) {
    error_log(sprintf(
        '[SmartClock PHP] %s in %s:%d%s',
        $e->getMessage(),
        $e->getFile(),
        $e->getLine(),
        PHP_EOL . $e->getTraceAsString()
    ));
    JsonResponse::send([
        'code' => 'server_error',
        'message' => 'internal server error',
    ], 500);
}
