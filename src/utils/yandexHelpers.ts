import type { YandexCapability, YandexDevice, YandexProperty } from '../types/smartHome';

export function getDeviceTypeLabel(type: string): string {
  if (type.startsWith('devices.types.light.strip')) return 'Светодиодная лента';
  if (type.startsWith('devices.types.light.ceiling')) return 'Люстра';
  if (type.startsWith('devices.types.light')) return 'Освещение';
  if (type.startsWith('devices.types.socket')) return 'Умная розетка';
  if (type.startsWith('devices.types.switch')) return 'Выключатель';
  if (type.startsWith('devices.types.thermostat.ac')) return 'Кондиционер';
  if (type.startsWith('devices.types.thermostat')) return 'Термостат';
  if (type.startsWith('devices.types.humidifier')) return 'Увлажнитель';
  if (type.startsWith('devices.types.purifier')) return 'Очиститель воздуха';
  if (type.startsWith('devices.types.vacuum_cleaner')) return 'Робот-пылесос';
  if (type.startsWith('devices.types.cooking.kettle')) return 'Умный чайник';
  if (type.startsWith('devices.types.cooking.coffee_maker')) return 'Кофеварка';
  if (type.startsWith('devices.types.cooking')) return 'Кухонный прибор';
  if (type.startsWith('devices.types.openable.curtain')) return 'Умные шторы';
  if (type.startsWith('devices.types.openable')) return 'Замок / Привод';
  if (type.startsWith('devices.types.media_device.tv')) return 'Телевизор';
  if (type.startsWith('devices.types.media_device')) return 'Медиаустройство';
  if (type.startsWith('devices.types.smart_speaker')) return 'Яндекс Станция';
  if (type.startsWith('devices.types.camera')) return 'Камера';
  if (type.startsWith('devices.types.sensor')) return 'Датчик';
  if (type.startsWith('devices.types.ventilation.fan')) return 'Вентилятор';
  if (type.startsWith('devices.types.washing_machine')) return 'Стиральная машина';
  if (type.startsWith('devices.types.iron')) return 'Утюг';
  return 'Умное устройство';
}

export type DeviceCategoryKey = 'all' | 'light' | 'climate' | 'power' | 'appliances' | 'media' | 'sensor';

export function getDeviceCategory(type: string): Exclude<DeviceCategoryKey, 'all'> {
  if (type.startsWith('devices.types.light')) return 'light';
  if (
    type.startsWith('devices.types.thermostat') ||
    type.startsWith('devices.types.humidifier') ||
    type.startsWith('devices.types.purifier') ||
    type.startsWith('devices.types.ventilation')
  ) {
    return 'climate';
  }
  if (type.startsWith('devices.types.socket') || type.startsWith('devices.types.switch')) return 'power';
  if (type.startsWith('devices.types.sensor')) return 'sensor';
  if (
    type.startsWith('devices.types.media_device') ||
    type.startsWith('devices.types.smart_speaker') ||
    type.startsWith('devices.types.camera')
  ) {
    return 'media';
  }
  return 'appliances';
}

export function getOnOffCapability(device: YandexDevice): YandexCapability | undefined {
  return device.capabilities.find((c) => c.type === 'devices.capabilities.on_off');
}

export function isDeviceOn(device: YandexDevice): boolean {
  const cap = getOnOffCapability(device);
  return cap?.state?.value === true;
}

export function getRangeCapability(device: YandexDevice, instance: string): YandexCapability | undefined {
  return device.capabilities.find(
    (c) => c.type === 'devices.capabilities.range' && (c.parameters?.instance === instance || c.state?.instance === instance)
  );
}

export function getColorCapability(device: YandexDevice): YandexCapability | undefined {
  return device.capabilities.find((c) => c.type === 'devices.capabilities.color_setting');
}

export function getModeCapabilities(device: YandexDevice): YandexCapability[] {
  return device.capabilities.filter((c) => c.type === 'devices.capabilities.mode');
}

export function getToggleCapabilities(device: YandexDevice): YandexCapability[] {
  return device.capabilities.filter((c) => c.type === 'devices.capabilities.toggle');
}

export function translateModeInstance(instance: string): string {
  const map: Record<string, string> = {
    thermostat: 'Режим климата',
    fan_speed: 'Скорость вентилятора',
    work_speed: 'Скорость работы',
    cleanup_mode: 'Режим уборки',
    program: 'Программа',
    swing: 'Направление потока',
    input_source: 'Источник сигнала',
    tea_mode: 'Режим чая',
    coffee_mode: 'Напиток',
    heat: 'Уровень нагрева',
    dishwashing: 'Мойка',
  };
  return map[instance] || instance;
}

