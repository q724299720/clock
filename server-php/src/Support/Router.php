<?php

declare(strict_types=1);

namespace SmartClock\Server\Support;

final class Router
{
    /** @var array<int, array{method:string,pattern:string,regex:string,handler:callable,role:?string}> */
    private array $routes = [];

    public function add(string $method, string $pattern, callable $handler, ?string $role = null): void
    {
        $regex = '#^' . preg_replace('#\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}#', '(?P<$1>[^/]+)', $pattern) . '$#';
        $this->routes[] = [
            'method' => strtoupper($method),
            'pattern' => $pattern,
            'regex' => $regex,
            'handler' => $handler,
            'role' => $role,
        ];
    }

    public function dispatch(Request $request, callable $authResolver): mixed
    {
        foreach ($this->routes as $route) {
            if ($route['method'] !== $request->method) {
                continue;
            }
            if (!preg_match($route['regex'], $request->path, $matches)) {
                continue;
            }

            $context = null;
            if ($route['role'] !== null) {
                $context = $authResolver($route['role']);
            }

            $params = [];
            foreach ($matches as $key => $value) {
                if (!is_string($key)) {
                    continue;
                }
                $params[] = $value;
            }

            return ($route['handler'])($request, $context, ...$params);
        }

        throw new ApiException(404, 'route not found', 'not_found');
    }
}
