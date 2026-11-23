import type { YandexDevice } from '../types/smartHome';
import {
  formatPropertyInfo,
  getDeviceCategory,
  getDevicePrimarySummary,
  getOnOffCapability,
  getRangeCapability,
  isDeviceOn,
} from '../utils/yandexHelpers';
import { DeviceIcon } from './DeviceIcon';

interface DeviceCardProps {
  device: YandexDevice;
  roomName?: string;
  isBusy?: boolean;
  onTogglePower: (device: YandexDevice) => void;
  onRefreshDevice?: (device: YandexDevice) => void;
}

export function DeviceCard({
  device,
  roomName,
  isBusy = false,
  onTogglePower,
  onRefreshDevice,
}: DeviceCardProps) {
  const onOffCap = getOnOffCapability(device);
  const isOn = isDeviceOn(device);
  const category = getDeviceCategory(device.type);
  const isLight = category === 'light';
  const isClimate = category === 'climate';
  const isPower = category === 'power';
  const brightness = getRangeCapability(device, 'brightness')?.state?.value;
  const openPos = getRangeCapability(device, 'open')?.state?.value;
  const targetTemp = getRangeCapability(device, 'temperature')?.state?.value;

  const summary = getDevicePrimarySummary(device, roomName);
  const hasAlertProperty = device.properties.some((p) => formatPropertyInfo(p).isAlert);

  // Saturated, energetic ON state vs crisp high-contrast OFF state
  const cardClasses = isOn
    ? isLight
      ? 'bg-gradient-to-br from-[#ffca3a] to-[#f59e0b] border-[#fbbf24] text-slate-950 shadow-lg shadow-amber-500/20'
      : isClimate
        ? 'bg-gradient-to-br from-[#0ea5e9] to-[#0369a1] border-[#38bdf8]/40 text-white shadow-lg shadow-sky-500/20'
        : isPower
          ? 'bg-gradient-to-br from-[#10b981] to-[#059669] border-[#34d399]/40 text-white shadow-lg shadow-emerald-500/25'
          : 'bg-gradient-to-br from-[#3b82f6] to-[#1d4ed8] border-[#60a5fa]/40 text-white shadow-lg shadow-blue-500/25'
    : hasAlertProperty
      ? 'bg-red-50 dark:bg-red-950/50 border-red-400 dark:border-red-700 text-red-950 dark:text-red-100'
      : 'bg-white dark:bg-[#151a25] border-slate-300/80 dark:border-[#273043] text-slate-900 dark:text-slate-100 hover:border-slate-400 dark:hover:border-[#394661] shadow-xs';

  const inactiveAccentIcon = isLight
    ? 'bg-amber-500/15 text-amber-600 dark:text-amber-400'
    : isClimate
      ? 'bg-sky-500/15 text-sky-600 dark:text-sky-400'
      : isPower
        ? 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400'
        : category === 'sensor'
          ? 'bg-cyan-500/15 text-cyan-600 dark:text-cyan-400'
          : 'bg-blue-500/15 text-blue-600 dark:text-blue-400';

  const iconBoxClasses = isOn
    ? isLight
      ? 'bg-slate-950/12 text-slate-950'
      : 'bg-white/20 text-white'
    : hasAlertProperty
      ? 'bg-red-600 text-white'
      : inactiveAccentIcon;

  const statusText = isOn
    ? brightness !== undefined
      ? `${brightness}%`
      : targetTemp !== undefined
        ? `${targetTemp}°`
        : 'ВКЛ'
    : openPos !== undefined
      ? `${openPos}%`
      : onOffCap
        ? 'ВЫКЛ'
        : '';

  const statusClasses = isOn
    ? isLight
      ? 'text-slate-950 font-bold'
      : 'text-white font-bold'
    : hasAlertProperty
      ? 'text-red-600 dark:text-red-300 font-bold'
      : 'text-slate-500 dark:text-slate-400 font-semibold';

  const metaClasses = isOn
    ? isLight
      ? 'text-slate-900/80'
      : 'text-white/85'
    : 'text-slate-500 dark:text-slate-400';

  const handleTileClick = () => {
    if (isBusy) return;
    if (onOffCap) {
      onTogglePower(device);
    } else if (onRefreshDevice) {
      onRefreshDevice(device);
    }
  };

  return (
    <button
      type="button"
      onClick={handleTileClick}
      disabled={isBusy}
      aria-pressed={onOffCap ? isOn : undefined}
      aria-label={onOffCap ? `${device.name}: ${isOn ? 'выключить' : 'включить'}` : device.name}
      className={`group relative w-full text-left rounded-3xl border p-4 transition-all duration-150 active:scale-[0.97] disabled:opacity-75 cursor-pointer select-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 flex flex-col justify-between min-h-[138px] ${cardClasses}`}
    >
      {/* Top row: Category Icon + Crisp Status Value (No extra toggle switch) */}
      <div className="flex items-start justify-between gap-2 w-full">
        <div className={`w-11 h-11 rounded-2xl flex items-center justify-center shrink-0 transition-colors ${iconBoxClasses}`}>
          <DeviceIcon type={device.type} className={`w-5 h-5 ${isBusy ? 'animate-pulse' : ''}`} />
        </div>

        {statusText && (
          <span className={`text-xs font-mono-tabular tracking-tight pt-1 ${statusClasses}`}>
            {statusText}
          </span>
        )}
      </div>

      {/* Bottom info: Room + Status bold on top, Equipment name underneath */}
      <div className="mt-4 w-full">
        <h3 className="text-sm sm:text-[15px] font-bold leading-snug line-clamp-2 tabular-nums">
          {summary}
        </h3>
        <p className={`mt-1 text-xs truncate ${metaClasses}`}>
          {device.name}
        </p>
      </div>
    </button>
  );
}
