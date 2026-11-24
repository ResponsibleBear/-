import type { YandexDevice } from '../types/smartHome';

export type SwitchGridItem =
  | { kind: 'single'; device: YandexDevice }
  | {
      kind: 'double';
      pairKey: string;
      left: YandexDevice;
      right: YandexDevice;
      isAuto: boolean;
      commonTitle: string;
    };

export function makeSortedPairKey(idA: string, idB: string): string {
  return idA < idB ? `${idA}|${idB}` : `${idB}|${idA}`;
}

function cleanStem(s: string): string {
  return s
    .replace(/[\s\-_.:/()[\]]+$/g, '')
    .replace(/^[\s\-_.:/()[\]]+/g, '')
    .trim();
}

export function extractExternalIdStem(externalId?: string): string | null {
  if (!externalId) return null;
  const trimmed = externalId.trim();
  if (!trimmed) return null;

  // Explicit channel/switch keyword suffix: e.g. bf123-switch_1, dev.ch2, aqara_gang_left
  const keywordMatch = trimmed.match(
    /^(.+?)[-_.:/](?:switch|channel|ch|gang|button|btn|ep|key|relay)[-_.:/]?(?:[1-4]|left|right|l|r)$/i
  );
  if (keywordMatch && keywordMatch[1].length >= 3) {
    return keywordMatch[1].toLowerCase();
  }

  // Left/right suffix: e.g. lumi123_left / lumi123_right
  const sideMatch = trimmed.match(/^(.+?)[-_.:/](?:left|right)$/i);
  if (sideMatch && sideMatch[1].length >= 4) {
    return sideMatch[1].toLowerCase();
  }

  // Numeric channel suffix after delimiter when base id is substantial: e.g. 0x00158d0001_1
  const numMatch = trimmed.match(/^(.+?)[-_.:/]([12])$/);
  if (numMatch && numMatch[1].length >= 5) {
    return numMatch[1].toLowerCase();
  }

  return null;
}

