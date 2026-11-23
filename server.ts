import crypto from 'crypto';
import dotenv from 'dotenv';
import express from 'express';
import type { Request, Response, NextFunction } from 'express';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import zlib from 'zlib';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = 3000;
const YANDEX_IOT_BASE = 'https://api.iot.yandex.net/v1.0';
const YANDEX_OAUTH_AUTHORIZE = 'https://oauth.yandex.ru/authorize';
const YANDEX_OAUTH_TOKEN = 'https://oauth.yandex.ru/token';
const SESSION_COOKIE_NAME = 'alice_android_session';
const DATA_DIR = path.resolve(__dirname, '.data');
const STORE_FILE = path.join(DATA_DIR, 'store.json');

interface PersistedStore {
  signingKey?: string;
  encryptedSessions?: Record<string, string>;
}

function readPersistedStore(): PersistedStore {
  try {
    if (!fs.existsSync(STORE_FILE)) return {};
    const raw = fs.readFileSync(STORE_FILE, 'utf8');
    return JSON.parse(raw) as PersistedStore;
  } catch {
    return {};
  }
}

function deriveEncryptionKey(secret: string): Buffer {
  return crypto.createHash('sha256').update(secret).digest();
}

function encryptSessionRecord(record: SessionRecord, key: Buffer): string {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const plaintext = JSON.stringify(record);
  const encrypted = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return `${iv.toString('base64')}.${tag.toString('base64')}.${encrypted.toString('base64')}`;
}

function decryptSessionRecord(payload: string, key: Buffer): SessionRecord | null {
  try {
    const parts = payload.split('.');
    if (parts.length !== 3) return null;
    const iv = Buffer.from(parts[0], 'base64');
    const tag = Buffer.from(parts[1], 'base64');
    const encrypted = Buffer.from(parts[2], 'base64');
    const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
    decipher.setAuthTag(tag);
    const decrypted = Buffer.concat([decipher.update(encrypted), decipher.final()]).toString('utf8');
    return JSON.parse(decrypted) as SessionRecord;
  } catch {
    return null;
  }
}

const initialStore = readPersistedStore();
const SESSION_SIGNING_KEY = process.env.SESSION_SECRET || initialStore.signingKey || crypto.randomBytes(32).toString('hex');
const STORE_ENC_KEY = deriveEncryptionKey(SESSION_SIGNING_KEY);

interface YandexProfile {
  displayName: string;
  login?: string;
  avatarUrl?: string;
  authMethod: 'oauth' | 'token';
}

interface SessionRecord {
  id: string;
  accessToken: string;
  refreshToken?: string;
  expiresAt?: number;
  profile: YandexProfile;
  createdAt: number;
}

interface ExchangeCodeRecord {
  sessionId: string;
  expiresAt: number;
}

interface AuthenticatedRequest extends Request {
  session?: SessionRecord;
  authSource?: 'bearer' | 'cookie';
}

const sessions = new Map<string, SessionRecord>();
if (initialStore.encryptedSessions) {
  for (const [id, enc] of Object.entries(initialStore.encryptedSessions)) {
    const dec = decryptSessionRecord(enc, STORE_ENC_KEY);
    if (dec) sessions.set(id, dec);
  }
}

function persistSessions(): void {
  try {
    if (!fs.existsSync(DATA_DIR)) {
      fs.mkdirSync(DATA_DIR, { recursive: true, mode: 0o700 });
    }
    const encryptedSessions: Record<string, string> = {};
    for (const [id, rec] of sessions.entries()) {
      encryptedSessions[id] = encryptSessionRecord(rec, STORE_ENC_KEY);
    }
    const payload: PersistedStore = {
      signingKey: process.env.SESSION_SECRET ? undefined : SESSION_SIGNING_KEY,
      encryptedSessions,
    };
    fs.writeFileSync(STORE_FILE, JSON.stringify(payload), { encoding: 'utf8', mode: 0o600 });
  } catch {
    // Non-fatal if filesystem is read-only
  }
}

