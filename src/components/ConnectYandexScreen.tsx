import { useState } from 'react';
import {
  Check,
  ChevronDown,
  ChevronUp,
  Copy,
  ExternalLink,
  KeyRound,
  ShieldCheck,
  Smartphone,
  Sparkles,
} from 'lucide-react';
import type { AppRuntimeConfig, YandexProfile } from '../types/smartHome';
import {
  connectWithYandexToken,
  exchangeOAuthCode,
  exchangeVerificationCode,
  fetchYandexOAuthUrl,
} from '../utils/api';
import { DownloadAndroidZipButton } from './DownloadAndroidZipButton';

interface ConnectYandexScreenProps {
  config: AppRuntimeConfig | null;
  onAuthenticated: (sessionToken: string, profile: YandexProfile) => void;
  hapticImpact: (style?: 'light' | 'medium' | 'heavy') => void;
  hapticNotify: (type?: 'success' | 'warning' | 'error') => void;
}

export function ConnectYandexScreen({
  config,
  onAuthenticated,
  hapticImpact,
  hapticNotify,
}: ConnectYandexScreenProps) {
  const [loadingOAuth, setLoadingOAuth] = useState(false);
  const [verificationModeOpen, setVerificationModeOpen] = useState(false);
  const [verificationCode, setVerificationCode] = useState('');
  const [loadingCode, setLoadingCode] = useState(false);

  const [tokenOpen, setTokenOpen] = useState(!config?.yandexOAuthConfigured);
  const [clientIdHelper, setClientIdHelper] = useState('');
  const [tokenInput, setTokenInput] = useState('');
  const [loadingToken, setLoadingToken] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copiedCallback, setCopiedCallback] = useState(false);

  const callbackUrl = config?.callbackUrl || `${window.location.origin}/auth/yandex/callback`;

  const handleCopyCallback = async () => {
    try {
      await navigator.clipboard.writeText(callbackUrl);
      setCopiedCallback(true);
      hapticImpact('light');
      setTimeout(() => setCopiedCallback(false), 2000);
    } catch {
      // Ignore clipboard failures
    }
  };

  const handleOAuthConnect = async () => {
    setError(null);
    setLoadingOAuth(true);
    hapticImpact('medium');

    try {
      const { url } = await fetchYandexOAuthUrl('callback');
      const popup = window.open(url, 'yandex_oauth_popup', 'width=540,height=680');
      if (!popup) {
        setError('Браузер заблокировал всплывающее окно. Разрешите pop-up или используйте вход по OAuth-токену ниже.');
        setLoadingOAuth(false);
        return;
      }

      const handleMessage = async (event: MessageEvent) => {
        if (!event.origin.endsWith('.run.app') && !event.origin.includes('localhost') && event.origin !== window.location.origin) {
          return;
        }
        if (event.data?.type === 'YANDEX_OAUTH_SUCCESS' && event.data?.exchangeCode) {
          window.removeEventListener('message', handleMessage);
          clearInterval(closedChecker);
          try {
            const auth = await exchangeOAuthCode(event.data.exchangeCode);
            hapticNotify('success');
            onAuthenticated(auth.sessionToken, auth.profile);
          } catch (err: any) {
            hapticNotify('error');
            setError(err?.message || 'Не удалось завершить авторизацию через Яндекс ID');
          } finally {
            setLoadingOAuth(false);
          }
        } else if (event.data?.type === 'YANDEX_OAUTH_ERROR') {
          window.removeEventListener('message', handleMessage);
          clearInterval(closedChecker);
          hapticNotify('error');
          setError(event.data.error || 'Авторизация отменена');
          setLoadingOAuth(false);
        }
      };

      window.addEventListener('message', handleMessage);
      const closedChecker = setInterval(() => {
        if (popup.closed) {
          clearInterval(closedChecker);
          window.removeEventListener('message', handleMessage);
          setLoadingOAuth(false);
        }
      }, 600);
    } catch (err: any) {
      hapticNotify('error');
      setError(err?.message || 'Ошибка запуска авторизации');
      if (err?.setupRequired) {
        setTokenOpen(true);
      }
      setLoadingOAuth(false);
    }
  };

  const handleOpenVerificationCodePopup = async () => {
    setError(null);
    hapticImpact('light');
    try {
      const { url } = await fetchYandexOAuthUrl('verification_code');
      const popup = window.open(url, 'yandex_code_popup', 'width=540,height=680');
      if (!popup) {
        setError('Разрешите всплывающие окна, чтобы открыть страницу Яндекса с кодом.');
      }
    } catch (err: any) {
      setError(err?.message || 'Не удалось открыть страницу авторизации Яндекса');
    }
  };

  const handleVerificationCodeSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!verificationCode.trim()) return;
    setError(null);
    setLoadingCode(true);
    hapticImpact('medium');
    try {
      const auth = await exchangeVerificationCode(verificationCode.trim());
      hapticNotify('success');
      onAuthenticated(auth.sessionToken, auth.profile);
    } catch (err: any) {
      hapticNotify('error');
      setError(err?.message || 'Не удалось подтвердить код Яндекса');
    } finally {
      setLoadingCode(false);
    }
  };

  const handleOpenTokenHelper = () => {
    const cid = clientIdHelper.trim();
    if (!/^[a-zA-Z0-9_-]{6,128}$/.test(cid)) {
      setError('Введите корректный Client ID приложения из Яндекс OAuth.');
      return;
    }
    setError(null);
    hapticImpact('light');
    const url = `https://oauth.yandex.ru/authorize?response_type=token&client_id=${encodeURIComponent(cid)}&force_confirm=yes`;
    const popup = window.open(url, 'yandex_token_popup', 'width=540,height=680');
    if (!popup) {
      setError('Браузер заблокировал всплывающее окно. Разрешите pop-up и нажмите ещё раз.');
    }
  };

  const handleTokenSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!tokenInput.trim()) return;
    setError(null);
    setLoadingToken(true);
    hapticImpact('medium');

    try {
      const auth = await connectWithYandexToken(tokenInput.trim());
      hapticNotify('success');
      onAuthenticated(auth.sessionToken, auth.profile);
    } catch (err: any) {
      hapticNotify('error');
      setError(err?.message || 'Не удалось подключиться по токену');
    } finally {
      setLoadingToken(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col justify-center px-4 py-8 sm:py-12">
      <div className="w-full max-w-4xl mx-auto grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
        {/* Main Connect Card */}
        <div className="lg:col-span-7 bg-white dark:bg-[#171c26] border border-slate-200/80 dark:border-white/[0.07] rounded-3xl p-6 sm:p-8 flex flex-col justify-between shadow-sm">
          <div>
            <div className="flex items-center justify-between gap-3 mb-6">
              <div className="flex items-center gap-3">
                <div className="w-11 h-11 rounded-2xl bg-blue-600 text-white flex items-center justify-center shrink-0">
                  <Sparkles className="w-5 h-5" />
                </div>
                <div>
                  <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
                    Алиса Дом
                  </h1>
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    Android App · Яндекс Умный дом API
                  </p>
                </div>
              </div>
            </div>

            <h2 className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100 leading-tight [text-wrap:balance] mb-3">
              Управляйте устройствами Алисы с экрана Android
            </h2>
            <p className="text-sm sm:text-base text-slate-600 dark:text-slate-400 leading-relaxed mb-6">
              Освещение, розетки, кондиционеры, шторы, датчики и голосовые сценарии Яндекса. Сессия и настройки сохраняются автоматически.
            </p>

            {error && (
              <div
                role="alert"
                className="mb-5 p-3.5 rounded-2xl bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900/60 text-sm text-red-700 dark:text-red-300"
              >
                {error}
              </div>
            )}

            {/* Primary OAuth CTA */}
            <div className="space-y-3">
              <button
                type="button"
                onClick={handleOAuthConnect}
                disabled={loadingOAuth || !config?.yandexOAuthConfigured}
                className="btn-3d-primary w-full min-h-[50px] px-5 py-3 rounded-[18px] disabled:opacity-50 disabled:cursor-not-allowed text-white font-bold text-sm sm:text-base transition-all duration-120 flex items-center justify-center gap-2.5 cursor-pointer"
              >
                <ShieldCheck className="w-5 h-5 shrink-0" />
                <span className="whitespace-nowrap">
                  {loadingOAuth ? 'Ожидание Яндекса...' : 'Войти через Яндекс ID'}
                </span>
              </button>

              {config?.yandexOAuthConfigured ? (
                <div>
                  <button
                    type="button"
                    onClick={() => setVerificationModeOpen((v) => !v)}
                    className="text-xs font-medium text-blue-600 hover:underline cursor-pointer"
                  >
                    {verificationModeOpen
                      ? 'Скрыть вход по коду подтверждения'
                      : 'У приложения в Яндексе указан oauth.yandex.ru/verification_code?'}
                  </button>

                  {verificationModeOpen && (
                    <form
                      onSubmit={handleVerificationCodeSubmit}
                      className="mt-3 p-3.5 rounded-2xl bg-slate-50 dark:bg-[#0e1117] border border-slate-200/80 dark:border-white/[0.08] space-y-2.5"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs text-slate-600 dark:text-slate-400">
                          1. Откройте Яндекс и скопируйте цифровой код:
                        </span>
                        <button
                          type="button"
                          onClick={handleOpenVerificationCodePopup}
                          className="btn-3d-neutral min-h-[38px] px-3.5 rounded-[16px] text-blue-600 dark:text-blue-400 text-xs font-bold shrink-0 cursor-pointer transition-all duration-120"
                        >
                          Получить код
                        </button>
                      </div>
                      <div className="flex gap-2">
                        <input
                          type="text"
                          value={verificationCode}
                          onChange={(e) => setVerificationCode(e.target.value)}
                          placeholder="Код с oauth.yandex.ru/verification_code"
                          className="flex-1 min-h-[46px] px-3 rounded-[16px] bg-white dark:bg-[#171c26] border border-slate-200 dark:border-white/[0.1] text-sm font-mono-tabular text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-600"
                        />
                        <button
                          type="submit"
                          disabled={loadingCode || verificationCode.trim().length < 4}
                          className="btn-3d-primary min-h-[46px] px-4 rounded-[16px] text-white text-xs font-bold disabled:opacity-40 cursor-pointer transition-all duration-120"
                        >
                          {loadingCode ? 'Вход...' : 'Войти'}
                        </button>
                      </div>
                    </form>
                  )}
                </div>
              ) : (
                <p className="text-xs text-slate-500 dark:text-slate-400 leading-relaxed">
                  Для входа в один клик задайте <span className="font-mono-tabular">YANDEX_CLIENT_ID</span> и{' '}
                  <span className="font-mono-tabular">YANDEX_CLIENT_SECRET</span> в секретах, либо подключитесь ниже по OAuth-токену (работает с <span className="font-mono-tabular">verification_code</span>).
                </p>
              )}
            </div>

            {/* Expandable Direct OAuth Token Flow */}
            <div className="mt-6 pt-5 border-t border-slate-200/80 dark:border-white/[0.07]">
              <button
                type="button"
                onClick={() => {
                  hapticImpact('light');
                  setTokenOpen((prev) => !prev);
                }}
                className="w-full min-h-[44px] flex items-center justify-between text-sm font-semibold text-slate-700 dark:text-slate-200 hover:text-slate-900 dark:hover:text-white cursor-pointer"
              >
                <span className="flex items-center gap-2">
                  <KeyRound className="w-4 h-4 text-blue-600" />
                  <span>Быстрое подключение по OAuth-токену</span>
                </span>
                {tokenOpen ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
              </button>

              {tokenOpen && (
                <div className="mt-3 space-y-4">
                  <div className="p-3.5 rounded-2xl bg-slate-50 dark:bg-[#0e1117] border border-slate-200/80 dark:border-white/[0.08] space-y-2.5">
                    <div className="text-xs font-semibold text-slate-800 dark:text-slate-200">
                      Как получить токен через <span className="font-mono-tabular">verification_code</span>
                    </div>
                    <p className="text-xs text-slate-500 dark:text-slate-400 leading-relaxed">
                      Вставьте <span className="font-mono-tabular">Client ID</span> из вашего приложения на <span className="font-mono-tabular">oauth.yandex.ru</span> — Яндекс сразу покажет токен на экране:
                    </p>
                    <div className="flex flex-col sm:flex-row gap-2.5">
                      <input
                        type="text"
                        value={clientIdHelper}
                        onChange={(e) => setClientIdHelper(e.target.value)}
                        placeholder="Client ID приложения Яндекса"
                        className="flex-1 min-h-[44px] px-3 rounded-[16px] bg-white dark:bg-[#171c26] border border-slate-200 dark:border-white/[0.1] text-xs font-mono-tabular text-slate-900 dark:text-slate-100 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600"
                      />
                      <button
                        type="button"
                        onClick={handleOpenTokenHelper}
                        className="btn-3d-neutral min-h-[44px] px-4 rounded-[16px] text-blue-600 dark:text-blue-400 text-xs font-bold whitespace-nowrap cursor-pointer transition-all duration-120"
                      >
                        Получить токен
                      </button>
                    </div>
                  </div>

                  <form onSubmit={handleTokenSubmit} className="space-y-3">
                    <label htmlFor="yandex-token-input" className="block text-xs text-slate-500 dark:text-slate-400 leading-relaxed">
                      Вставьте полученный OAuth-токен (проверяется через <span className="font-mono-tabular">api.iot.yandex.net</span> и сохраняется в зашифрованном виде на сервере):
                    </label>
                    <input
                      id="yandex-token-input"
                      type="password"
                      value={tokenInput}
                      onChange={(e) => setTokenInput(e.target.value)}
                      placeholder="y0_AgAAAA..."
                      autoComplete="off"
                      className="w-full min-h-[46px] px-3.5 py-2.5 rounded-[16px] bg-slate-50 dark:bg-[#0e1117] border border-slate-200 dark:border-white/[0.1] text-sm font-mono-tabular text-slate-900 dark:text-slate-100 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600"
                    />
                    <button
                      type="submit"
                      disabled={loadingToken || tokenInput.trim().length < 16}
                      className="btn-3d-primary w-full min-h-[48px] px-4 py-2.5 rounded-[18px] text-white font-bold text-sm disabled:opacity-40 transition-all duration-120 cursor-pointer"
                    >
                      {loadingToken ? 'Проверяем токен...' : 'Подключить дом'}
                    </button>
                  </form>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Android Setup & Guide Card */}
        <div className="lg:col-span-5 bg-white dark:bg-[#171c26] border border-slate-200/80 dark:border-white/[0.07] rounded-3xl p-6 sm:p-8 flex flex-col justify-between">
          <div className="space-y-5">
            <div className="flex items-center gap-2.5">
              <Smartphone className="w-5 h-5 text-blue-600 shrink-0" />
              <h3 className="text-base font-bold text-slate-900 dark:text-slate-100">
                Установка на Android и настройка OAuth
              </h3>
            </div>

            <div className="space-y-4 text-xs sm:text-sm text-slate-600 dark:text-slate-400">
              <div>
                <div className="font-semibold text-slate-900 dark:text-slate-200 mb-1">
                  1. Нативный Kotlin APK (Android Studio)
                </div>
                <p className="leading-relaxed text-xs mb-2.5">
                  Автономное Android-приложение на Kotlin + Jetpack Compose (работает напрямую с Яндекс API, без сервера и без лишних логинов):
                </p>
                <DownloadAndroidZipButton />
              </div>

              <div>
                <div className="font-semibold text-slate-900 dark:text-slate-200 mb-1">
                  2. Настройка Яндекс OAuth
                </div>
                <p className="leading-relaxed mb-2">
                  На{' '}
                  <a
                    href="https://oauth.yandex.ru/client/new"
                    target="_blank"
                    rel="noreferrer"
                    className="text-blue-600 hover:underline inline-flex items-center gap-1"
                  >
                    oauth.yandex.ru <ExternalLink className="w-3 h-3" />
                  </a>{' '}
                  включите права <span className="font-mono-tabular">iot:view</span> и{' '}
                  <span className="font-mono-tabular">iot:control</span>. Для платформы «Веб-сервисы» укажите Callback URL:
                </p>
                <div className="flex items-center gap-2 p-2.5 rounded-xl bg-slate-50 dark:bg-[#0e1117] border border-slate-200/80 dark:border-white/[0.08]">
                  <code className="text-xs font-mono-tabular text-slate-700 dark:text-slate-300 truncate flex-1">
                    {callbackUrl}
                  </code>
                  <button
                    type="button"
                    onClick={handleCopyCallback}
                    aria-label="Скопировать Callback URL"
                    className="min-w-[36px] min-h-[36px] flex items-center justify-center rounded-lg hover:bg-slate-200/70 dark:hover:bg-white/10 text-slate-600 dark:text-slate-300 cursor-pointer"
                  >
                    {copiedCallback ? <Check className="w-4 h-4 text-emerald-500" /> : <Copy className="w-4 h-4" />}
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div className="mt-6 pt-4 border-t border-slate-200/80 dark:border-white/[0.07] text-xs text-slate-500 dark:text-slate-400">
            Данные сессии и настройки сохраняются локально и шифруются на сервере
          </div>
        </div>
      </div>
    </div>
  );
}
