<?php

declare(strict_types=1);

namespace SmartClock\Server\Support;

final class JwtService
{
    public function __construct(
        private readonly Config $config
    ) {
    }

    public function createAccessToken(int $userId, string $role): array
    {
        $now = Time::now();
        $expiresAt = $now->modify(sprintf('+%d minutes', $this->config->jwtAccessTokenMinutes));
        $header = ['alg' => 'HS256', 'typ' => 'JWT'];
        $payload = [
            'iss' => $this->config->jwtIssuer,
            'sub' => (string) $userId,
            'role' => $role,
            'iat' => $now->getTimestamp(),
            'exp' => $expiresAt->getTimestamp(),
        ];

        $encodedHeader = $this->base64UrlEncode(json_encode($header, JSON_UNESCAPED_SLASHES));
        $encodedPayload = $this->base64UrlEncode(json_encode($payload, JSON_UNESCAPED_SLASHES));
        $signature = hash_hmac('sha256', $encodedHeader . '.' . $encodedPayload, $this->config->jwtSecret, true);
        $token = $encodedHeader . '.' . $encodedPayload . '.' . $this->base64UrlEncode($signature);

        return [
            'token' => $token,
            'expiresAt' => $expiresAt,
        ];
    }

    public function parse(string $token): AuthContext
    {
        $parts = explode('.', $token);
        if (count($parts) !== 3) {
            throw new ApiException(401, 'invalid access token', 'invalid_token');
        }
        [$encodedHeader, $encodedPayload, $encodedSignature] = $parts;
        $expectedSignature = $this->base64UrlEncode(
            hash_hmac('sha256', $encodedHeader . '.' . $encodedPayload, $this->config->jwtSecret, true)
        );
        if (!hash_equals($expectedSignature, $encodedSignature)) {
            throw new ApiException(401, 'invalid access token', 'invalid_token');
        }
        $payload = json_decode((string) $this->base64UrlDecode($encodedPayload), true);
        if (!is_array($payload)) {
            throw new ApiException(401, 'invalid access token', 'invalid_token');
        }
        if (($payload['iss'] ?? null) !== $this->config->jwtIssuer) {
            throw new ApiException(401, 'invalid access token', 'invalid_token');
        }
        if (($payload['exp'] ?? 0) < time()) {
            throw new ApiException(401, 'access token expired', 'token_expired');
        }

        return new AuthContext(
            userId: (int) ($payload['sub'] ?? 0),
            role: (string) ($payload['role'] ?? 'USER'),
        );
    }

    private function base64UrlEncode(string $value): string
    {
        return rtrim(strtr(base64_encode($value), '+/', '-_'), '=');
    }

    private function base64UrlDecode(string $value): string
    {
        $padding = strlen($value) % 4;
        if ($padding > 0) {
            $value .= str_repeat('=', 4 - $padding);
        }

        return (string) base64_decode(strtr($value, '-_', '+/'));
    }
}
