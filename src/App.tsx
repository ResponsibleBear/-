import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  LayoutGrid,
  Link2,
  Play,
  Power,
  RefreshCw,
  Search,
  Settings,
  Sparkles,
  ToggleRight,
  WifiOff,
  X,
} from 'lucide-react';
import { ConnectYandexScreen } from './components/ConnectYandexScreen';
import { DeviceCard } from './components/DeviceCard';
import { DoubleSwitchCard } from './components/DoubleSwitchCard';
import { ScenariosView } from './components/ScenariosView';
import { SettingsView, type ThemePreference } from './components/SettingsView';
import { useOnlineStatus } from './hooks/useOnlineStatus';
import type {
  AppRuntimeConfig,
  DeviceAction,
  YandexDevice,
  YandexGroup,
  YandexProfile,
  YandexScenario,
  YandexUserInfo,
} from './types/smartHome';
import {
  fetchAuthStatus,
  fetchDeviceState,
  fetchRuntimeConfig,
  fetchSmartHomeInfo,
  logoutYandex,
  runYandexScenario,
  sendBatchDeviceActions,
  sendDeviceActions,
  sendGroupActions,
  setSessionToken,
} from './utils/api';
import { groupDoubleSwitches, makeSortedPairKey } from './utils/switchGrouping';
import {
  type DeviceCategoryKey,
  getDeviceCategory,
  getOnOffCapability,
  isDeviceOn,
} from './utils/yandexHelpers';

type ActiveTab = 'switches' | 'other' | 'scenarios' | 'settings';

const PREFS_STORAGE_KEY = 'alice_android_prefs_v1';
const SWITCH_PAIRS_STORAGE_KEY = 'alice_switch_pairs_v1';

interface SavedSwitchPairs {
  manualPairs: Array<[string, string]>;
  blockedPairs: string[];
}

function loadSavedSwitchPairs(): SavedSwitchPairs {
  try {
    const raw = localStorage.getItem(SWITCH_PAIRS_STORAGE_KEY);
    if (!raw) return { manualPairs: [], blockedPairs: [] };
    const parsed = JSON.parse(raw) as Partial<SavedSwitchPairs>;
    const manualPairs = Array.isArray(parsed.manualPairs)
      ? parsed.manualPairs.filter(
          (p): p is [string, string] => Array.isArray(p) && p.length === 2 && typeof p[0] === 'string' && typeof p[1] === 'string'
        )
      : [];
    const blockedPairs = Array.isArray(parsed.blockedPairs)
      ? parsed.blockedPairs.filter((s): s is string => typeof s === 'string')
      : [];
    return { manualPairs, blockedPairs };
  } catch {
    return { manualPairs: [], blockedPairs: [] };
  }
}

interface SavedPrefs {
  themePref: ThemePreference;
  hapticsEnabled: boolean;
  autoRefreshSec: number;
  selectedHouseholdId: string;
}

function loadSavedPrefs(): SavedPrefs {
  try {
    const raw = localStorage.getItem(PREFS_STORAGE_KEY);
    if (!raw) {
      return { themePref: 'auto', hapticsEnabled: true, autoRefreshSec: 30, selectedHouseholdId: 'all' };
    }
    const parsed = JSON.parse(raw) as Partial<SavedPrefs>;
    return {
      themePref: parsed.themePref === 'light' || parsed.themePref === 'dark' ? parsed.themePref : 'auto',
      hapticsEnabled: typeof parsed.hapticsEnabled === 'boolean' ? parsed.hapticsEnabled : true,
      autoRefreshSec: typeof parsed.autoRefreshSec === 'number' ? parsed.autoRefreshSec : 30,
      selectedHouseholdId: typeof parsed.selectedHouseholdId === 'string' ? parsed.selectedHouseholdId : 'all',
    };
  } catch {
    return { themePref: 'auto', hapticsEnabled: true, autoRefreshSec: 30, selectedHouseholdId: 'all' };
  }
}

const CATEGORY_TABS: Array<{ id: DeviceCategoryKey; label: string }> = [
  { id: 'all', label: 'Все типы' },
  { id: 'light', label: 'Свет' },
  { id: 'climate', label: 'Климат' },
  { id: 'power', label: 'Розетки' },
  { id: 'appliances', label: 'Техника' },
  { id: 'media', label: 'Медиа' },
  { id: 'sensor', label: 'Датчики' },
];

