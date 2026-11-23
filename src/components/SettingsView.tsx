import { Check, Copy, Home, LogOut, Moon, RefreshCw, Smartphone, Sun, Vibrate } from 'lucide-react';
import { useState } from 'react';
import type { AppRuntimeConfig, YandexHousehold, YandexProfile } from '../types/smartHome';
import { DownloadAndroidZipButton } from './DownloadAndroidZipButton';

export type ThemePreference = 'auto' | 'light' | 'dark';

interface SettingsViewProps {
  profile: YandexProfile | null;
  config: AppRuntimeConfig | null;
  households: YandexHousehold[];
  selectedHouseholdId: string;
  onSelectHousehold: (id: string) => void;
  themePref: ThemePreference;
  onChangeTheme: (pref: ThemePreference) => void;
  hapticsEnabled: boolean;
  onToggleHaptics: () => void;
  autoRefreshSec: number;
  onChangeAutoRefresh: (sec: number) => void;
  onLogout: () => void;
}

export function SettingsView({
  profile,
  config,
  households,
  selectedHouseholdId,
  onSelectHousehold,
  themePref,
  onChangeTheme,
  hapticsEnabled,
  onToggleHaptics,
  autoRefreshSec,
  onChangeAutoRefresh,
  onLogout,
}: SettingsViewProps) {
  const [copiedCallback, setCopiedCallback] = useState(false);

  const callbackUrl = config?.callbackUrl || `${window.location.origin}/auth/yandex/callback`;

  const copyCallback = async () => {
    try {
      await navigator.clipboard.writeText(callbackUrl);
      setCopiedCallback(true);
      setTimeout(() => setCopiedCallback(false), 1800);
    } catch {
      // Ignore
    }
  };

  return (
    <div className="max-w-3xl space-y-6">
      {/* Account & Household Card */}
      <section className="rounded-3xl bg-white dark:bg-[#171c26] border border-slate-200/80 dark:border-white/[0.07] p-5 sm:p-6 space-y-5">
        <div className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-3.5 min-w-0">
            {profile?.avatarUrl ? (
              <img
                src={profile.avatarUrl}
                alt={profile.displayName}
                referrerPolicy="no-referrer"
                className="w-12 h-12 rounded-2xl object-cover shrink-0 bg-slate-100"
              />
            ) : (
              <div className="w-12 h-12 rounded-2xl bg-blue-600 text-white flex items-center justify-center font-bold text-lg shrink-0">
                {(profile?.displayName || 'Я').charAt(0).toUpperCase()}
              </div>
            )}
            <div className="min-w-0">
              <h2 className="text-base font-bold text-slate-900 dark:text-slate-100 truncate">
                {profile?.displayName || 'Яндекс Умный дом'}
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400 truncate">
                {profile?.authMethod === 'oauth' ? 'Подключено через Яндекс ID' : 'Подключено по OAuth-токену'} · Сессия сохранена
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={onLogout}
            className="min-h-[44px] px-4 py-2 rounded-xl bg-red-50 dark:bg-red-950/40 text-red-600 dark:text-red-300 text-xs font-semibold hover:bg-red-100 dark:hover:bg-red-950/70 flex items-center gap-2 shrink-0 cursor-pointer"
          >
            <LogOut className="w-4 h-4" />
            <span>Выйти</span>
          </button>
        </div>

        {/* Household Selector */}
        {households.length > 0 && (
          <div className="pt-4 border-t border-slate-200/70 dark:border-white/[0.07] space-y-2">
            <label className="text-xs font-medium text-slate-500 dark:text-slate-400 flex items-center gap-1.5">
              <Home className="w-3.5 h-3.5" />
              Активный дом (сохраняется автоматически)
            </label>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => onSelectHousehold('all')}
                className={`min-h-[40px] px-3.5 py-2 rounded-xl text-xs font-semibold cursor-pointer ${
                  selectedHouseholdId === 'all'
                    ? 'bg-blue-600 text-white'
                    : 'bg-slate-100 dark:bg-white/[0.07] text-slate-700 dark:text-slate-200'
                }`}
              >
                Все дома
              </button>
              {households.map((h) => (
                <button
                  key={h.id}
                  type="button"
                  onClick={() => onSelectHousehold(h.id)}
                  className={`min-h-[40px] px-3.5 py-2 rounded-xl text-xs font-semibold cursor-pointer ${
                    selectedHouseholdId === h.id
                      ? 'bg-blue-600 text-white'
                      : 'bg-slate-100 dark:bg-white/[0.07] text-slate-700 dark:text-slate-200'
                  }`}
                >
                  {h.name}
                </button>
              ))}
            </div>
          </div>
        )}
      </section>

      {/* Preferences Card */}
      <section className="rounded-3xl bg-white dark:bg-[#171c26] border border-slate-200/80 dark:border-white/[0.07] p-5 sm:p-6 space-y-5">
        <h3 className="text-base font-bold text-slate-900 dark:text-slate-100">Интерфейс и поведение</h3>

        {/* Theme */}
        <div className="space-y-2">
          <div className="text-xs font-medium text-slate-500 dark:text-slate-400">Тема оформления</div>
          <div className="grid grid-cols-3 gap-2 p-1 rounded-2xl bg-slate-100 dark:bg-[#0e1117]">
            {[
              { id: 'auto' as const, label: 'Системная', icon: Smartphone },
              { id: 'light' as const, label: 'Светлая', icon: Sun },
              { id: 'dark' as const, label: 'Тёмная', icon: Moon },
            ].map((item) => {
              const Icon = item.icon;
              const active = themePref === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => onChangeTheme(item.id)}
                  className={`min-h-[42px] rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors cursor-pointer ${
                    active
                      ? 'bg-white dark:bg-[#1e2533] text-slate-900 dark:text-slate-100 shadow-xs'
                      : 'text-slate-600 dark:text-slate-400'
                  }`}
                >
                  <Icon className="w-3.5 h-3.5" />
                  <span>{item.label}</span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Auto refresh */}
        <div className="space-y-2">
          <div className="text-xs font-medium text-slate-500 dark:text-slate-400 flex items-center gap-1.5">
            <RefreshCw className="w-3.5 h-3.5" />
            Автообновление статусов
          </div>
          <div className="grid grid-cols-4 gap-2">
            {[
              { sec: 0, label: 'Выкл' },
              { sec: 15, label: '15 сек' },
              { sec: 30, label: '30 сек' },
              { sec: 60, label: '60 сек' },
            ].map((opt) => (
              <button
                key={opt.sec}
                type="button"
                onClick={() => onChangeAutoRefresh(opt.sec)}
                className={`min-h-[42px] rounded-xl text-xs font-mono-tabular font-medium cursor-pointer ${
                  autoRefreshSec === opt.sec
                    ? 'bg-blue-600 text-white'
                    : 'bg-slate-100 dark:bg-white/[0.07] text-slate-700 dark:text-slate-200'
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>

        {/* Vibration feedback */}
        <button
          type="button"
          onClick={onToggleHaptics}
          className="w-full min-h-[52px] pt-3 border-t border-slate-200/70 dark:border-white/[0.07] flex items-center justify-between cursor-pointer"
        >
          <span className="flex items-center gap-2.5 text-sm font-medium text-slate-800 dark:text-slate-200">
            <Vibrate className="w-4 h-4 text-blue-600" />
            <span>Виброотклик на Android</span>
          </span>
          <span
            className={`w-11 h-6 rounded-full p-0.5 transition-colors flex items-center ${
              hapticsEnabled ? 'bg-blue-600 justify-end' : 'bg-slate-300 dark:bg-slate-700 justify-start'
            }`}
          >
            <span className="w-5 h-5 rounded-full bg-white shadow-xs" />
          </span>
        </button>
      </section>

      {/* Android Installation & APK Source */}
      <section className="rounded-3xl bg-white dark:bg-[#171c26] border border-slate-200/80 dark:border-white/[0.07] p-5 sm:p-6 space-y-4">
        <h3 className="text-base font-bold text-slate-900 dark:text-slate-100">
          Приложение для Android
        </h3>
        <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
          Скачайте архив нативного Kotlin-проекта для сборки APK в Android Studio:
        </p>
        <div>
          <DownloadAndroidZipButton />
        </div>

        <div className="pt-2">
          <div className="text-xs text-slate-500 dark:text-slate-400 mb-1">
            Redirect URI для <span className="font-mono-tabular">oauth.yandex.ru</span>
          </div>
          <div className="flex items-center gap-2 p-2.5 rounded-xl bg-slate-50 dark:bg-[#0e1117] border border-slate-200/80 dark:border-white/[0.08]">
            <code className="text-xs font-mono-tabular text-slate-700 dark:text-slate-300 truncate flex-1">
              {callbackUrl}
            </code>
            <button
              type="button"
              onClick={copyCallback}
              aria-label="Скопировать Callback URL"
              className="min-w-[36px] min-h-[36px] rounded-lg flex items-center justify-center hover:bg-slate-200/70 dark:hover:bg-white/10 cursor-pointer"
            >
              {copiedCallback ? <Check className="w-4 h-4 text-emerald-500" /> : <Copy className="w-4 h-4" />}
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}