export function translateModeValue(value: string): string {
  const map: Record<string, string> = {
    auto: 'Авто',
    cool: 'Охлаждение',
    heat: 'Обогрев',
    dry: 'Осушение',
    fan_only: 'Вентиляция',
    eco: 'Эко',
    quiet: 'Тихий',
    low: 'Низкая',
    medium: 'Средняя',
    high: 'Высокая',
    turbo: 'Турбо',
    normal: 'Обычный',
    fast: 'Быстрый',
    slow: 'Медленный',
    wet_cleaning: 'Влажная уборка',
    dry_cleaning: 'Сухая уборка',
    mixed_cleaning: 'Комбинированная',
    express: 'Экспресс',
    intensive: 'Интенсивная',
    vertical: 'Вертикально',
    horizontal: 'Горизонтально',
    stationary: 'Фиксированно',
    black_tea: 'Чёрный чай',
    green_tea: 'Зелёный чай',
    oolong_tea: 'Улун',
    puerh_tea: 'Пуэр',
    white_tea: 'Белый чай',
    herbal_tea: 'Травяной чай',
    flower_tea: 'Цветочный чай',
    boil: 'Кипячение',
    one: '1',
    two: '2',
    three: '3',
    four: '4',
  };
  return map[value] || value;
}

export function translateToggleInstance(instance: string): string {
  const map: Record<string, string> = {
    backlight: 'Подсветка',
    controls_locked: 'Блокировка от детей',
    ionization: 'Ионизация',
    keep_warm: 'Поддержание тепла',
    mute: 'Без звука',
    oscillation: 'Вращение',
    pause: 'Пауза',
    trunk: 'Багажник',
  };
  return map[instance] || instance;
}

export function translateColorScene(sceneId: string): string {
  const map: Record<string, string> = {
    alarm: 'Тревога',
    alice: 'Алиса',
    candle: 'Свеча',
    dinner: 'Ужин',
    fantasy: 'Фантазия',
    garland: 'Гирлянда',
    jungle: 'Джунгли',
    movie: 'Кино',
    neon: 'Неон',
    night: 'Ночь',
    ocean: 'Океан',
    party: 'Вечеринка',
    reading: 'Чтение',
    rest: 'Отдых',
    romance: 'Романтика',
    siren: 'Сирена',
    sunset: 'Закат',
  };
  return map[sceneId] || sceneId;
}

export function formatPropertyInfo(prop: YandexProperty): {
  label: string;
  valueText: string;
  isAlert: boolean;
  instance: string;
} {
  const instance = prop.state?.instance || prop.parameters?.instance || '';
  const val = prop.state?.value;

  const labelMap: Record<string, string> = {
    temperature: 'Температура',
    humidity: 'Влажность',
    pressure: 'Давление',
    co2_level: 'CO₂',
    pm1_density: 'PM1',
    pm2_5_density: 'PM2.5',
    pm10_density: 'PM10',
    tvoc: 'TVOC',
    power: 'Мощность',
    voltage: 'Напряжение',
    amperage: 'Сила тока',
    battery_level: 'Заряд',
    water_level: 'Уровень воды',
    illumination: 'Освещённость',
    food_level: 'Корм',
    meter: 'Счётчик',
    motion: 'Движение',
    open: 'Открытие',
    water_leak: 'Протечка',
    smoke: 'Дым',
    gas: 'Газ',
    vibration: 'Вибрация',
    button: 'Кнопка',
  };

  const label = labelMap[instance] || instance || 'Показатель';

  if (val === undefined || val === null) {
    return { label, valueText: '—', isAlert: false, instance };
  }

  if (prop.type === 'devices.properties.event' || typeof val === 'string') {
    const strVal = String(val);
    const eventMap: Record<string, { text: string; alert: boolean }> = {
      detected: { text: 'Обнаружено', alert: true },
      not_detected: { text: 'Спокойно', alert: false },
      opened: { text: 'Открыто', alert: true },
      closed: { text: 'Закрыто', alert: false },
      leak: { text: 'Протечка!', alert: true },
      dry: { text: 'Сухо', alert: false },
      high: { text: 'Высокий уровень', alert: true },
      normal: { text: 'Норма', alert: false },
      tilt: { text: 'Наклон', alert: true },
      fall: { text: 'Падение', alert: true },
      vibration: { text: 'Вибрация', alert: true },
      click: { text: 'Нажатие', alert: false },
      double_click: { text: 'Двойное нажатие', alert: false },
      long_press: { text: 'Удержание', alert: false },
    };
    const mapped = eventMap[strVal];
    return {
      label,
      valueText: mapped ? mapped.text : strVal,
      isAlert: mapped ? mapped.alert : false,
      instance,
    };
  }

  const num = Number(val);
  const formattedNum = Number.isInteger(num) ? String(num) : num.toFixed(1);

  const unitMap: Record<string, string> = {
    temperature: '°C',
    humidity: '%',
    pressure: 'мм',
    co2_level: 'ppm',
    power: 'Вт',
    voltage: 'В',
    amperage: 'А',
    battery_level: '%',
    water_level: '%',
    illumination: 'лк',
    pm2_5_density: 'мкг/м³',
  };
  const unit = unitMap[instance] || '';
  const isAlert =
    (instance === 'co2_level' && num >= 1200) ||
    (instance === 'battery_level' && num <= 15);

  return {
    label,
    valueText: unit ? `${formattedNum} ${unit}` : formattedNum,
    isAlert,
    instance,
  };
}