const exchangeCodes = new Map<string, ExchangeCodeRecord>();
const oauthStates = new Map<string, { expiresAt: number }>();

function getAppUrl(): string {
  const raw = process.env.APP_URL || `http://localhost:${PORT}`;
  return raw.replace(/\/+$/, '');
}

function getCallbackUrl(): string {
  return `${getAppUrl()}/auth/yandex/callback`;
}

function signSessionId(sessionId: string): string {
  const sig = crypto.createHmac('sha256', SESSION_SIGNING_KEY).update(sessionId).digest('hex');
  return `${sessionId}.${sig}`;
}

function verifySignedSessionToken(token: string): string | null {
  const dotIdx = token.lastIndexOf('.');
  if (dotIdx <= 0) return null;
  const sessionId = token.slice(0, dotIdx);
  const sig = token.slice(dotIdx + 1);
  if (!sessionId || !sig) return null;

  const expectedSig = crypto.createHmac('sha256', SESSION_SIGNING_KEY).update(sessionId).digest('hex');
  const sigBuf = Buffer.from(sig, 'utf8');
  const expectedBuf = Buffer.from(expectedSig, 'utf8');
  if (sigBuf.length !== expectedBuf.length) return null;
  if (!crypto.timingSafeEqual(sigBuf, expectedBuf)) return null;
  return sessionId;
}

function parseCookies(cookieHeader: string | undefined): Record<string, string> {
  const result: Record<string, string> = {};
  if (!cookieHeader) return result;
  for (const part of cookieHeader.split(';')) {
    const idx = part.indexOf('=');
    if (idx === -1) continue;
    const key = part.slice(0, idx).trim();
    const val = part.slice(idx + 1).trim();
    if (key) {
      try {
        result[key] = decodeURIComponent(val);
      } catch {
        result[key] = val;
      }
    }
  }
  return result;
}

function setSessionCookie(res: Response, signedToken: string): void {
  const encoded = encodeURIComponent(signedToken);
  const cookieParts = [
    `${SESSION_COOKIE_NAME}=${encoded}`,
    'Path=/',
    'HttpOnly',
    'Secure',
    'SameSite=None',
    'Partitioned',
    `Max-Age=${30 * 24 * 60 * 60}`,
  ];
  res.setHeader('Set-Cookie', cookieParts.join('; '));
}

function clearSessionCookie(res: Response): void {
  const cookieParts = [
    `${SESSION_COOKIE_NAME}=`,
    'Path=/',
    'HttpOnly',
    'Secure',
    'SameSite=None',
    'Partitioned',
    'Max-Age=0',
  ];
  res.setHeader('Set-Cookie', cookieParts.join('; '));
}

function isValidResourceId(id: string): boolean {
  return /^[a-zA-Z0-9._:-]{1,128}$/.test(id);
}

function cleanupExpiredMaps(): void {
  const now = Date.now();
  for (const [code, record] of exchangeCodes.entries()) {
    if (record.expiresAt < now) {
      exchangeCodes.delete(code);
    }
  }
  for (const [state, record] of oauthStates.entries()) {
    if (record.expiresAt < now) {
      oauthStates.delete(state);
    }
  }
}

setInterval(cleanupExpiredMaps, 60_000).unref();

async function fetchYandexProfile(accessToken: string, authMethod: 'oauth' | 'token'): Promise<YandexProfile> {
  try {
    const profileRes = await fetch('https://login.yandex.ru/info?format=json', {
      headers: { Authorization: `OAuth ${accessToken}` },
    });
    if (profileRes.ok) {
      const data = (await profileRes.json()) as {
        display_name?: string;
        real_name?: string;
        login?: string;
        default_avatar_id?: string;
        is_avatar_empty?: boolean;
      };
      const displayName = data.display_name || data.real_name || data.login || 'Яндекс ID';
      const avatarUrl =
        data.default_avatar_id && !data.is_avatar_empty
          ? `https://avatars.yandex.net/get-yapic/${encodeURIComponent(data.default_avatar_id)}/islands-middle`
          : undefined;
      return {
        displayName,
        login: data.login,
        avatarUrl,
        authMethod,
      };
    }
  } catch {
    // login:info scope is optional for Smart Home OAuth tokens
  }
  return {
    displayName: 'Яндекс Умный дом',
    authMethod,
  };
}

