import { useState } from 'react';
import { Check, Layers, Play, Power, Sparkles } from 'lucide-react';
import type { YandexGroup, YandexScenario } from '../types/smartHome';

interface ScenariosViewProps {
  scenarios: YandexScenario[];
  groups: YandexGroup[];
  onRunScenario: (scenario: YandexScenario) => Promise<void>;
  onToggleGroup: (group: YandexGroup, turnOn: boolean) => Promise<void>;
}

export function ScenariosView({ scenarios, groups, onRunScenario, onToggleGroup }: ScenariosViewProps) {
  const [runningId, setRunningId] = useState<string | null>(null);
  const [doneId, setDoneId] = useState<string | null>(null);
  const [busyGroupId, setBusyGroupId] = useState<string | null>(null);

  const handleRun = async (scenario: YandexScenario) => {
    setRunningId(scenario.id);
    try {
      await onRunScenario(scenario);
      setDoneId(scenario.id);
      setTimeout(() => setDoneId((curr) => (curr === scenario.id ? null : curr)), 1800);
    } finally {
      setRunningId(null);
    }
  };

  const handleGroupPower = async (group: YandexGroup, turnOn: boolean) => {
    setBusyGroupId(group.id);
    try {
      await onToggleGroup(group, turnOn);
    } finally {
      setBusyGroupId(null);
    }
  };

  return (
    <div className="space-y-8">
      {/* Scenarios Section */}
      <section className="space-y-3">
        <div className="flex items-baseline justify-between">
          <h2 className="text-lg font-bold text-slate-900 dark:text-slate-100">Сценарии Алисы</h2>
          <span className="text-xs text-slate-500 dark:text-slate-400 tabular-nums">
            {scenarios.length} всего
          </span>
        </div>

        {scenarios.length === 0 ? (
          <div className="rounded-3xl bg-white dark:bg-[#151a25] border border-slate-300/80 dark:border-[#273043] p-6 text-sm text-slate-500 dark:text-slate-400">
            У вас пока нет сценариев в приложении Дом с Алисой. Создайте сценарий в Яндекс Доме, и он сразу появится здесь.
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {scenarios.map((scenario) => {
              const isRunning = runningId === scenario.id;
              const isDone = doneId === scenario.id;

              return (
                <button
                  key={scenario.id}
                  type="button"
                  disabled={isRunning}
                  onClick={() => handleRun(scenario)}
                  className={`min-h-[72px] p-4 rounded-3xl border active:scale-[0.98] transition-all flex items-center justify-between gap-3 text-left cursor-pointer ${
                    isDone
                      ? 'bg-gradient-to-br from-emerald-500 to-teal-600 border-emerald-400 text-white shadow-lg shadow-emerald-500/20'
                      : isRunning
                        ? 'bg-gradient-to-br from-[#3b82f6] to-[#1d4ed8] border-[#60a5fa]/40 text-white shadow-lg shadow-blue-500/25'
                        : 'bg-white dark:bg-[#151a25] border-slate-300/80 dark:border-[#273043] hover:border-blue-500 shadow-xs'
                  }`}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div
                      className={`w-11 h-11 rounded-2xl flex items-center justify-center shrink-0 transition-colors ${
                        isDone || isRunning
                          ? 'bg-white/20 text-white'
                          : 'bg-blue-500/15 text-blue-600 dark:text-blue-400'
                      }`}
                    >
                      {isDone ? <Check className="w-5 h-5" /> : <Sparkles className="w-5 h-5" />}
                    </div>
                    <div className="min-w-0">
                      <div
                        className={`text-sm font-bold truncate ${
                          isDone || isRunning ? 'text-white' : 'text-slate-900 dark:text-slate-100'
                        }`}
                      >
                        {scenario.name}
                      </div>
                      <div
                        className={`text-xs ${
                          isDone || isRunning ? 'text-white/85' : 'text-slate-500 dark:text-slate-400'
                        }`}
                      >
                        {isDone ? 'Выполнено' : scenario.is_active ? 'Нажмите для запуска' : 'Выключен в Алисе'}
                      </div>
                    </div>
                  </div>

                  <div
                    className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${
                      isDone || isRunning
                        ? 'bg-white/20 text-white'
                        : 'bg-slate-100 dark:bg-white/[0.08] text-slate-700 dark:text-slate-200'
                    }`}
                  >
                    <Play className={`w-4 h-4 ${isRunning ? 'animate-pulse' : ''}`} />
                  </div>
                </button>
              );
            })}
          </div>
        )}
      </section>

      {/* Device Groups Section */}
      <section className="space-y-3">
        <div className="flex items-baseline justify-between">
          <h2 className="text-lg font-bold text-slate-900 dark:text-slate-100">Группы устройств</h2>
          <span className="text-xs text-slate-500 dark:text-slate-400 tabular-nums">
            {groups.length} групп
          </span>
        </div>

        {groups.length === 0 ? (
          <div className="rounded-3xl bg-white dark:bg-[#151a25] border border-slate-300/80 dark:border-[#273043] p-6 text-sm text-slate-500 dark:text-slate-400">
            Группы не настроены. Объедините светильники или розетки в группу в приложении Дом с Алисой для управления одним нажатием.
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {groups.map((group) => {
              const hasOnOff = group.capabilities.some((c) => c.type === 'devices.capabilities.on_off');
              const isGroupOn = group.state === 'on' || group.capabilities.some((c) => c.type === 'devices.capabilities.on_off' && c.state?.value === true);
              const busy = busyGroupId === group.id;

              return (
                <div
                  key={group.id}
                  className={`p-4 rounded-3xl border flex items-center justify-between gap-3 transition-all ${
                    isGroupOn
                      ? 'bg-gradient-to-br from-[#3b82f6] to-[#1d4ed8] border-[#60a5fa]/40 text-white shadow-lg shadow-blue-500/25'
                      : 'bg-white dark:bg-[#151a25] border-slate-300/80 dark:border-[#273043] shadow-xs'
                  }`}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div
                      className={`w-11 h-11 rounded-2xl flex items-center justify-center shrink-0 ${
                        isGroupOn
                          ? 'bg-white/20 text-white'
                          : 'bg-sky-500/15 text-sky-600 dark:text-sky-400'
                      }`}
                    >
                      <Layers className="w-5 h-5" />
                    </div>
                    <div className="min-w-0">
                      <div
                        className={`text-sm font-bold truncate ${
                          isGroupOn ? 'text-white' : 'text-slate-900 dark:text-slate-100'
                        }`}
                      >
                        {group.name}
                      </div>
                      <div
                        className={`text-xs tabular-nums ${
                          isGroupOn ? 'text-white/85' : 'text-slate-500 dark:text-slate-400'
                        }`}
                      >
                        {group.devices.length} устройств · {isGroupOn ? 'Включено' : 'Выключено'}
                      </div>
                    </div>
                  </div>

                  {hasOnOff && (
                    <div className="flex items-center gap-1.5 shrink-0">
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => handleGroupPower(group, !isGroupOn)}
                        aria-label={`${isGroupOn ? 'Выключить' : 'Включить'} группу ${group.name}`}
                        className={`min-w-[44px] min-h-[44px] px-3 rounded-2xl text-xs font-bold transition-colors flex items-center justify-center gap-1.5 cursor-pointer ${
                          isGroupOn
                            ? 'bg-white/20 hover:bg-white/30 text-white'
                            : 'bg-blue-600 hover:bg-blue-700 text-white shadow-xs'
                        }`}
                      >
                        <Power className="w-4 h-4" />
                        <span>{isGroupOn ? 'Выкл' : 'Вкл'}</span>
                      </button>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}
