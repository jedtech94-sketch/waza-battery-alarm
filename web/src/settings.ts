import type { AppSettings } from "./types";

const STORAGE_KEY = "waza-battery-alarm.settings.v1";

export const DEFAULT_SETTINGS: AppSettings = {
  monitoringEnabled: false,
  thresholdPercent: 20,
  warningPercent: 10,
  criticalPercent: 6,
  emergencyPercent: 6,
  simpleMode: true,
  alertLevelsEnabled: true,
  repeatIntervalMinutes: 5,
  snoozeMinutes: 5,
  voiceMessage: "Please connect the charger",
  warningMessage: "Battery is very low. Connect the charger now.",
  criticalMessage: "Battery critical. Connect the charger immediately.",
  chargerConnectedMessage: "Charger connected",
  quietHoursEnabled: true,
  quietStartHour: 22,
  quietEndHour: 7,
  criticalOverridesQuietHours: true,
  unplugReminderEnabled: true,
  unplugTargetPercent: 80,
  unplugMessage: "Battery reached the target. You can unplug the charger.",
  slowChargeWarningEnabled: true,
  slowChargeCheckMinutes: 7,
  slowChargeMessage: "Slow charger detected. Check the cable.",
  includeTimeLeft: true,
  selectedVoiceURI: "",
  selectedSoundKey: "built_in",
  themeMode: "system",
  voiceEnabled: true,
  soundEnabled: true,
  notificationEnabled: true,
};

export function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function text(value: unknown, fallback: string): string {
  return typeof value === "string" && value.trim() ? value.trim() : fallback;
}

export function normalizeSettings(input: Partial<AppSettings>): AppSettings {
  return {
    monitoringEnabled: input.monitoringEnabled ?? DEFAULT_SETTINGS.monitoringEnabled,
    thresholdPercent: clamp(
      Number(input.thresholdPercent ?? DEFAULT_SETTINGS.thresholdPercent),
      5,
      50,
    ),
    warningPercent: clamp(Number(input.warningPercent ?? DEFAULT_SETTINGS.warningPercent), 1, 50),
    criticalPercent: clamp(Number(input.criticalPercent ?? DEFAULT_SETTINGS.criticalPercent), 1, 50),
    emergencyPercent: clamp(Number(input.emergencyPercent ?? DEFAULT_SETTINGS.emergencyPercent), 1, 20),
    simpleMode: input.simpleMode ?? DEFAULT_SETTINGS.simpleMode,
    alertLevelsEnabled: input.alertLevelsEnabled ?? DEFAULT_SETTINGS.alertLevelsEnabled,
    repeatIntervalMinutes: clamp(
      Number(input.repeatIntervalMinutes ?? DEFAULT_SETTINGS.repeatIntervalMinutes),
      1,
      60,
    ),
    snoozeMinutes: clamp(Number(input.snoozeMinutes ?? DEFAULT_SETTINGS.snoozeMinutes), 1, 60),
    voiceMessage: text(input.voiceMessage, DEFAULT_SETTINGS.voiceMessage),
    warningMessage: text(input.warningMessage, DEFAULT_SETTINGS.warningMessage),
    criticalMessage: text(input.criticalMessage, DEFAULT_SETTINGS.criticalMessage),
    chargerConnectedMessage: text(
      input.chargerConnectedMessage,
      DEFAULT_SETTINGS.chargerConnectedMessage,
    ),
    quietHoursEnabled: input.quietHoursEnabled ?? DEFAULT_SETTINGS.quietHoursEnabled,
    quietStartHour: clamp(Number(input.quietStartHour ?? DEFAULT_SETTINGS.quietStartHour), 0, 23),
    quietEndHour: clamp(Number(input.quietEndHour ?? DEFAULT_SETTINGS.quietEndHour), 0, 23),
    criticalOverridesQuietHours:
      input.criticalOverridesQuietHours ?? DEFAULT_SETTINGS.criticalOverridesQuietHours,
    unplugReminderEnabled:
      input.unplugReminderEnabled ?? DEFAULT_SETTINGS.unplugReminderEnabled,
    unplugTargetPercent: clamp(
      Number(input.unplugTargetPercent ?? DEFAULT_SETTINGS.unplugTargetPercent),
      50,
      100,
    ),
    unplugMessage: text(input.unplugMessage, DEFAULT_SETTINGS.unplugMessage),
    slowChargeWarningEnabled:
      input.slowChargeWarningEnabled ?? DEFAULT_SETTINGS.slowChargeWarningEnabled,
    slowChargeCheckMinutes: clamp(
      Number(input.slowChargeCheckMinutes ?? DEFAULT_SETTINGS.slowChargeCheckMinutes),
      1,
      60,
    ),
    slowChargeMessage: text(input.slowChargeMessage, DEFAULT_SETTINGS.slowChargeMessage),
    includeTimeLeft: input.includeTimeLeft ?? DEFAULT_SETTINGS.includeTimeLeft,
    selectedVoiceURI: typeof input.selectedVoiceURI === "string" ? input.selectedVoiceURI : "",
    selectedSoundKey: text(input.selectedSoundKey, DEFAULT_SETTINGS.selectedSoundKey),
    themeMode:
      input.themeMode === "light" || input.themeMode === "dark"
        ? input.themeMode
        : "system",
    voiceEnabled: input.voiceEnabled ?? DEFAULT_SETTINGS.voiceEnabled,
    soundEnabled: input.soundEnabled ?? DEFAULT_SETTINGS.soundEnabled,
    notificationEnabled: input.notificationEnabled ?? DEFAULT_SETTINGS.notificationEnabled,
  };
}

export function loadSettings(): AppSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULT_SETTINGS };
    return normalizeSettings(JSON.parse(raw) as Partial<AppSettings>);
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

export function saveSettings(settings: AppSettings): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch {
    // Storage can be unavailable in private browsing; settings still work for this session.
  }
}