async function refreshSessionToken(session: SessionRecord): Promise<boolean> {
  const clientId = process.env.YANDEX_CLIENT_ID;
  const clientSecret = process.env.YANDEX_CLIENT_SECRET;
  if (!session.refreshToken || !clientId || !clientSecret) {
    return false;
  }

  try {
    const body = new URLSearchParams({
      grant_type: 'refresh_token',
      refresh_token: session.refreshToken,
      client_id: clientId,
      client_secret: clientSecret,
    });
    const res = await fetch(YANDEX_OAUTH_TOKEN, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString(),
    });
    if (!res.ok) return false;
    const data = (await res.json()) as {
      access_token?: string;
      refresh_token?: string;
      expires_in?: number;
    };
    if (!data.access_token) return false;
    session.accessToken = data.access_token;
    if (data.refresh_token) session.refreshToken = data.refresh_token;
    if (data.expires_in) session.expiresAt = Date.now() + data.expires_in * 1000;
    persistSessions();
    return true;
  } catch {
    return false;
  }
}

async function yandexIotRequest(
  session: SessionRecord,
  endpointPath: string,
  options: { method?: 'GET' | 'POST'; body?: unknown } = {}
): Promise<{ status: number; data: unknown }> {
  const doFetch = async (token: string) => {
    const headers: Record<string, string> = {
      Authorization: `Bearer ${token}`,
    };
    if (options.body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    return fetch(`${YANDEX_IOT_BASE}${endpointPath}`, {
      method: options.method || 'GET',
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    });
  };

  let res = await doFetch(session.accessToken);
  if (res.status === 401 && session.refreshToken) {
    const refreshed = await refreshSessionToken(session);
    if (refreshed) {
      res = await doFetch(session.accessToken);
    }
  }

  let data: unknown = null;
  const text = await res.text();
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = { message: text };
    }
  }

  return { status: res.status, data };
}

function resolveSession(req: AuthenticatedRequest, _res: Response, next: NextFunction): void {
  const authHeader = req.headers.authorization;
  let rawToken: string | undefined;
  let source: 'bearer' | 'cookie' | undefined;

  if (authHeader && authHeader.startsWith('Bearer ')) {
    rawToken = authHeader.slice(7).trim();
    source = 'bearer';
  } else {
    const cookies = parseCookies(req.headers.cookie);
    if (cookies[SESSION_COOKIE_NAME]) {
      rawToken = cookies[SESSION_COOKIE_NAME];
      source = 'cookie';
    }
  }

  if (rawToken) {
    const sessionId = verifySignedSessionToken(rawToken);
    if (sessionId) {
      const session = sessions.get(sessionId);
      if (session) {
        req.session = session;
        req.authSource = source;
      }
    }
  }
  next();
}

function requireAuth(req: AuthenticatedRequest, res: Response, next: NextFunction): void {
  if (!req.session) {
    res.status(401).json({ error: 'Требуется подключение к Яндекс Умному дому' });
    return;
  }

  // CSRF check for cookie-authenticated state-changing requests
  if (req.authSource === 'cookie' && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(req.method)) {
    const hasCustomHeader = req.headers['x-requested-with'] === 'XMLHttpRequest';
    const origin = req.headers.origin;
    const host = req.headers['x-forwarded-host'] || req.headers.host;
    let originMatchesHost = false;
    if (origin && host) {
      try {
        const originHost = new URL(origin).host;
        originMatchesHost = originHost === host;
      } catch {
        originMatchesHost = false;
      }
    }
    if (!hasCustomHeader && !originMatchesHost) {
      res.status(403).json({ error: 'CSRF check failed' });
      return;
    }
  }

  next();
}