export function hsvToHex(h: number, s: number, v: number): string {
  const sNorm = Math.max(0, Math.min(100, s)) / 100;
  const vNorm = Math.max(0, Math.min(100, v)) / 100;
  const c = vNorm * sNorm;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = vNorm - c;

  let r = 0;
  let g = 0;
  let b = 0;
  if (h >= 0 && h < 60) {
    r = c; g = x; b = 0;
  } else if (h >= 60 && h < 120) {
    r = x; g = c; b = 0;
  } else if (h >= 120 && h < 180) {
    r = 0; g = c; b = x;
  } else if (h >= 180 && h < 240) {
    r = 0; g = x; b = c;
  } else if (h >= 240 && h < 300) {
    r = x; g = 0; b = c;
  } else {
    r = c; g = 0; b = x;
  }

  const toHex = (n: number) =>
    Math.round((n + m) * 255)
      .toString(16)
      .padStart(2, '0');
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

export function hexToHsv(hex: string): { h: number; s: number; v: number } {
  const clean = hex.replace('#', '');
  const r = parseInt(clean.slice(0, 2), 16) / 255;
  const g = parseInt(clean.slice(2, 4), 16) / 255;
  const b = parseInt(clean.slice(4, 6), 16) / 255;

  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const d = max - min;

  let h = 0;
  if (d !== 0) {
    if (max === r) h = ((g - b) / d) % 6;
    else if (max === g) h = (b - r) / d + 2;
    else h = (r - g) / d + 4;
    h = Math.round(h * 60);
    if (h < 0) h += 360;
  }
  const s = max === 0 ? 0 : Math.round((d / max) * 100);
  const v = Math.round(max * 100);
  return { h, s, v };
}

export function hexToRgbInt(hex: string): number {
  return parseInt(hex.replace('#', ''), 16);
}

export function rgbIntToHex(rgb: number): string {
  return `#${Math.max(0, Math.min(0xffffff, rgb)).toString(16).padStart(6, '0')}`;
}

export function getDevicePrimarySummary(device: YandexDevice, roomName?: string): string {
  const parts: string[] = [];
  if (roomName) parts.push(roomName);

  const onCap = getOnOffCapability(device);
  const brightness = getRangeCapability(device, 'brightness')?.state?.value;
  const tempTarget = getRangeCapability(device, 'temperature')?.state?.value;
  const openPos = getRangeCapability(device, 'open')?.state?.value;

  if (onCap) {
    const isOn = onCap.state?.value === true;
    if (isOn) {
      if (brightness !== undefined) parts.push(`${brightness}%`);
      else if (tempTarget !== undefined) parts.push(`${tempTarget}°C`);
      else parts.push('Включено');
    } else {
      parts.push('Выключено');
    }
  } else if (openPos !== undefined) {
    parts.push(`Открыто на ${openPos}%`);
  }

  const firstFloatProp = device.properties.find((p) => p.type === 'devices.properties.float' && p.state?.value !== undefined);
  if (firstFloatProp) {
    parts.push(formatPropertyInfo(firstFloatProp).valueText);
  } else {
    const firstEventProp = device.properties.find((p) => p.type === 'devices.properties.event' && p.state?.value !== undefined);
    if (firstEventProp) {
      parts.push(formatPropertyInfo(firstEventProp).valueText);
    }
  }

  return parts.join(' · ') || getDeviceTypeLabel(device.type);
}
