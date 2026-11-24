import { Unlink2 } from 'lucide-react';
import type { YandexDevice } from '../types/smartHome';
import { getOnOffCapability, isDeviceOn } from '../utils/yandexHelpers';

interface DoubleSwitchCardProps {
  left: YandexDevice;
  right: YandexDevice;
  title: string;
  roomName?: string;
  busyDeviceIds: Record<string, boolean>;
  pairingMode: boolean;
  onTogglePower: (device: YandexDevice) => void;
  onSplitPair: (left: YandexDevice, right: YandexDevice) => void;
}

export function DoubleSwitchCard({
  left,
  right,
  title,
  roomName,
  busyDeviceIds,
  pairingMode,
  onTogglePower,
  onSplitPair,
}: DoubleSwitchCardProps) {
  const leftOn = isDeviceOn(left);
  const rightOn = isDeviceOn(right);
  const activeCount = (leftOn ? 1 : 0) + (rightOn ? 1 : 0);

  const plateSummary = [
    roomName || title,
    activeCount === 2 ? 'Обе вкл' : activeCount === 1 ? '1 из 2 вкл' : 'Выключено',
  ].join(' · ');

  const renderRockerKey = (dev: YandexDevice, fallbackLabel: string) => {
    const isOn = isDeviceOn(dev);
    const isBusy = Boolean(busyDeviceIds[dev.id]);
    const hasOnOff = Boolean(getOnOffCapability(dev));

    return (
      <button
        key={dev.id}
        type="button"
        disabled={isBusy || !hasOnOff}
        onClick={() => onTogglePower(dev)}
        aria-pressed={isOn}
        aria-label={`${dev.name}: ${isOn ? 'выключить' : 'включить'}`}
        className={`relative rounded-[18px] p-3.5 text-left transition-all duration-120 flex flex-col justify-between min-h-[112px] cursor-pointer select-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:opacity-75 ${
          isOn ? 'keycap-3d-emerald text-white' : 'keycap-3d-off text-slate-900 dark:text-slate-100'
        }`}
      >
        {/* Hardware LED bar at top right (no toggle-switch icon clutter) */}
        <div className="flex items-center justify-end w-full">
          <span
            aria-hidden="true"
            className={`w-7 h-1.5 rounded-full transition-all ${
              isOn
                ? 'bg-white shadow-[0_0_10px_rgba(255,255,255,0.9)]'
                : 'bg-slate-300 dark:bg-slate-700 shadow-inner'
            }`}
          />
        </div>

        {/* Generous 2-line Equipment Name + Crisp Status */}
        <div className="my-1.5 w-full">
          <div className="text-sm sm:text-[15px] font-bold leading-snug line-clamp-2">
            {dev.name || fallbackLabel}
          </div>
        </div>

        <div
          className={`text-xs font-semibold ${
            isOn ? 'text-white/90' : 'text-slate-500 dark:text-slate-400'
          }`}
        >
          {isOn ? 'Включено' : 'Выключено'}
        </div>
      </button>
    );
  };

  return (
    <div className="col-span-2 plate-recess-3d rounded-[26px] p-3 flex flex-col justify-between min-h-[146px]">
      {/* Wall switch outer frame header */}
      <div className="flex items-center justify-between gap-2 px-1 pb-2.5">
        <div className="min-w-0">
          <h3 className="text-xs sm:text-sm font-bold text-slate-900 dark:text-slate-100 truncate tabular-nums">
            {plateSummary}
          </h3>
        </div>

        {pairingMode && (
          <button
            type="button"
            onClick={() => onSplitPair(left, right)}
            className="btn-3d-neutral min-h-[34px] px-2.5 py-1 rounded-xl text-[11px] font-semibold text-slate-700 dark:text-slate-200 flex items-center gap-1.5 shrink-0 cursor-pointer"
          >
            <Unlink2 className="w-3.5 h-3.5" />
            <span>Разъединить</span>
          </button>
        )}
      </div>

      {/* Twin 3D Rocker Paddles side-by-side inside the wall plate well */}
      <div className="grid grid-cols-2 gap-2.5 pb-1">
        {renderRockerKey(left, 'Клавиша 1')}
        {renderRockerKey(right, 'Клавиша 2')}
      </div>
    </div>
  );
}