export default function App() {
  const initialPrefs = useMemo(() => loadSavedPrefs(), []);
  const isOnline = useOnlineStatus();

  const [hapticsEnabled, setHapticsEnabled] = useState(initialPrefs.hapticsEnabled);
  const [themePref, setThemePref] = useState<ThemePreference>(initialPrefs.themePref);
  const [autoRefreshSec, setAutoRefreshSec] = useState<number>(initialPrefs.autoRefreshSec);
  const [selectedHouseholdId, setSelectedHouseholdId] = useState<string>(initialPrefs.selectedHouseholdId);

  const [config, setConfig] = useState<AppRuntimeConfig | null>(null);
  const [bootLoading, setBootLoading] = useState(true);
  const [authenticated, setAuthenticated] = useState(false);
  const [profile, setProfile] = useState<YandexProfile | null>(null);

  const [smartHome, setSmartHome] = useState<YandexUserInfo | null>(null);
  const [homeLoading, setHomeLoading] = useState(false);
  const [homeError, setHomeError] = useState<string | null>(null);
  const [toast, setToast] = useState<{ text: string; isError?: boolean } | null>(null);

  const [activeTab, setActiveTab] = useState<ActiveTab>('switches');
  const [selectedCategory, setSelectedCategory] = useState<DeviceCategoryKey>('all');
  const [searchQuery, setSearchQuery] = useState('');

  const [busyDeviceIds, setBusyDeviceIds] = useState<Record<string, boolean>>({});
  const initialPairs = useMemo(() => loadSavedSwitchPairs(), []);
  const [manualPairs, setManualPairs] = useState<Array<[string, string]>>(initialPairs.manualPairs);
  const [blockedPairKeys, setBlockedPairKeys] = useState<string[]>(initialPairs.blockedPairs);
  const [pairingMode, setPairingMode] = useState(false);
  const [firstPairCandidate, setFirstPairCandidate] = useState<YandexDevice | null>(null);

  // Persist UI preferences automatically
  useEffect(() => {
    try {
      const payload: SavedPrefs = {
        themePref,
        hapticsEnabled,
        autoRefreshSec,
        selectedHouseholdId,
      };
      localStorage.setItem(PREFS_STORAGE_KEY, JSON.stringify(payload));
    } catch {
      // Ignore storage quota errors
    }
  }, [themePref, hapticsEnabled, autoRefreshSec, selectedHouseholdId]);

  useEffect(() => {
    try {
      const payload: SavedSwitchPairs = {
        manualPairs,
        blockedPairs: blockedPairKeys,
      };
      localStorage.setItem(SWITCH_PAIRS_STORAGE_KEY, JSON.stringify(payload));
    } catch {
      // Ignore storage quota errors
    }
  }, [manualPairs, blockedPairKeys]);

  // Android Vibration API haptics
  const hapticImpact = useCallback(
    (style: 'light' | 'medium' | 'heavy' = 'light') => {
      if (!hapticsEnabled) return;
      try {
        if ('vibrate' in navigator) {
          navigator.vibrate(style === 'heavy' ? 24 : style === 'medium' ? 14 : 8);
        }
      } catch {
        // Ignore on unsupported browsers
      }
    },
    [hapticsEnabled]
  );

  const hapticNotify = useCallback(
    (type: 'success' | 'warning' | 'error' = 'success') => {
      if (!hapticsEnabled) return;
      try {
        if ('vibrate' in navigator) {
          if (type === 'error') navigator.vibrate([20, 40, 20]);
          else navigator.vibrate(12);
        }
      } catch {
        // Ignore
      }
    },
    [hapticsEnabled]
  );

  // Resolve and apply dark/light theme + Android theme-color meta
  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const resolveDark = () => {
      if (themePref === 'dark') return true;
      if (themePref === 'light') return false;
      return mq.matches;
    };

    const apply = () => {
      const dark = resolveDark();
      document.documentElement.classList.toggle('dark', dark);
      const themeMeta = document.querySelector('meta[name="theme-color"]');
      if (themeMeta) {
        themeMeta.setAttribute('content', dark ? '#090b10' : '#eef1f7');
      }
    };

    apply();
    mq.addEventListener('change', apply);
    return () => mq.removeEventListener('change', apply);
  }, [themePref]);

  const showToast = useCallback((text: string, isError = false) => {
    setToast({ text, isError });
    setTimeout(() => {
      setToast((curr) => (curr?.text === text ? null : curr));
    }, 3200);
  }, []);

  const loadSmartHome = useCallback(async (silent = false) => {
    if (!silent) setHomeLoading(true);
    setHomeError(null);
    try {
      const data = await fetchSmartHomeInfo();
      setSmartHome(data);
    } catch (err: any) {
      if (err?.status === 401) {
        setAuthenticated(false);
        setSessionToken(null);
        setSmartHome(null);
      } else {
        setHomeError(err?.message || 'Не удалось загрузить устройства Яндекса');
      }
    } finally {
      if (!silent) setHomeLoading(false);
    }
  }, []);

  // Boot sequence: config + persistent server session check
  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const [cfg, auth] = await Promise.all([fetchRuntimeConfig(), fetchAuthStatus()]);
        if (!active) return;
        setConfig(cfg);

        if (auth.authenticated) {
          if (auth.sessionToken) setSessionToken(auth.sessionToken);
          setProfile(auth.profile || null);
          setAuthenticated(true);
          await loadSmartHome();
        }
      } catch {
        // Ignore boot network hiccups
      } finally {
        if (active) setBootLoading(false);
      }
    })();

    return () => {
      active = false;
    };
  }, [loadSmartHome]);

  // Auto-refresh polling
  useEffect(() => {
    if (!authenticated || autoRefreshSec <= 0) return;
    const timer = setInterval(() => {
      loadSmartHome(true);
    }, autoRefreshSec * 1000);
    return () => clearInterval(timer);
  }, [authenticated, autoRefreshSec, loadSmartHome]);

  const handleAuthenticated = useCallback(
    async (sessionToken: string, userProfile: YandexProfile) => {
      setSessionToken(sessionToken);
      setProfile(userProfile);
      setAuthenticated(true);
      await loadSmartHome();
    },
    [loadSmartHome]
  );

  const handleLogout = useCallback(async () => {
    hapticImpact('medium');
    try {
      await logoutYandex();
    } finally {
      setSessionToken(null);
      setAuthenticated(false);
      setProfile(null);
      setSmartHome(null);
      setActiveTab('switches');
    }
  }, [hapticImpact]);

  const applyOptimisticActions = useCallback((deviceId: string, actions: DeviceAction[]) => {
    setSmartHome((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        devices: prev.devices.map((dev) => {
          if (dev.id !== deviceId) return dev;
          const nextCaps = dev.capabilities.map((cap) => {
            const match = actions.find(
              (a) =>
                a.type === cap.type &&
                (cap.parameters?.instance === a.state.instance ||
                  cap.state?.instance === a.state.instance ||
                  a.type === 'devices.capabilities.color_setting' ||
                  a.type === 'devices.capabilities.on_off')
            );
            if (!match) return cap;
            return {
              ...cap,
              state: {
                instance: match.state.instance,
                value: match.state.value,
              },
            };
          });
          return { ...dev, capabilities: nextCaps };
        }),
      };
    });
  }, []);

  const handleExecuteActions = useCallback(
    async (deviceId: string, actions: DeviceAction[]) => {
      const previousSnapshot = smartHome;
      setBusyDeviceIds((prev) => ({ ...prev, [deviceId]: true }));
      applyOptimisticActions(deviceId, actions);

      try {
        const result = await sendDeviceActions(deviceId, actions);
        const capResult = result.devices?.[0]?.capabilities?.[0]?.state?.action_result;
        if (capResult && capResult.status === 'ERROR') {
          throw new Error(capResult.error_message || 'Устройство не ответило на команду');
        }
        hapticNotify('success');
      } catch (err: any) {
        hapticNotify('error');
        if (previousSnapshot) setSmartHome(previousSnapshot);
        showToast(err?.message || 'Не удалось выполнить команду', true);
      } finally {
        setBusyDeviceIds((prev) => ({ ...prev, [deviceId]: false }));
      }
    },
    [smartHome, applyOptimisticActions, hapticNotify, showToast]
  );

  const handleTogglePower = useCallback(
    (device: YandexDevice) => {
      const cap = getOnOffCapability(device);
      if (!cap) return;
      const nextValue = !isDeviceOn(device);
      hapticImpact('medium');
      handleExecuteActions(device.id, [
        {
          type: 'devices.capabilities.on_off',
          state: { instance: 'on', value: nextValue },
        },
      ]);
    },
    [handleExecuteActions, hapticImpact]
  );

  const handleRefreshSingleDevice = useCallback(
    async (deviceId: string) => {
      try {
        const updated = await fetchDeviceState(deviceId);
        setSmartHome((prev) => {
          if (!prev) return prev;
          return {
            ...prev,
            devices: prev.devices.map((d) => (d.id === deviceId ? { ...d, ...updated } : d)),
          };
        });
        hapticNotify('success');
      } catch (err: any) {
        hapticNotify('error');
        showToast(err?.message || 'Ошибка обновления устройства', true);
      }
    },
    [hapticNotify, showToast]
  );

  const handleRunScenario = useCallback(
    async (scenario: YandexScenario) => {
      hapticImpact('medium');
      try {
        await runYandexScenario(scenario.id);
        hapticNotify('success');
        showToast(`Сценарий «${scenario.name}» запущен`);
        setTimeout(() => loadSmartHome(true), 800);
      } catch (err: any) {
        hapticNotify('error');
        showToast(err?.message || 'Не удалось запустить сценарий', true);
      }
    },
    [hapticImpact, hapticNotify, showToast, loadSmartHome]
  );

  const handleToggleGroup = useCallback(
    async (group: YandexGroup, turnOn: boolean) => {
      hapticImpact('medium');
      try {
        await sendGroupActions(group.id, [
          {
            type: 'devices.capabilities.on_off',
            state: { instance: 'on', value: turnOn },
          },
        ]);
        hapticNotify('success');
        showToast(`${group.name}: ${turnOn ? 'включено' : 'выключено'}`);
        setTimeout(() => loadSmartHome(true), 500);
      } catch (err: any) {
        hapticNotify('error');
        showToast(err?.message || 'Ошибка управления группой', true);
      }
    },
    [hapticImpact, hapticNotify, showToast, loadSmartHome]
  );

  const households = smartHome?.households || [];

  const scopedRooms = useMemo(() => {
    const rooms = smartHome?.rooms || [];
    if (selectedHouseholdId === 'all') return rooms;
    return rooms.filter((r) => !r.household_id || r.household_id === selectedHouseholdId);
  }, [smartHome, selectedHouseholdId]);

  const scopedDevices = useMemo(() => {
    const devs = smartHome?.devices || [];
    if (selectedHouseholdId === 'all') return devs;
    return devs.filter((d) => !d.household_id || d.household_id === selectedHouseholdId);
  }, [smartHome, selectedHouseholdId]);

  const scopedGroups = useMemo(() => {
    const groups = smartHome?.groups || [];
    if (selectedHouseholdId === 'all') return groups;
    return groups.filter((g) => !g.household_id || g.household_id === selectedHouseholdId);
  }, [smartHome, selectedHouseholdId]);

  const roomNameMap = useMemo(() => new Map(scopedRooms.map((r) => [r.id, r.name])), [scopedRooms]);

  const switchDevices = useMemo(
    () => scopedDevices.filter((dev) => dev.type.startsWith('devices.types.switch')),
    [scopedDevices]
  );

  const otherDevices = useMemo(
    () => scopedDevices.filter((dev) => !dev.type.startsWith('devices.types.switch')),
    [scopedDevices]
  );

  const filteredDevices = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    const baseList = activeTab === 'switches' ? switchDevices : otherDevices;
    return baseList.filter((dev) => {
      if (activeTab === 'other' && selectedCategory !== 'all' && getDeviceCategory(dev.type) !== selectedCategory) {
        return false;
      }
      if (q && !dev.name.toLowerCase().includes(q)) return false;
      return true;
    });
  }, [activeTab, switchDevices, otherDevices, selectedCategory, searchQuery]);

  const blockedSet = useMemo(() => new Set(blockedPairKeys), [blockedPairKeys]);

  const switchGridItems = useMemo(() => {
    if (activeTab !== 'switches') return [];
    return groupDoubleSwitches(filteredDevices, manualPairs, blockedSet);
  }, [activeTab, filteredDevices, manualPairs, blockedSet]);

  const activeSwitchIds = useMemo(
    () => switchDevices.filter((d) => Boolean(getOnOffCapability(d)) && isDeviceOn(d)).map((d) => d.id),
    [switchDevices]
  );

  const handleTurnOffAllSwitches = useCallback(async () => {
    const idsToOff =
      activeSwitchIds.length > 0
        ? activeSwitchIds
        : switchDevices.filter((d) => Boolean(getOnOffCapability(d))).map((d) => d.id);
    if (idsToOff.length === 0) return;
    hapticImpact('heavy');
    const previousSnapshot = smartHome;
    const offAction: DeviceAction = { type: 'devices.capabilities.on_off', state: { instance: 'on', value: false } };
    setSmartHome((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        devices: prev.devices.map((dev) => {
          if (!idsToOff.includes(dev.id)) return dev;
          return {
            ...dev,
            capabilities: dev.capabilities.map((cap) =>
              cap.type === 'devices.capabilities.on_off'
                ? { ...cap, state: { instance: 'on', value: false } }
                : cap
            ),
          };
        }),
      };
    });
    try {
      await sendBatchDeviceActions(idsToOff, [offAction]);
      hapticNotify('success');
      showToast('Все выключатели отключены');
    } catch (err: any) {
      hapticNotify('error');
      if (previousSnapshot) setSmartHome(previousSnapshot);
      showToast(err?.message || 'Не удалось выключить устройства', true);
    }
  }, [activeSwitchIds, switchDevices, smartHome, hapticImpact, hapticNotify, showToast]);

  const handleSelectForPair = useCallback(
    (device: YandexDevice) => {
      hapticImpact('light');
      if (!firstPairCandidate) {
        setFirstPairCandidate(device);
        return;
      }
      if (firstPairCandidate.id === device.id) {
        setFirstPairCandidate(null);
        return;
      }
      const pKey = makeSortedPairKey(firstPairCandidate.id, device.id);
      setBlockedPairKeys((prev) => prev.filter((k) => k !== pKey));
      setManualPairs((prev) => {
        const cleaned = prev.filter(
          ([a, b]) =>
            a !== firstPairCandidate.id &&
            b !== firstPairCandidate.id &&
            a !== device.id &&
            b !== device.id
        );
        return [...cleaned, [firstPairCandidate.id, device.id]];
      });
      showToast(`Объединены: ${firstPairCandidate.name} + ${device.name}`);
      setFirstPairCandidate(null);
      setPairingMode(false);
    },
    [firstPairCandidate, hapticImpact, showToast]
  );

  const handleSplitPair = useCallback(
    (left: YandexDevice, right: YandexDevice) => {
      hapticImpact('medium');
      const pKey = makeSortedPairKey(left.id, right.id);
      setManualPairs((prev) =>
        prev.filter(
          ([a, b]) =>
            !((a === left.id && b === right.id) || (a === right.id && b === left.id))
        )
      );
      setBlockedPairKeys((prev) => (prev.includes(pKey) ? prev : [...prev, pKey]));
      showToast(`Разъединено: ${left.name} и ${right.name}`);
    },
    [hapticImpact, showToast]
  );

  const summaryStats = useMemo(() => {
    const targetList =
      activeTab === 'switches' ? switchDevices : activeTab === 'other' ? otherDevices : scopedDevices;
    const controllable = targetList.filter((d) => Boolean(getOnOffCapability(d)));
    const activeCount = controllable.filter((d) => isDeviceOn(d)).length;

    const temps: number[] = [];
    const humidities: number[] = [];
    for (const dev of scopedDevices) {
      for (const prop of dev.properties || []) {
        const inst = prop.state?.instance || prop.parameters?.instance;
        const val = prop.state?.value;
        if (typeof val === 'number') {
          if (inst === 'temperature') temps.push(val);
          if (inst === 'humidity') humidities.push(val);
        }
      }
    }

    const avgTemp = temps.length > 0 ? (temps.reduce((a, b) => a + b, 0) / temps.length).toFixed(1) : null;
    const avgHum = humidities.length > 0 ? Math.round(humidities.reduce((a, b) => a + b, 0) / humidities.length) : null;

    return {
      totalDevices: targetList.length,
      activeCount,
      avgTemp,
      avgHum,
    };
  }, [activeTab, switchDevices, otherDevices, scopedDevices]);

  if (bootLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center p-6">
        <div className="flex flex-col items-center gap-3 text-slate-500 dark:text-slate-400">
          <div className="w-11 h-11 rounded-2xl bg-blue-600 text-white flex items-center justify-center">
            <Sparkles className="w-5 h-5 animate-pulse" />
          </div>
          <p className="text-sm font-medium">Загрузка пульта Алисы...</p>
        </div>
      </div>
    );
  }

  if (!authenticated) {
    return (
      <ConnectYandexScreen
        config={config}
        onAuthenticated={handleAuthenticated}
        hapticImpact={hapticImpact}
        hapticNotify={hapticNotify}
      />
    );
  }

  const activeHouseholdName =
    selectedHouseholdId === 'all'
      ? households.length === 1
        ? households[0].name
        : 'Мой дом'
      : households.find((h) => h.id === selectedHouseholdId)?.name || 'Мой дом';

  const navItems: Array<{ id: ActiveTab; label: string; icon: typeof LayoutGrid }> = [
    { id: 'switches', label: 'Выключатели', icon: ToggleRight },
    { id: 'other', label: 'Остальное', icon: LayoutGrid },
    { id: 'scenarios', label: 'Сценарии', icon: Play },
    { id: 'settings', label: 'Настройки', icon: Settings },
  ];

  return (
    <div className="min-h-screen pb-24 md:pb-12">
      {/* Desktop Top Bar (3-Zone Contract: Brand — 4 Links — 1 Primary Action) */}
      <header className="hidden md:flex items-center justify-between gap-8 max-w-6xl mx-auto px-6 py-4 border-b border-slate-200/80 dark:border-white/[0.07]">
        <a
          href="#switches"
          onClick={(e) => {
            e.preventDefault();
            setActiveTab('switches');
          }}
          className="text-lg font-bold tracking-tight text-slate-900 dark:text-slate-100 whitespace-nowrap shrink-0"
        >
          Алиса Дом
        </a>

        <nav className="flex items-center gap-6 text-sm font-medium">
          {navItems.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => {
                hapticImpact('light');
                setActiveTab(item.id);
              }}
              className={`py-1 whitespace-nowrap shrink-0 transition-colors cursor-pointer ${
                activeTab === item.id
                  ? 'text-blue-600 dark:text-blue-400 font-semibold'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100'
              }`}
            >
              {item.label}
            </button>
          ))}
        </nav>

        <div className="flex items-center shrink-0">
          <button
            type="button"
            onClick={() => {
              hapticImpact('light');
              loadSmartHome();
            }}
            disabled={homeLoading}
            className="btn-3d-neutral h-[44px] px-4 rounded-[18px] text-slate-900 dark:text-slate-100 text-xs font-semibold flex items-center justify-center gap-2 disabled:opacity-60 whitespace-nowrap shrink-0 cursor-pointer transition-all duration-120"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${homeLoading ? 'animate-spin' : ''}`} />
            <span>Обновить</span>
          </button>
        </div>
      </header>

      {/* Main Content Container */}
      <main className="max-w-6xl mx-auto px-4 sm:px-6 pt-4 md:pt-7 space-y-5">
        {/* Scrollable Header / Stretched Turn-Off-All + Centered Icon Buttons */}
        <div className="flex items-center gap-2.5 w-full">
          {activeTab === 'switches' ? (
            <>
              <button
                type="button"
                onClick={handleTurnOffAllSwitches}
                disabled={switchDevices.filter((d) => Boolean(getOnOffCapability(d))).length === 0}
                className={`flex-1 h-[48px] px-5 rounded-[18px] text-sm font-bold flex items-center justify-center gap-2.5 whitespace-nowrap cursor-pointer disabled:opacity-50 transition-all duration-120 ${
                  activeSwitchIds.length > 0
                    ? 'btn-3d-primary text-white'
                    : 'btn-3d-neutral text-slate-900 dark:text-slate-100'
                }`}
              >
                <Power className="w-4 h-4 shrink-0" />
                <span>Выключить все</span>
              </button>

              <button
                type="button"
                onClick={() => {
                  hapticImpact('light');
                  setActiveTab('settings');
                }}
                aria-label="Настройки"
                className="btn-3d-neutral w-[48px] h-[48px] rounded-[18px] flex items-center justify-center text-slate-800 dark:text-slate-100 shrink-0 cursor-pointer transition-all duration-120"
              >
                <Settings className="w-4 h-4" />
              </button>

              <button
                type="button"
                onClick={() => {
                  hapticImpact('light');
                  loadSmartHome();
                }}
                aria-label="Обновить список устройств"
                className="btn-3d-neutral w-[48px] h-[48px] rounded-[18px] flex items-center justify-center text-slate-800 dark:text-slate-100 shrink-0 cursor-pointer transition-all duration-120"
              >
                <RefreshCw className={`w-4 h-4 ${homeLoading ? 'animate-spin' : ''}`} />
              </button>
            </>
          ) : (
            <>
              <div className="flex-1 min-w-0">
                <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100 truncate">
                  {activeTab === 'other' && `Остальные устройства · ${activeHouseholdName}`}
                  {activeTab === 'scenarios' && 'Сценарии и группы'}
                  {activeTab === 'settings' && 'Настройки'}
                </h1>
                <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 mt-0.5 tabular-nums">
                  {summaryStats.totalDevices} устройств · {summaryStats.activeCount} включено
                  {summaryStats.avgTemp ? ` · ${summaryStats.avgTemp}°C` : ''}
                  {summaryStats.avgHum !== null ? ` · ${summaryStats.avgHum}%` : ''}
                </p>
              </div>

              <button
                type="button"
                onClick={() => {
                  hapticImpact('light');
                  setActiveTab('settings');
                }}
                aria-label="Настройки"
                className="md:hidden btn-3d-neutral w-[48px] h-[48px] rounded-[18px] flex items-center justify-center text-slate-800 dark:text-slate-100 shrink-0 cursor-pointer transition-all duration-120"
              >
                <Settings className="w-4 h-4" />
              </button>

              <button
                type="button"
                onClick={() => {
                  hapticImpact('light');
                  loadSmartHome();
                }}
                aria-label="Обновить список устройств"
                className="md:hidden btn-3d-neutral w-[48px] h-[48px] rounded-[18px] flex items-center justify-center text-slate-800 dark:text-slate-100 shrink-0 cursor-pointer transition-all duration-120"
              >
                <RefreshCw className={`w-4 h-4 ${homeLoading ? 'animate-spin' : ''}`} />
              </button>
            </>
          )}
        </div>

        {/* Offline Banner */}
        {!isOnline && (
          <div
            role="status"
            className="p-3.5 rounded-2xl bg-amber-50 dark:bg-amber-950/40 border border-amber-300 dark:border-amber-800/60 flex items-center gap-2.5 text-xs sm:text-sm text-amber-800 dark:text-amber-200"
          >
            <WifiOff className="w-4 h-4 shrink-0" />
            <span>Нет подключения к интернету — отображаются последние данные</span>
          </div>
        )}

        {/* Error Banner */}
        {homeError && (
          <div
            role="alert"
            className="p-4 rounded-2xl bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900/60 flex items-center justify-between gap-3 text-sm text-red-700 dark:text-red-300"
          >
            <div className="flex items-center gap-2.5">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{homeError}</span>
            </div>
            <button
              type="button"
              onClick={() => loadSmartHome()}
              className="btn-3d-danger min-h-[40px] px-3.5 rounded-[16px] text-xs font-semibold shrink-0 cursor-pointer transition-all duration-120"
            >
              Повторить
            </button>
          </div>
        )}

        {/* TAB: SWITCHES OR OTHER */}
        {(activeTab === 'switches' || activeTab === 'other') && (
          <div className="space-y-5">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              {activeTab === 'switches' && switchDevices.length >= 2 && (
                <div className="flex items-center gap-2.5 flex-wrap">
                  <button
                    type="button"
                    onClick={() => {
                      hapticImpact('light');
                      setPairingMode((v) => !v);
                      setFirstPairCandidate(null);
                    }}
                    className={`min-h-[44px] px-4 py-2 rounded-[18px] text-xs font-bold flex items-center gap-1.5 whitespace-nowrap shrink-0 cursor-pointer transition-all duration-120 ${
                      pairingMode
                        ? 'btn-3d-primary text-white'
                        : 'btn-3d-neutral text-slate-700 dark:text-slate-200'
                    }`}
                  >
                    <Link2 className="w-3.5 h-3.5" />
                    <span>{pairingMode ? 'Готово' : '2-клавишные'}</span>
                  </button>
                </div>
              )}

              {activeTab === 'other' && (
                <div className="flex items-center gap-2.5 overflow-x-auto no-scrollbar pb-2">
                  {CATEGORY_TABS.map((cat) => (
                    <button
                      key={cat.id}
                      type="button"
                      onClick={() => {
                        hapticImpact('light');
                        setSelectedCategory(cat.id);
                      }}
                      className={`min-h-[42px] px-4 py-2 rounded-[18px] text-xs font-bold whitespace-nowrap shrink-0 transition-all duration-120 cursor-pointer ${
                        selectedCategory === cat.id
                          ? 'btn-3d-primary text-white'
                          : 'btn-3d-neutral text-slate-700 dark:text-slate-200'
                      }`}
                    >
                      {cat.label}
                    </button>
                  ))}
                </div>
              )}

              <div className="relative sm:w-64 shrink-0 sm:ml-auto">
                <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  type="search"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder={activeTab === 'switches' ? 'Поиск выключателя...' : 'Поиск устройства...'}
                  className="w-full min-h-[44px] pl-10 pr-9 py-2 rounded-[18px] bg-white dark:bg-[#151a25] border border-slate-300/80 dark:border-[#273043] text-sm text-slate-900 dark:text-slate-100 placeholder:text-slate-400 shadow-inner focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                {searchQuery && (
                  <button
                    type="button"
                    onClick={() => setSearchQuery('')}
                    aria-label="Очистить поиск"
                    className="absolute right-2 top-1/2 -translate-y-1/2 w-8 h-8 flex items-center justify-center text-slate-400 hover:text-slate-600 cursor-pointer"
                  >
                    <X className="w-4 h-4" />
                  </button>
                )}
              </div>
            </div>

            {activeTab === 'switches' && pairingMode && (
              <div className="p-3.5 rounded-2xl bg-blue-500/10 border border-blue-500/30 flex items-center justify-between gap-3 text-xs sm:text-sm text-slate-800 dark:text-slate-100">
                <span>
                  {firstPairCandidate
                    ? `Выбран «${firstPairCandidate.name}». Нажмите на второй выключатель, чтобы объединить их в двухклавишный.`
                    : 'Нажмите последовательно на 2 одиночных выключателя для объединения в двухклавишный блок, либо нажмите «Разъединить» на паре.'}
                </span>
                <button
                  type="button"
                  onClick={() => {
                    setPairingMode(false);
                    setFirstPairCandidate(null);
                  }}
                  className="btn-3d-neutral min-h-[40px] px-3.5 rounded-[16px] text-xs font-bold shrink-0 cursor-pointer transition-all duration-120"
                >
                  Отмена
                </button>
              </div>
            )}

            {homeLoading && !smartHome ? (
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3.5">
                {Array.from({ length: 8 }).map((_, i) => (
                  <div
                    key={i}
                    className="h-[146px] rounded-[24px] keycap-3d-off animate-pulse"
                  />
                ))}
              </div>
            ) : filteredDevices.length === 0 ? (
              <div className="rounded-3xl keycap-3d-off p-8 text-center">
                <p className="text-sm font-semibold text-slate-900 dark:text-slate-100 mb-1">
                  {scopedDevices.length === 0
                    ? 'В вашем Яндекс Доме пока нет устройств'
                    : activeTab === 'switches'
                      ? 'Выключатели не найдены'
                      : 'По выбранному фильтру устройств нет'}
                </p>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  {scopedDevices.length === 0
                    ? 'Добавьте устройства в приложении Дом с Алисой, и они автоматически появятся здесь.'
                    : activeTab === 'switches'
                      ? 'Перейдите на вкладку «Остальное» — там находятся светильники, розетки, датчики и техника.'
                      : 'Попробуйте сбросить поиск или категорию.'}
                </p>
              </div>
            ) : activeTab === 'switches' ? (
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3.5 sm:gap-4">
                {switchGridItems.map((item) =>
                  item.kind === 'double' ? (
                    <DoubleSwitchCard
                      key={item.pairKey}
                      left={item.left}
                      right={item.right}
                      title={item.commonTitle}
                      roomName={item.left.room ? roomNameMap.get(item.left.room) : undefined}
                      busyDeviceIds={busyDeviceIds}
                      pairingMode={pairingMode}
                      onTogglePower={handleTogglePower}
                      onSplitPair={handleSplitPair}
                    />
                  ) : (
                    <DeviceCard
                      key={item.device.id}
                      device={item.device}
                      roomName={item.device.room ? roomNameMap.get(item.device.room) : undefined}
                      isBusy={Boolean(busyDeviceIds[item.device.id])}
                      pairingMode={pairingMode}
                      isPairSelected={firstPairCandidate?.id === item.device.id}
                      onTogglePower={handleTogglePower}
                      onSelectForPair={handleSelectForPair}
                      onRefreshDevice={(d) => {
                        hapticImpact('light');
                        handleRefreshSingleDevice(d.id);
                      }}
                    />
                  )
                )}
              </div>
            ) : (
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3.5 sm:gap-4">
                {filteredDevices.map((device) => (
                  <DeviceCard
                    key={device.id}
                    device={device}
                    roomName={device.room ? roomNameMap.get(device.room) : undefined}
                    isBusy={Boolean(busyDeviceIds[device.id])}
                    onTogglePower={handleTogglePower}
                    onRefreshDevice={(d) => {
                      hapticImpact('light');
                      handleRefreshSingleDevice(d.id);
                    }}
                  />
                ))}
              </div>
            )}
          </div>
        )}

        {/* TAB: SCENARIOS & GROUPS */}
        {activeTab === 'scenarios' && (
          <ScenariosView
            scenarios={smartHome?.scenarios || []}
            groups={scopedGroups}
            onRunScenario={handleRunScenario}
            onToggleGroup={handleToggleGroup}
          />
        )}

        {/* TAB: SETTINGS */}
        {activeTab === 'settings' && (
          <SettingsView
            profile={profile}
            config={config}
            households={households}
            selectedHouseholdId={selectedHouseholdId}
            onSelectHousehold={(id) => {
              hapticImpact('light');
              setSelectedHouseholdId(id);
            }}
            themePref={themePref}
            onChangeTheme={(pref) => {
              hapticImpact('light');
              setThemePref(pref);
            }}
            hapticsEnabled={hapticsEnabled}
            onToggleHaptics={() => setHapticsEnabled((v) => !v)}
            autoRefreshSec={autoRefreshSec}
            onChangeAutoRefresh={(sec) => {
              hapticImpact('light');
              setAutoRefreshSec(sec);
            }}
            onLogout={handleLogout}
          />
        )}
      </main>

      {/* Mobile Bottom Navigation Bar */}
      <nav
        aria-label="Основная навигация"
        className="md:hidden fixed bottom-0 left-0 right-0 z-40 bg-white/95 dark:bg-[#141923]/95 backdrop-blur-md border-t border-slate-200/80 dark:border-[#273043] pb-safe"
      >
        <div className="grid grid-cols-4 h-14">
          {navItems.map((item) => {
            const Icon = item.icon;
            const active = activeTab === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => {
                  hapticImpact('light');
                  setActiveTab(item.id);
                }}
                className={`flex flex-col items-center justify-center gap-0.5 min-h-[44px] transition-colors cursor-pointer ${
                  active
                    ? 'text-blue-600 dark:text-blue-400'
                    : 'text-slate-500 dark:text-slate-400'
                }`}
              >
                <Icon className="w-5 h-5" />
                <span className="text-[10px] font-semibold tracking-tight whitespace-nowrap">
                  {item.label}
                </span>
              </button>
            );
          })}
        </div>
      </nav>

      {/* Feedback Toast */}
      {toast && (
        <div
          role="status"
          className={`fixed bottom-20 md:bottom-6 left-1/2 -translate-x-1/2 z-50 px-4 py-2.5 rounded-2xl text-xs sm:text-sm font-medium shadow-lg whitespace-nowrap max-w-[90vw] truncate ${
            toast.isError
              ? 'bg-red-600 text-white'
              : 'bg-slate-900 dark:bg-slate-100 text-white dark:text-slate-900'
          }`}
        >
          {toast.text}
        </div>
      )}
    </div>
  );
}