async function startServer() {
  const app = express();
  app.set('trust proxy', 1);
  app.use(express.json({ limit: '256kb' }));
  app.use(resolveSession);

  // Public runtime configuration (no secrets exposed)
  app.get('/api/config', (_req: Request, res: Response) => {
    const yandexOAuthConfigured = Boolean(process.env.YANDEX_CLIENT_ID && process.env.YANDEX_CLIENT_SECRET);
    res.json({
      appUrl: getAppUrl(),
      callbackUrl: getCallbackUrl(),
      yandexOAuthConfigured,
    });
  });

  // Step 1 of Yandex OAuth popup flow: return Yandex OAuth authorize URL
  app.get('/api/auth/yandex/url', (req: Request, res: Response) => {
    const clientId = process.env.YANDEX_CLIENT_ID;
    const clientSecret = process.env.YANDEX_CLIENT_SECRET;
    if (!clientId || !clientSecret) {
      res.status(400).json({
        error: 'YANDEX_CLIENT_ID и YANDEX_CLIENT_SECRET не заданы в переменных окружения.',
        setupRequired: true,
        callbackUrl: getCallbackUrl(),
      });
      return;
    }

    const mode = req.query.mode === 'verification_code' ? 'verification_code' : 'callback';
    const state = crypto.randomBytes(24).toString('hex');
    oauthStates.set(state, { expiresAt: Date.now() + 10 * 60 * 1000 });

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: clientId,
      redirect_uri:
        mode === 'verification_code' ? 'https://oauth.yandex.ru/verification_code' : getCallbackUrl(),
      state,
      force_confirm: 'yes',
    });

    res.json({ url: `${YANDEX_OAUTH_AUTHORIZE}?${params.toString()}` });
  });

  // Step 2 of Yandex OAuth popup flow: callback from Yandex
  const handleYandexCallback = async (req: Request, res: Response) => {
    const code = typeof req.query.code === 'string' ? req.query.code : '';
    const state = typeof req.query.state === 'string' ? req.query.state : '';
    const oauthError = typeof req.query.error === 'string' ? req.query.error : '';

    const renderPopupResult = (payload: Record<string, unknown>, message: string, isError = false) => {
      const safePayload = JSON.stringify(payload).replace(/</g, '\\u003c');
      const safeMessage = message.replace(/</g, '&lt;').replace(/>/g, '&gt;');
      res.status(isError ? 400 : 200).send(`<!doctype html>
<html lang="ru">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Яндекс ID — Авторизация</title>
    <style>
      body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; background: #0e1117; color: #f1f5f9; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 24px; text-align: center; }
      .box { max-width: 360px; background: #171c26; border: 1px solid rgba(255,255,255,0.08); border-radius: 20px; padding: 28px; }
      p { margin: 0; font-size: 15px; line-height: 1.5; color: ${isError ? '#fca5a5' : '#cbd5e1'}; }
    </style>
  </head>
  <body>
    <div class="box">
      <p>${safeMessage}</p>
    </div>
    <script>
      (function() {
        var payload = ${safePayload};
        if (window.opener) {
          window.opener.postMessage(payload, '*');
          setTimeout(function() { window.close(); }, 350);
        } else if (!payload.error) {
          window.location.href = '/';
        }
      })();
    </script>
  </body>
</html>`);
    };

    if (oauthError) {
      renderPopupResult(
        { type: 'YANDEX_OAUTH_ERROR', error: `Яндекс отклонил авторизацию: ${oauthError}` },
        `Авторизация отменена (${oauthError}). Это окно можно закрыть.`,
        true
      );
      return;
    }

    const storedState = state ? oauthStates.get(state) : undefined;
    if (!storedState || storedState.expiresAt < Date.now()) {
      renderPopupResult(
        { type: 'YANDEX_OAUTH_ERROR', error: 'Истёк или неверен параметр state.' },
        'Сессия авторизации истекла. Закройте окно и попробуйте снова.',
        true
      );
      return;
    }
    oauthStates.delete(state);

    const clientId = process.env.YANDEX_CLIENT_ID;
    const clientSecret = process.env.YANDEX_CLIENT_SECRET;
    if (!code || !clientId || !clientSecret) {
      renderPopupResult(
        { type: 'YANDEX_OAUTH_ERROR', error: 'Отсутствует код авторизации или настройки OAuth.' },
        'Ошибка конфигурации OAuth на сервере.',
        true
      );
      return;
    }

    try {
      const tokenBody = new URLSearchParams({
        grant_type: 'authorization_code',
        code,
        client_id: clientId,
        client_secret: clientSecret,
      });
      const tokenRes = await fetch(YANDEX_OAUTH_TOKEN, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: tokenBody.toString(),
      });

      const tokenJson = (await tokenRes.json()) as {
        access_token?: string;
        refresh_token?: string;
        expires_in?: number;
        error_description?: string;
        error?: string;
      };

      if (!tokenRes.ok || !tokenJson.access_token) {
        const msg = tokenJson.error_description || tokenJson.error || 'Не удалось получить токен Яндекса';
        renderPopupResult({ type: 'YANDEX_OAUTH_ERROR', error: msg }, msg, true);
        return;
      }

      const profile = await fetchYandexProfile(tokenJson.access_token, 'oauth');
      const sessionId = crypto.randomBytes(24).toString('hex');
      const session: SessionRecord = {
        id: sessionId,
        accessToken: tokenJson.access_token,
        refreshToken: tokenJson.refresh_token,
        expiresAt: tokenJson.expires_in ? Date.now() + tokenJson.expires_in * 1000 : undefined,
        profile,
        createdAt: Date.now(),
      };
      sessions.set(sessionId, session);
      persistSessions();

      const signedToken = signSessionId(sessionId);
      setSessionCookie(res, signedToken);

      const exchangeCode = crypto.randomBytes(24).toString('hex');
      exchangeCodes.set(exchangeCode, {
        sessionId,
        expiresAt: Date.now() + 60_000,
      });

      renderPopupResult(
        { type: 'YANDEX_OAUTH_SUCCESS', exchangeCode },
        'Яндекс ID успешно подключён. Возвращаемся в приложение...'
      );
    } catch {
      renderPopupResult(
        { type: 'YANDEX_OAUTH_ERROR', error: 'Сетевая ошибка при обмене кода на токен' },
        'Не удалось связаться с сервером Яндекса.',
        true
      );
    }
  };

  app.get(['/auth/yandex/callback', '/auth/yandex/callback/'], handleYandexCallback);

  // Step 3: Exchange short-lived one-time code for Bearer session token in memory
  app.post('/api/auth/exchange', (req: Request, res: Response) => {
    const code = typeof req.body?.exchangeCode === 'string' ? req.body.exchangeCode : '';
    if (!code) {
      res.status(400).json({ error: 'Отсутствует exchangeCode' });
      return;
    }
    const record = exchangeCodes.get(code);
    exchangeCodes.delete(code);

    if (!record || record.expiresAt < Date.now()) {
      res.status(401).json({ error: 'Код обмена истёк' });
      return;
    }

    const session = sessions.get(record.sessionId);
    if (!session) {
      res.status(401).json({ error: 'Сессия не найдена' });
      return;
    }

    const sessionToken = signSessionId(session.id);
    setSessionCookie(res, sessionToken);
    res.json({ sessionToken, profile: session.profile });
  });

  // Exchange Yandex verification_code (from https://oauth.yandex.ru/verification_code) for session
  app.post('/api/auth/yandex/code', async (req: Request, res: Response) => {
    const code = typeof req.body?.code === 'string' ? req.body.code.trim() : '';
    const clientId = process.env.YANDEX_CLIENT_ID;
    const clientSecret = process.env.YANDEX_CLIENT_SECRET;

    if (!clientId || !clientSecret) {
      res.status(400).json({
        error: 'YANDEX_CLIENT_ID и YANDEX_CLIENT_SECRET не заданы на сервере.',
      });
      return;
    }

    if (!code || code.length < 4 || code.length > 128) {
      res.status(400).json({ error: 'Введите код подтверждения от Яндекса' });
      return;
    }

    try {
      const tokenBody = new URLSearchParams({
        grant_type: 'authorization_code',
        code,
        client_id: clientId,
        client_secret: clientSecret,
      });
      const tokenRes = await fetch(YANDEX_OAUTH_TOKEN, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: tokenBody.toString(),
      });

      const tokenJson = (await tokenRes.json()) as {
        access_token?: string;
        refresh_token?: string;
        expires_in?: number;
        error_description?: string;
        error?: string;
      };

      if (!tokenRes.ok || !tokenJson.access_token) {
        res.status(401).json({
          error: tokenJson.error_description || tokenJson.error || 'Неверный или просроченный код Яндекса',
        });
        return;
      }

      const verifyRes = await fetch(`${YANDEX_IOT_BASE}/user/info`, {
        headers: { Authorization: `Bearer ${tokenJson.access_token}` },
      });
      if (!verifyRes.ok) {
        res.status(403).json({
          error: 'У приложения нет доступа к Умному дому (нужны права iot:view и iot:control).',
        });
        return;
      }

      const profile = await fetchYandexProfile(tokenJson.access_token, 'oauth');
      const sessionId = crypto.randomBytes(24).toString('hex');
      const session: SessionRecord = {
        id: sessionId,
        accessToken: tokenJson.access_token,
        refreshToken: tokenJson.refresh_token,
        expiresAt: tokenJson.expires_in ? Date.now() + tokenJson.expires_in * 1000 : undefined,
        profile,
        createdAt: Date.now(),
      };
      sessions.set(sessionId, session);
      persistSessions();

      const sessionToken = signSessionId(sessionId);
      setSessionCookie(res, sessionToken);
      res.json({ sessionToken, profile });
    } catch {
      res.status(502).json({ error: 'Ошибка соединения с сервером Яндекса' });
    }
  });

  // Alternative direct Yandex OAuth token authentication (verified immediately against Yandex IoT API)
  app.post('/api/auth/token', async (req: Request, res: Response) => {
    const rawToken = typeof req.body?.token === 'string' ? req.body.token.trim() : '';
    if (!rawToken || rawToken.length < 16 || rawToken.length > 512) {
      res.status(400).json({ error: 'Введите корректный OAuth-токен Яндекса' });
      return;
    }

    try {
      const verifyRes = await fetch(`${YANDEX_IOT_BASE}/user/info`, {
        headers: { Authorization: `Bearer ${rawToken}` },
      });

      if (!verifyRes.ok) {
        if (verifyRes.status === 401 || verifyRes.status === 403) {
          res.status(401).json({
            error: 'Токен отклонён Яндексом. Убедитесь, что у токена есть права iot:view и iot:control.',
          });
          return;
        }
        res.status(502).json({ error: `Яндекс API вернул ошибку (${verifyRes.status})` });
        return;
      }

      const profile = await fetchYandexProfile(rawToken, 'token');
      const sessionId = crypto.randomBytes(24).toString('hex');
      const session: SessionRecord = {
        id: sessionId,
        accessToken: rawToken,
        profile,
        createdAt: Date.now(),
      };
      sessions.set(sessionId, session);
      persistSessions();

      const sessionToken = signSessionId(sessionId);
      setSessionCookie(res, sessionToken);
      res.json({ sessionToken, profile });
    } catch {
      res.status(502).json({ error: 'Не удалось подключиться к api.iot.yandex.net' });
    }
  });

  app.get('/api/auth/status', (req: AuthenticatedRequest, res: Response) => {
    if (!req.session) {
      res.json({ authenticated: false });
      return;
    }
    const sessionToken = signSessionId(req.session.id);
    res.json({
      authenticated: true,
      profile: req.session.profile,
      sessionToken,
    });
  });

  app.post('/api/auth/logout', (req: AuthenticatedRequest, res: Response) => {
    if (req.session) {
      sessions.delete(req.session.id);
      persistSessions();
    }
    clearSessionCookie(res);
    res.json({ ok: true });
  });

  // Yandex Smart Home Proxy Endpoints
  app.get('/api/smart-home/info', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    try {
      const result = await yandexIotRequest(req.session!, '/user/info');
      res.status(result.status).json(result.data);
    } catch {
      res.status(502).json({ error: 'Ошибка соединения с Яндекс Умным домом' });
    }
  });

  app.get('/api/smart-home/devices/:id', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    const id = String(req.params.id || '');
    if (!isValidResourceId(id)) {
      res.status(400).json({ error: 'Некорректный идентификатор устройства' });
      return;
    }
    try {
      const result = await yandexIotRequest(req.session!, `/devices/${encodeURIComponent(id)}`);
      res.status(result.status).json(result.data);
    } catch {
      res.status(502).json({ error: 'Ошибка получения состояния устройства' });
    }
  });

  app.post('/api/smart-home/devices/actions', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    const devices = req.body?.devices;
    if (!Array.isArray(devices) || devices.length === 0 || devices.length > 50) {
      res.status(400).json({ error: 'Некорректный список устройств для команды' });
      return;
    }
    for (const item of devices) {
      if (!item || typeof item.id !== 'string' || !isValidResourceId(item.id) || !Array.isArray(item.actions)) {
        res.status(400).json({ error: 'Некорректный формат команды устройства' });
        return;
      }
    }

    try {
      const result = await yandexIotRequest(req.session!, '/devices/actions', {
        method: 'POST',
        body: { devices },
      });
      res.status(result.status).json(result.data);
    } catch {
      res.status(502).json({ error: 'Не удалось отправить команду устройству' });
    }
  });

  app.post('/api/smart-home/groups/:id/actions', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    const id = String(req.params.id || '');
    if (!isValidResourceId(id) || !Array.isArray(req.body?.actions)) {
      res.status(400).json({ error: 'Некорректные параметры команды группы' });
      return;
    }
    try {
      const result = await yandexIotRequest(req.session!, `/groups/${encodeURIComponent(id)}/actions`, {
        method: 'POST',
        body: { actions: req.body.actions },
      });
      res.status(result.status).json(result.data);
    } catch {
      res.status(502).json({ error: 'Не удалось управлять группой устройств' });
    }
  });

  app.post('/api/smart-home/scenarios/:id/actions', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
    const id = String(req.params.id || '');
    if (!isValidResourceId(id)) {
      res.status(400).json({ error: 'Некорректный идентификатор сценария' });
      return;
    }
    try {
      const result = await yandexIotRequest(req.session!, `/scenarios/${encodeURIComponent(id)}/actions`, {
        method: 'POST',
      });
      res.status(result.status).json(result.data);
    } catch {
      res.status(502).json({ error: 'Не удалось запустить сценарий Алисы' });
    }
  });

  // Downloadable ZIP archive of the native Kotlin Android Studio project
  app.get('/api/android-project.zip', (_req: Request, res: Response) => {
    const androidRoot = path.resolve(__dirname, 'android-app');
    if (!fs.existsSync(androidRoot)) {
      res.status(404).json({ error: 'Проект android-app не найден' });
      return;
    }

    const crcTable = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      crcTable[n] = c;
    }
    const calcCrc32 = (buf: Buffer): number => {
      let c = -1;
      for (let i = 0; i < buf.length; i++) {
        c = (c >>> 8) ^ crcTable[(c ^ buf[i]) & 0xff];
      }
      return (c ^ -1) >>> 0;
    };

    const files: Array<{ relPath: string; data: Buffer }> = [];
    const walk = (dir: string) => {
      for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        if (entry.name === '.gradle' || entry.name === 'build' || entry.name === '.idea') continue;
        const full = path.resolve(dir, entry.name);
        if (!full.startsWith(androidRoot + path.sep)) continue;
        if (entry.isDirectory()) {
          walk(full);
        } else if (entry.isFile()) {
          const rel = path.relative(androidRoot, full).split(path.sep).join('/');
          files.push({ relPath: rel, data: fs.readFileSync(full) });
        }
      }
    };
    walk(androidRoot);

    const localParts: Buffer[] = [];
    const centralParts: Buffer[] = [];
    let offset = 0;

    for (const file of files) {
      const nameBuf = Buffer.from(file.relPath, 'utf8');
      const crc = calcCrc32(file.data);
      const compressed = zlib.deflateRawSync(file.data);

      const localHeader = Buffer.alloc(30);
      localHeader.writeUInt32LE(0x04034b50, 0);
      localHeader.writeUInt16LE(20, 4);
      localHeader.writeUInt16LE(0x0800, 6); // UTF-8
      localHeader.writeUInt16LE(8, 8); // deflate
      localHeader.writeUInt16LE(0, 10);
      localHeader.writeUInt16LE(0, 12);
      localHeader.writeUInt32LE(crc, 14);
      localHeader.writeUInt32LE(compressed.length, 18);
      localHeader.writeUInt32LE(file.data.length, 22);
      localHeader.writeUInt16LE(nameBuf.length, 26);
      localHeader.writeUInt16LE(0, 28);

      localParts.push(localHeader, nameBuf, compressed);

      const centralHeader = Buffer.alloc(46);
      centralHeader.writeUInt32LE(0x02014b50, 0);
      centralHeader.writeUInt16LE(20, 4);
      centralHeader.writeUInt16LE(20, 6);
      centralHeader.writeUInt16LE(0x0800, 8);
      centralHeader.writeUInt16LE(8, 10);
      centralHeader.writeUInt16LE(0, 12);
      centralHeader.writeUInt16LE(0, 14);
      centralHeader.writeUInt32LE(crc, 16);
      centralHeader.writeUInt32LE(compressed.length, 20);
      centralHeader.writeUInt32LE(file.data.length, 24);
      centralHeader.writeUInt16LE(nameBuf.length, 28);
      centralHeader.writeUInt16LE(0, 30);
      centralHeader.writeUInt16LE(0, 32);
      centralHeader.writeUInt16LE(0, 34);
      centralHeader.writeUInt16LE(0, 36);
      centralHeader.writeUInt32LE(0, 38);
      centralHeader.writeUInt32LE(offset, 42);

      centralParts.push(centralHeader, nameBuf);
      offset += localHeader.length + nameBuf.length + compressed.length;
    }

    const centralDir = Buffer.concat(centralParts);
    const eocd = Buffer.alloc(22);
    eocd.writeUInt32LE(0x06054b50, 0);
    eocd.writeUInt16LE(0, 4);
    eocd.writeUInt16LE(0, 6);
    eocd.writeUInt16LE(files.length, 8);
    eocd.writeUInt16LE(files.length, 10);
    eocd.writeUInt32LE(centralDir.length, 12);
    eocd.writeUInt32LE(offset, 16);
    eocd.writeUInt16LE(0, 20);

    const zipBuffer = Buffer.concat([...localParts, centralDir, eocd]);
    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', 'attachment; filename="alice-dom-android.zip"');
    res.setHeader('Content-Length', String(zipBuffer.length));
    res.send(zipBuffer);
  });

  // Serve Vite middleware in development, static dist in production
  if (process.env.NODE_ENV === 'production') {
    const distPath = path.resolve(__dirname, 'dist');
    app.use(express.static(distPath));
    app.get('*', (_req: Request, res: Response) => {
      const indexFile = path.join(distPath, 'index.html');
      if (fs.existsSync(indexFile)) {
        res.sendFile(indexFile);
      } else {
        res.status(404).send('Build not found');
      }
    });
  } else {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Server listening on http://0.0.0.0:${PORT}`);
  });
}

startServer().catch((err) => {
  console.error('Failed to start server:', err);
  process.exit(1);
});
