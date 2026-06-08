<?php

declare(strict_types=1);

namespace SmartClock\Server\Support;

final class Request
{
    private ?array $jsonBody = null;

    public function __construct(
        public readonly string $method,
        public readonly string $path,
        public readonly array $query,
        public readonly array $headers,
        public readonly string $rawBody,
        public readonly string $clientIp
    ) {
    }

    public static function fromGlobals(): self
    {
        $path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
        $headers = function_exists('getallheaders') ? getallheaders() : [];
        $clientIp = $headers['X-Real-IP'] ?? ($_SERVER['REMOTE_ADDR'] ?? '127.0.0.1');

        return new self(
            method: strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET'),
            path: $path,
            query: $_GET,
            headers: array_change_key_case($headers, CASE_LOWER),
            rawBody: file_get_contents('php://input') ?: '',
            clientIp: $clientIp,
        );
    }

    public function json(): array
    {
        if ($this->jsonBody !== null) {
            return $this->jsonBody;
        }
        if ($this->rawBody === '') {
            $this->jsonBody = [];
            return $this->jsonBody;
        }

        $decoded = json_decode($this->rawBody, true);
        if (!is_array($decoded)) {
            throw new ApiException(400, 'invalid json body', 'invalid_json');
        }

        $this->jsonBody = $decoded;
        return $this->jsonBody;
    }

    public function bearerToken(): ?string
    {
        $header = $this->headers['authorization'] ?? null;
        if (!$header || !preg_match('/^Bearer\\s+(.+)$/i', $header, $matches)) {
            return null;
        }

        return trim($matches[1]);
    }
}
