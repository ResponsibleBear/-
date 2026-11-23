export interface YandexCapability {
  retrievable: boolean;
  type: string;
  parameters?: Record<string, any>;
  state?: {
    instance: string;
    value: any;
  } | null;
  last_updated?: number;
}

export interface YandexProperty {
  retrievable: boolean;
  type: string;
  parameters?: {
    instance?: string;
    unit?: string;
    events?: Array<{ value: string; name?: string }>;
  };
  state?: {
    instance: string;
    value: any;
  } | null;
  last_updated?: number;
}

export interface YandexDevice {
  id: string;
  name: string;
  aliases: string[];
  type: string;
  external_id?: string;
  skill_id?: string;
  household_id?: string;
  room: string | null;
  groups: string[];
  capabilities: YandexCapability[];
  properties: YandexProperty[];
}

export interface YandexRoom {
  id: string;
  name: string;
  household_id?: string;
  devices: string[];
}

export interface YandexGroup {
  id: string;
  name: string;
  aliases?: string[];
  household_id?: string;
  type: string;
  devices: string[];
  capabilities: YandexCapability[];
  state?: string;
}

export interface YandexScenario {
  id: string;
  name: string;
  is_active: boolean;
  icon?: string;
}

export interface YandexHousehold {
  id: string;
  name: string;
  type?: string;
}

export interface YandexUserInfo {
  status: string;
  request_id?: string;
  rooms: YandexRoom[];
  groups: YandexGroup[];
  devices: YandexDevice[];
  scenarios: YandexScenario[];
  households: YandexHousehold[];
}

export interface YandexProfile {
  displayName: string;
  login?: string;
  avatarUrl?: string;
  authMethod: 'oauth' | 'token';
}

export interface AppRuntimeConfig {
  appUrl: string;
  callbackUrl: string;
  yandexOAuthConfigured: boolean;
}

export interface DeviceAction {
  type: string;
  state: {
    instance: string;
    value: any;
  };
}
