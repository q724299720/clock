export type LoginPayload = {
  account: string;
  isEmail: boolean;
  password: string;
};

export type PageParams = {
  page: number;
  pageSize: number;
};

export type PageResponse<T> = {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
};

type AuthUser = {
  id: number;
  role: string;
  nickname?: string | null;
};

type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
  user: AuthUser;
};

type ApiErrorResponse = {
  code?: string;
  message?: string;
};

const ACCESS_TOKEN_KEY = "smartclock_admin_access_token";
const REFRESH_TOKEN_KEY = "smartclock_admin_refresh_token";

let refreshPromise: Promise<string> | null = null;

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function hasStoredSession(): boolean {
  return Boolean(getAccessToken() || getRefreshToken());
}

export function setSessionTokens(accessToken: string | null, refreshToken: string | null) {
  if (accessToken) {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  } else {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
  }

  if (refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  } else {
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  }
}

export function clearSession() {
  setSessionTokens(null, null);
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  authenticated = true,
  allowRefresh = true
): Promise<T> {
  const token = authenticated ? getAccessToken() : null;
  const headers = new Headers(init.headers);
  headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(path, { ...init, headers });
  if (!response.ok) {
    if (response.status === 401 && authenticated && allowRefresh) {
      await refreshSession();
      return request<T>(path, init, authenticated, false);
    }

    const body = await response.json().catch(() => ({ message: response.statusText }));
    throw new Error((body as ApiErrorResponse).message || response.statusText);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

async function refreshSession(): Promise<string> {
  if (refreshPromise) {
    return refreshPromise;
  }

  refreshPromise = (async () => {
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
      clearSession();
      throw new Error("Session expired, please sign in again.");
    }

    const response = await fetch("/api/v1/auth/refresh", {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ refreshToken })
    });

    const body = await response.json().catch(() => null);
    if (!response.ok) {
      clearSession();
      throw new Error((body as ApiErrorResponse | null)?.message || "Session expired, please sign in again.");
    }

    const authResponse = body as AuthResponse | null;
    if (!authResponse?.accessToken || !authResponse?.refreshToken) {
      clearSession();
      throw new Error("Session expired, please sign in again.");
    }

    setSessionTokens(authResponse.accessToken, authResponse.refreshToken);
    return authResponse.accessToken;
  })();

  try {
    return await refreshPromise;
  } finally {
    refreshPromise = null;
  }
}

function withPaging(params: URLSearchParams, paging: PageParams) {
  params.set("page", String(paging.page));
  params.set("pageSize", String(paging.pageSize));
}

function normalizePageResponse<T>(payload: unknown, paging: PageParams): PageResponse<T> {
  if (Array.isArray(payload)) {
    const total = payload.length;
    const start = Math.max((paging.page - 1) * paging.pageSize, 0);
    const end = start + paging.pageSize;
    return {
      items: payload.slice(start, end) as T[],
      total,
      page: paging.page,
      pageSize: paging.pageSize
    };
  }

  if (payload && typeof payload === "object") {
    const data = payload as Partial<PageResponse<T>>;
    if (Array.isArray(data.items)) {
      return {
        items: data.items,
        total: typeof data.total === "number" ? data.total : data.items.length,
        page: typeof data.page === "number" ? data.page : paging.page,
        pageSize: typeof data.pageSize === "number" ? data.pageSize : paging.pageSize
      };
    }
  }

  return {
    items: [],
    total: 0,
    page: paging.page,
    pageSize: paging.pageSize
  };
}

export async function login(payload: LoginPayload) {
  return request<{
    accessToken: string;
    refreshToken: string;
    refreshTokenExpiresAt: string;
    user: { id: number; role: string; nickname?: string | null };
  }>(
    "/api/v1/auth/login",
    {
      method: "POST",
      body: JSON.stringify(payload)
    },
    false
  );
}

export async function fetchUsers(q: string, paging: PageParams) {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  withPaging(params, paging);
  const payload = await request<unknown>(`/api/v1/admin/users?${params.toString()}`);
  return normalizePageResponse<any>(payload, paging);
}

export async function updateUserStatus(id: number, status: number) {
  return request(`/api/v1/admin/users/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status })
  });
}

export async function fetchAlarms(q: string, paging: PageParams, userId?: number) {
  const params = new URLSearchParams();
  if (userId) params.set("userId", String(userId));
  if (q) params.set("q", q);
  withPaging(params, paging);
  const payload = await request<unknown>(`/api/v1/admin/alarms?${params.toString()}`);
  return normalizePageResponse<any>(payload, paging);
}

export async function deleteAlarm(id: number) {
  return request(`/api/v1/admin/alarms/${id}`, { method: "DELETE" });
}

export async function fetchAlarmLogs(paging: PageParams, userId?: number) {
  const params = new URLSearchParams();
  if (userId) params.set("userId", String(userId));
  withPaging(params, paging);
  const payload = await request<unknown>(`/api/v1/admin/alarm-logs?${params.toString()}`);
  return normalizePageResponse<any>(payload, paging);
}

export async function fetchAuditLogs(paging: PageParams) {
  const params = new URLSearchParams();
  withPaging(params, paging);
  const payload = await request<unknown>(`/api/v1/admin/audit-logs?${params.toString()}`);
  return normalizePageResponse<any>(payload, paging);
}