export function extractNameStemAndSide(name: string): { stem: string | null; sideRank: number } {
  const trimmed = name.trim();
  if (!trimmed) return { stem: null, sideRank: 2 };

  // Side keywords at end (e.g. "Коридор левый", "Спальня правая", "Левая клавиша")
  const sidePatterns: Array<{ regex: RegExp; rank: number }> = [
    { regex: /^(.*?)(?:\s*[-–—(/]?\s*(?:левый|левая|левое|слева|left)\s*\)?)$/i, rank: 0 },
    { regex: /^(.*?)(?:\s*[-–—(/]?\s*(?:правый|правая|правое|справа|right)\s*\)?)$/i, rank: 1 },
    {
      regex: /^(.*?)(?:\s*[-–—(/]?\s*(?:клавиша|кнопка|канал|линия|switch|key|button|gang|ch)?\s*#?\s*(1|I|L)\s*\)?)$/i,
      rank: 0,
    },
    {
      regex: /^(.*?)(?:\s*[-–—(/]?\s*(?:клавиша|кнопка|канал|линия|switch|key|button|gang|ch)?\s*#?\s*(2|II|R)\s*\)?)$/i,
      rank: 1,
    },
  ];

  for (const { regex, rank } of sidePatterns) {
    const m = trimmed.match(regex);
    if (m) {
      const stem = cleanStem(m[1] || '');
      // Avoid stripping when the whole name was e.g. just a number
      return { stem: stem.toLowerCase(), sideRank: rank };
    }
  }

  return { stem: null, sideRank: 2 };
}

function orderPairDevices(a: YandexDevice, b: YandexDevice): [YandexDevice, YandexDevice] {
  const sideA = extractNameStemAndSide(a.name).sideRank;
  const sideB = extractNameStemAndSide(b.name).sideRank;
  if (sideA !== sideB) {
    return sideA < sideB ? [a, b] : [b, a];
  }
  const extA = a.external_id || '';
  const extB = b.external_id || '';
  if (extA && extB && extA !== extB) {
    return extA.localeCompare(extB, 'ru') <= 0 ? [a, b] : [b, a];
  }
  return a.name.localeCompare(b.name, 'ru') <= 0 ? [a, b] : [b, a];
}

function computeCommonTitle(left: YandexDevice, right: YandexDevice): string {
  const sL = extractNameStemAndSide(left.name).stem;
  const sR = extractNameStemAndSide(right.name).stem;
  if (sL && sR && sL === sR && sL.length >= 2) {
    // Recover original casing from left.name prefix
    const rawPrefix = cleanStem(left.name.slice(0, Math.min(left.name.length, sL.length + 2)));
    if (rawPrefix.length >= 2) return rawPrefix;
  }
  return 'Двухклавишный выключатель';
}

export function groupDoubleSwitches(
  devices: YandexDevice[],
  manualPairs: Array<[string, string]>,
  blockedPairKeys: Set<string>
): SwitchGridItem[] {
  const byId = new Map(devices.map((d) => [d.id, d]));
  const pairedPartner = new Map<string, { partner: YandexDevice; isAuto: boolean }>();

  // 1. Apply manual pairs first
  for (const [idA, idB] of manualPairs) {
    if (idA === idB) continue;
    const devA = byId.get(idA);
    const devB = byId.get(idB);
    if (!devA || !devB) continue;
    if (pairedPartner.has(idA) || pairedPartner.has(idB)) continue;
    pairedPartner.set(idA, { partner: devB, isAuto: false });
    pairedPartner.set(idB, { partner: devA, isAuto: false });
  }

  // 2. Auto-detect by external_id stem within same room + skill_id
  const extBuckets = new Map<string, YandexDevice[]>();
  for (const dev of devices) {
    if (pairedPartner.has(dev.id)) continue;
    const extStem = extractExternalIdStem(dev.external_id);
    if (!extStem) continue;
    const bucketKey = `${dev.room || ''}|${dev.skill_id || ''}|${extStem}`;
    const list = extBuckets.get(bucketKey) || [];
    list.push(dev);
    extBuckets.set(bucketKey, list);
  }
  for (const group of extBuckets.values()) {
    if (group.length === 2) {
      const [a, b] = group;
      const pKey = makeSortedPairKey(a.id, b.id);
      if (!blockedPairKeys.has(pKey) && !pairedPartner.has(a.id) && !pairedPartner.has(b.id)) {
        pairedPartner.set(a.id, { partner: b, isAuto: true });
        pairedPartner.set(b.id, { partner: a, isAuto: true });
      }
    }
  }

  // 3. Auto-detect by name stem within same room + skill_id
  const nameBuckets = new Map<string, YandexDevice[]>();
  for (const dev of devices) {
    if (pairedPartner.has(dev.id)) continue;
    const { stem } = extractNameStemAndSide(dev.name);
    if (stem === null) continue;
    // If no room is assigned, require a meaningful non-empty stem
    if (!dev.room && stem.length < 2) continue;
    const bucketKey = `${dev.room || ''}|${dev.skill_id || ''}|${stem}`;
    const list = nameBuckets.get(bucketKey) || [];
    list.push(dev);
    nameBuckets.set(bucketKey, list);
  }
  for (const group of nameBuckets.values()) {
    if (group.length === 2) {
      const [a, b] = group;
      const pKey = makeSortedPairKey(a.id, b.id);
      if (!blockedPairKeys.has(pKey) && !pairedPartner.has(a.id) && !pairedPartner.has(b.id)) {
        pairedPartner.set(a.id, { partner: b, isAuto: true });
        pairedPartner.set(b.id, { partner: a, isAuto: true });
      }
    }
  }

  // 4. Emit ordered grid items preserving original position of the first device in each pair
  const emitted = new Set<string>();
  const items: SwitchGridItem[] = [];

  for (const dev of devices) {
    if (emitted.has(dev.id)) continue;
    const pairInfo = pairedPartner.get(dev.id);
    if (pairInfo) {
      emitted.add(dev.id);
      emitted.add(pairInfo.partner.id);
      const [left, right] = pairInfo.isAuto
        ? orderPairDevices(dev, pairInfo.partner)
        : [dev, pairInfo.partner];
      items.push({
        kind: 'double',
        pairKey: makeSortedPairKey(left.id, right.id),
        left,
        right,
        isAuto: pairInfo.isAuto,
        commonTitle: computeCommonTitle(left, right),
      });
    } else {
      emitted.add(dev.id);
      items.push({ kind: 'single', device: dev });
    }
  }

  return items;
}
