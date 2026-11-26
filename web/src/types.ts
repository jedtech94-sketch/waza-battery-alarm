export interface AppSettings {
  monitoringEnabled: boolean;
  thresholdPercent: number;
  warningPercent: number;
  criticalPercent: number;
  emergencyPercent: number;
  simpleMode: boolean;
  alertLevelsEnabled: boolean;
  repeatIntervalMinutes: number;
  snoozeMinutes: number;
  voiceMessage: string;
  warningMessage: string;
  criticalMessage: string;
  chargerConnectedMessage: string;
  chargingAnnouncementsEnabled: boolean;
  chargingStartedAnnouncementEnabled: boolean;
  chargingUnpluggedAnnouncementEnabled: boolean;
  chargingCompleteAnnouncementEnabled: boolean;
  chargingStartedMessage: string;
  chargingUnpluggedMessage: string;
  chargingCompleteMessage: string;
  quietHoursEnabled: boolean;
  quietStartHour: number;
  quietEndHour: number;
  criticalOverridesQuietHours: boolean;
  unplugReminderEnabled: boolean;
  unplugTargetPercent: number;
  unplugMessage: string;
  slowChargeWarningEnabled: boolean;
  slowChargeCheckMinutes: number;
  slowChargeMessage: string;
  includeTimeLeft: boolean;
  selectedVoiceURI: string;
  selectedSoundKey: string;
  themeMode: "system" | "light" | "dark";
  voiceEnabled: boolean;
  soundEnabled: boolean;
  notificationEnabled: boolean;
}

export interface BatterySnapshot {
  percent: number;
  charging: boolean;
  chargingTime: number;
  dischargingTime: number;
}

export type AlertSeverity = "reminder" | "warning" | "critical";
export type AlertKind = "low" | "unplug" | "slow";

export interface AlertDecision {
  kind: AlertKind | null;
  severity: AlertSeverity;
  silent: boolean;
  emergency?: boolean;
}
