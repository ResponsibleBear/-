import type { AppRuntimeConfig, DeviceAction, YandexDevice, YandexProfile, YandexUserInfo } from '../types/smartHome';

let inMemorySessionToken: string | null = null;

export function setSessionToken(token: string | null): void {
  inMemorySessionToken = token;
}

export function getSessionToken(): string | null {
  return inMemorySessionToken;
}

async function apiFetch<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers || {});
  headers.set('X-Requested-With', 'XMLHttpRequest');
  if (inMemorySessionToken) {
    headers.set('Authorization', `Bearer ${inMemorySessionToken}`);
  }
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const res = await fetch(url, {
    ...options,
    headers,
    credentials: 'include',
  });

  const text = await res.text();
  let data: any = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = { error: text };
    }
  }

  if (!res.ok) {
    const err = new Error(data?.error || data?.message || `Ошибка сервера (${res.status})`) as Error & {
      status?: number;
      setupRequired?: boolean;
      callbackUrl?: string;
    };
    err.status = res.status;
    err.setupRequired = data?.setupRequired;
    err.callbackUrl = data?.callbackUrl;
    throw err;
  }

  return data as T;
}

export function fetchRuntimeConfig(): Promise<AppRuntimeConfig> {
  return apiFetch<AppRuntimeConfig>('/api/config');
}

export function fetchAuthStatus(): Promise<{
  authenticated: boolean;
  profile?: YandexProfile;
  sessionToken?: string;
}> {
  return apiFetch('/api/auth/status');
}

export function fetchYandexOAuthUrl(mode: 'callback' | 'verification_code' = 'callback'): Promise<{ url: string }> {
  return apiFetch(`/api/auth/yandex/url?mode=${encodeURIComponent(mode)}`);
}

export function exchangeOAuthCode(exchangeCode: string): Promise<{
  sessionToken: string;
  profile: YandexProfile;
}> {
  return apiFetch('/api/auth/exchange', {
    method: 'POST',
    body: JSON.stringify({ exchangeCode }),
  });
}

export function exchangeVerificationCode(code: string): Promise<{
  sessionToken: string;
  profile: YandexProfile;
}> {
  return apiFetch('/api/auth/yandex/code', {
    method: 'POST',
    body: JSON.stringify({ code }),
  });
}

export function connectWithYandexToken(token: string): Promise<{
  sessionToken: string;
  profile: YandexProfile;
}> {
  return apiFetch('/api/auth/token', {
    method: 'POST',
    body: JSON.stringify({ token }),
  });
}

export function logoutYandex(): Promise<{ ok: boolean }> {
  return apiFetch('/api/auth/logout', { method: 'POST' });
}

export function fetchSmartHomeInfo(): Promise<YandexUserInfo> {
  return apiFetch<YandexUserInfo>('/api/smart-home/info');
}

export function fetchDeviceState(deviceId: string): Promise<YandexDevice & { status: string }> {
  return apiFetch(`/api/smart-home/devices/${encodeURIComponent(deviceId)}`);
}

export function sendDeviceActions(
  deviceId: string,
  actions: DeviceAction[]
): Promise<{ status: string; devices?: Array<{ id: string; capabilities: Array<{ type: string; state: { instance: string; action_result?: { status: string; error_message?: string } } }> }> }> {
  return apiFetch('/api/smart-home/devices/actions', {
    method: 'POST',
    body: JSON.stringify({
      devices: [{ id: deviceId, actions }],
    }),
  });
}

export function sendBatchDeviceActions(
  deviceIds: string[],
  actions: DeviceAction[]
): Promise<{ status: string }> {
  return apiFetch('/api/smart-home/devices/actions', {
    method: 'POST',
    body: JSON.stringify({
      devices: deviceIds.map((id) => ({ id, actions })),
    }),
  });
}

export function sendGroupActions(groupId: string, actions: DeviceAction[]): Promise<{ status: string }> {
  return apiFetch(`/api/smart-home/groups/${encodeURIComponent(groupId)}/actions`, {
    method: 'POST',
    body: JSON.stringify({ actions }),
  });
}

export function runYandexScenario(scenarioId: string): Promise<{ status: string }> {
  return apiFetch(`/api/smart-home/scenarios/${encodeURIComponent(scenarioId)}/actions`, {
    method: 'POST',
  });
}
