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
  pairingMode?: boolean;
  isPairSelected?: boolean;
  onTogglePower: (device: YandexDevice) => void;
  onRefreshDevice?: (device: YandexDevice) => void;
  onSelectForPair?: (device: YandexDevice) => void;
}

export function DeviceCard({
  device,
  roomName,
  isBusy = false,
  pairingMode = false,
  isPairSelected = false,
  onTogglePower,
  onRefreshDevice,
  onSelectForPair,
}: DeviceCardProps) {
  const onOffCap = getOnOffCapability(device);
  const isOn = isDeviceOn(device);
  const isSwitch = device.type.startsWith('devices.types.switch');
  const category = getDeviceCategory(device.type);
  const isLight = category === 'light';
  const isClimate = category === 'climate';
  const isPower = category === 'power';
  const brightness = getRangeCapability(device, 'brightness')?.state?.value;
  const openPos = getRangeCapability(device, 'open')?.state?.value;
  const targetTemp = getRangeCapability(device, 'temperature')?.state?.value;

  const summary = getDevicePrimarySummary(device, roomName);
  const hasAlertProperty = device.properties.some((p) => formatPropertyInfo(p).isAlert);

  const surfaceClass = isOn
    ? isLight
      ? 'keycap-3d-amber text-slate-950'
      : isClimate
        ? 'keycap-3d-sky text-white'
        : isPower
          ? 'keycap-3d-emerald text-white'
          : 'keycap-3d-blue text-white'
    : hasAlertProperty
      ? 'keycap-3d-alert text-red-950 dark:text-red-100'
      : 'keycap-3d-off text-slate-900 dark:text-slate-100';

  const inactiveAccentIcon = isLight
    ? 'bg-amber-500/12 text-amber-600 dark:text-amber-400 border border-amber-500/20'
    : isClimate
      ? 'bg-sky-500/12 text-sky-600 dark:text-sky-400 border border-sky-500/20'
      : isPower
        ? 'bg-emerald-500/12 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
        : category === 'sensor'
          ? 'bg-cyan-500/12 text-cyan-600 dark:text-cyan-400 border border-cyan-500/20'
          : 'bg-blue-500/12 text-blue-600 dark:text-blue-400 border border-blue-500/20';

  const iconSocketClasses = isOn
    ? isLight
      ? 'bg-slate-950/12 text-slate-950 shadow-[inset_0_2px_4px_rgba(15,23,42,0.16)]'
      : 'bg-white/22 text-white shadow-[inset_0_1px_2px_rgba(255,255,255,0.35),0_2px_6px_rgba(0,0,0,0.14)]'
    : hasAlertProperty
      ? 'bg-red-600 text-white shadow-inner'
      : `${inactiveAccentIcon} shadow-[inset_0_2px_4px_rgba(15,23,42,0.08)] dark:shadow-[inset_0_2px_5px_rgba(0,0,0,0.45)]`;

  const ledClasses = isOn
    ? isLight
      ? 'bg-slate-950/75 shadow-[0_0_8px_rgba(15,23,42,0.28)]'
      : 'bg-white shadow-[0_0_10px_rgba(255,255,255,0.85)]'
    : hasAlertProperty
      ? 'bg-red-500 shadow-[0_0_8px_rgba(239,68,68,0.65)]'
      : 'bg-slate-300 dark:bg-slate-700/80 shadow-inner';

  const metricBadge = isOn
    ? brightness !== undefined
      ? `${brightness}%`
      : targetTemp !== undefined
        ? `${targetTemp}°`
        : ''
    : openPos !== undefined
      ? `${openPos}%`
      : '';

  const metaClasses = isOn
    ? isLight
      ? 'text-slate-900/85'
      : 'text-white/85'
    : 'text-slate-500 dark:text-slate-400';

  const handleTileClick = () => {
    if (isBusy) return;
    if (pairingMode && onSelectForPair) {
      onSelectForPair(device);
      return;
    }
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
      aria-pressed={pairingMode ? isPairSelected : onOffCap ? isOn : undefined}
      aria-label={
        pairingMode
          ? `Выбрать ${device.name} для объединения`
          : onOffCap
            ? `${device.name}: ${isOn ? 'выключить' : 'включить'}`
            : device.name
      }
      className={`group relative w-full text-left rounded-[24px] p-4 transition-all duration-120 disabled:opacity-75 cursor-pointer select-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 flex flex-col justify-between min-h-[146px] ${surfaceClass} ${
        isPairSelected ? 'ring-3 ring-blue-500 ring-offset-2 ring-offset-[#e6ebf3] dark:ring-offset-[#0b0f17]' : ''
      }`}
    >
      {isSwitch ? (
        <>
          {/* Clean Switch Layout: Room + LED at top, large 2-line Name in middle, crisp Status at bottom */}
          <div className="flex items-center justify-between gap-2 w-full">
            <span className={`text-xs font-semibold truncate ${metaClasses}`}>
              {roomName || 'Выключатель'}
            </span>
            <span className={`w-7 h-1.5 rounded-full shrink-0 transition-all ${ledClasses}`} aria-hidden="true" />
          </div>

          <div className="my-2 w-full">
            <h3 className="text-base sm:text-[17px] font-bold leading-snug line-clamp-2">
              {device.name}
            </h3>
          </div>

          <div className="w-full flex items-center justify-between gap-2">
            <span className={`text-xs font-semibold ${isOn ? 'text-white' : metaClasses}`}>
              {pairingMode ? (isPairSelected ? 'Выбрано' : 'Выбрать для пары') : isOn ? 'Включено' : 'Выключено'}
            </span>
          </div>
        </>
      ) : (
        <>
          {/* Other Devices Layout: Category Icon + LED / Metric, Room + Status bold + Name underneath */}
          <div className="flex items-start justify-between gap-2 w-full">
            <div className={`w-11 h-11 rounded-2xl flex items-center justify-center shrink-0 transition-colors ${iconSocketClasses}`}>
              <DeviceIcon type={device.type} className={`w-5 h-5 ${isBusy ? 'animate-pulse' : ''}`} />
            </div>

            <div className="flex flex-col items-end gap-1.5 pt-0.5">
              <span className={`w-6 h-1.5 rounded-full transition-all ${ledClasses}`} aria-hidden="true" />
              {metricBadge && (
                <span className="text-xs font-mono-tabular font-bold tracking-tight">
                  {metricBadge}
                </span>
              )}
            </div>
          </div>

          <div className="mt-4 w-full">
            <h3 className="text-sm sm:text-[15px] font-bold leading-snug line-clamp-2 tabular-nums">
              {summary}
            </h3>
            <p className={`mt-1 text-xs truncate ${metaClasses}`}>
              {device.name}
            </p>
          </div>
        </>
      )}
    </button>
  );
}
