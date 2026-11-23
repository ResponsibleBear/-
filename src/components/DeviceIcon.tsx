import {
  AirVent,
  Blinds,
  Camera,
  Coffee,
  Droplets,
  Fan,
  Flame,
  Gauge,
  LampCeiling,
  Lightbulb,
  Lock,
  PlugZap,
  Radio,
  SlidersHorizontal,
  Speaker,
  Thermometer,
  ToggleLeft,
  Tv,
  Wind,
} from 'lucide-react';

interface DeviceIconProps {
  type: string;
  className?: string;
}

export function DeviceIcon({ type, className = 'w-5 h-5' }: DeviceIconProps) {
  if (type.startsWith('devices.types.light.ceiling')) return <LampCeiling className={className} />;
  if (type.startsWith('devices.types.light')) return <Lightbulb className={className} />;
  if (type.startsWith('devices.types.socket')) return <PlugZap className={className} />;
  if (type.startsWith('devices.types.switch')) return <ToggleLeft className={className} />;
  if (type.startsWith('devices.types.thermostat.ac')) return <AirVent className={className} />;
  if (type.startsWith('devices.types.thermostat')) return <Thermometer className={className} />;
  if (type.startsWith('devices.types.humidifier')) return <Droplets className={className} />;
  if (type.startsWith('devices.types.purifier')) return <Wind className={className} />;
  if (type.startsWith('devices.types.ventilation')) return <Fan className={className} />;
  if (type.startsWith('devices.types.cooking.kettle')) return <Flame className={className} />;
  if (type.startsWith('devices.types.cooking.coffee_maker')) return <Coffee className={className} />;
  if (type.startsWith('devices.types.openable.curtain')) return <Blinds className={className} />;
  if (type.startsWith('devices.types.openable')) return <Lock className={className} />;
  if (type.startsWith('devices.types.media_device.tv')) return <Tv className={className} />;
  if (type.startsWith('devices.types.media_device')) return <Radio className={className} />;
  if (type.startsWith('devices.types.smart_speaker')) return <Speaker className={className} />;
  if (type.startsWith('devices.types.camera')) return <Camera className={className} />;
  if (type.startsWith('devices.types.sensor')) return <Gauge className={className} />;
  return <SlidersHorizontal className={className} />;
}
