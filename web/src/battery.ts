import { clamp } from "./settings";
import type { BatterySnapshot } from "./types";

export type { BatterySnapshot } from "./types";

interface BatteryManagerLike extends EventTarget {
  readonly charging: boolean;
  readonly chargingTime: number;
  readonly dischargingTime: number;
  readonly level: number;
}

interface NavigatorWithBattery extends Navigator {
  getBattery?: () => Promise<BatteryManagerLike>;
}

export interface BatterySupport {
  supported: boolean;
  reason?: string;
}

const BATTERY_EVENTS = [
  "levelchange",
  "chargingchange",
  "chargingtimechange",
  "dischargingtimechange",
] as const;

function batteryNavigator(): NavigatorWithBattery {
  return navigator as NavigatorWithBattery;
}

export function batterySupport(): BatterySupport {
  if (typeof batteryNavigator().getBattery !== "function") {
    return {
      supported: false,
      reason: "This browser does not expose battery information. Use the Android app instead.",
    };
  }

  if (!window.isSecureContext) {
    return {
      supported: false,
      reason: "Battery access requires HTTPS or localhost.",
    };
  }

  return { supported: true };
}

function readSnapshot(battery: BatteryManagerLike): BatterySnapshot {
  return {
    percent: Math.round(clamp(battery.level, 0, 1) * 100),
    charging: Boolean(battery.charging),
    chargingTime: battery.chargingTime,
    dischargingTime: battery.dischargingTime,
  };
}

export async function readBatteryOnce(): Promise<BatterySnapshot> {
  const support = batterySupport();
  if (!support.supported) throw new Error(support.reason ?? "Battery API unavailable.");

  const getBattery = batteryNavigator().getBattery;
  if (!getBattery) throw new Error("Battery API unavailable.");

  const battery = await getBattery.call(batteryNavigator());
  return readSnapshot(battery);
}

export async function subscribeBattery(
  onSnapshot: (snapshot: BatterySnapshot) => void,
): Promise<() => void> {
  const support = batterySupport();
  if (!support.supported) throw new Error(support.reason ?? "Battery API unavailable.");

  const getBattery = batteryNavigator().getBattery;
  if (!getBattery) throw new Error("Battery API unavailable.");

  const battery = await getBattery.call(batteryNavigator());
  const emit = () => onSnapshot(readSnapshot(battery));

  for (const event of BATTERY_EVENTS) {
    battery.addEventListener(event, emit);
  }
  emit();

  return () => {
    for (const event of BATTERY_EVENTS) {
      battery.removeEventListener(event, emit);
    }
  };
}
