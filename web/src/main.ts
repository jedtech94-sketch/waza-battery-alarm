import "./styles.css";
import { AlertController } from "./alert";
import { batterySupport, readBatteryOnce, subscribeBattery, type BatterySnapshot } from "./battery";
import { DEFAULT_SETTINGS, loadSettings, saveSettings } from "./settings";
import type { AlertDecision, AlertSeverity } from "./types";

function byId<T extends HTMLElement>(id: string): T {
  const element = document.getElementById(id);
  if (!element) throw new Error(`Missing element #${id}`);
  return element as T;
}

const monitoringEnabled = byId<HTMLInputElement>("monitoring-enabled");
const simpleMode = byId<HTMLInputElement>("simple-mode");
const alertLevels = byId<HTMLInputElement>("alert-levels");
const threshold = byId<HTMLInputElement>("threshold");
const thresholdOutput = byId<HTMLOutputElement>("threshold-output");
const warning = byId<HTMLInputElement>("warning");
const warningOutput = byId<HTMLOutputElement>("warning-output");
const critical = byId<HTMLInputElement>("critical");
const criticalOutput = byId<HTMLOutputElement>("critical-output");
const reminderMessage = byId<HTMLInputElement>("reminder-message");
const warningMessage = byId<HTMLInputElement>("warning-message");
const criticalMessage = byId<HTMLInputElement>("critical-message");
const repeat = byId<HTMLInputElement>("repeat");
const repeatOutput = byId<HTMLOutputElement>("repeat-output");
const snooze = byId<HTMLInputElement>("snooze");
const snoozeOutput = byId<HTMLOutputElement>("snooze-output");
const includeTimeLeft = byId<HTMLInputElement>("include-time-left");
const quietEnabled = byId<HTMLInputElement>("quiet-enabled");
const quietStart = byId<HTMLInputElement>("quiet-start");
const quietStartOutput = byId<HTMLOutputElement>("quiet-start-output");
const quietEnd = byId<HTMLInputElement>("quiet-end");
const quietEndOutput = byId<HTMLOutputElement>("quiet-end-output");
const criticalOverride = byId<HTMLInputElement>("critical-override");
const unplugEnabled = byId<HTMLInputElement>("unplug-enabled");
const unplugTarget = byId<HTMLInputElement>("unplug-target");
const unplugTargetOutput = byId<HTMLOutputElement>("unplug-target-output");
const unplugMessage = byId<HTMLInputElement>("unplug-message");
const slowEnabled = byId<HTMLInputElement>("slow-enabled");
const slowMinutes = byId<HTMLInputElement>("slow-minutes");
const slowMinutesOutput = byId<HTMLOutputElement>("slow-minutes-output");
const slowMessage = byId<HTMLInputElement>("slow-message");
const connectedMessage = byId<HTMLInputElement>("connected-message");
const voiceSelect = byId<HTMLSelectElement>("voice-select");
const soundSelect = byId<HTMLSelectElement>("sound-select");
const themeSelect = byId<HTMLSelectElement>("theme-select");
const voiceEnabled = byId<HTMLInputElement>("voice-enabled");
const soundEnabled = byId<HTMLInputElement>("sound-enabled");
const notificationEnabled = byId<HTMLInputElement>("notification-enabled");
const supportNote = byId<HTMLParagraphElement>("support-note");
const batteryLevel = byId<HTMLElement>("battery-level");
const powerState = byId<HTMLElement>("power-state");
const monitorState = byId<HTMLElement>("monitor-state");
const statusMessage = byId<HTMLParagraphElement>("status-message");
const enableButton = byId<HTMLButtonElement>("enable");
const disableButton = byId<HTMLButtonElement>("disable");
const testButton = byId<HTMLButtonElement>("test");
const snoozeButton = byId<HTMLButtonElement>("snooze-now");
const bottomNav = byId<HTMLElement>("bottom-nav");
const resetButton = byId<HTMLButtonElement>("reset-settings");

const support = batterySupport();
const alerts = new AlertController();
const settings = loadSettings();

let battery: BatterySnapshot | null = null;
let detachBattery: (() => void) | null = null;
let running = false;
let lastAlertAt = new Map<AlertSeverity, number>();
let snoozedUntil = 0;
let chargingSessionStartLevel: number | null = null;
let chargingSessionStartAt = 0;
let unplugAlerted = false;
let slowChargeAlerted = false;
let lastKnownCharging: boolean | null = null;
let emergencyActive = false;
let lastEmergencyAt = 0;

function setStatus(text: string): void {
  statusMessage.textContent = text;
}

function selectTab(name: string): void {
  document.querySelectorAll<HTMLElement>("[data-panel]").forEach((panel) => {
    panel.hidden = panel.dataset.panel !== name;
  });

  bottomNav.querySelectorAll<HTMLButtonElement>("button").forEach((button) => {
    button.classList.toggle("active", button.dataset.tab === name);
  });
}

const darkModeMedia = window.matchMedia("(prefers-color-scheme: dark)");

function applyTheme(): void {
  const dark = settings.themeMode === "dark" ||
    (settings.themeMode === "system" && darkModeMedia.matches);
  document.documentElement.dataset.theme = dark ? "dark" : "light";
}

function hourLabel(hour: number): string {
  return `${hour.toString().padStart(2, "0")}:00`;
}

function resetLowBatteryState(): void {
  lastAlertAt = new Map<AlertSeverity, number>();
  snoozedUntil = 0;
}

function resetAllState(): void {
  resetLowBatteryState();
  chargingSessionStartLevel = null;
  chargingSessionStartAt = 0;
  unplugAlerted = false;
  slowChargeAlerted = false;
  lastKnownCharging = null;
  emergencyActive = false;
  lastEmergencyAt = 0;
}

function renderSettings(): void {
  monitoringEnabled.checked = settings.monitoringEnabled;
  simpleMode.checked = settings.simpleMode;
  alertLevels.checked = settings.alertLevelsEnabled;

  threshold.value = String(settings.thresholdPercent);
  thresholdOutput.value = `${settings.thresholdPercent}%`;
  warning.value = String(settings.warningPercent);
  warningOutput.value = `${settings.warningPercent}%`;
  critical.value = String(settings.criticalPercent);
  criticalOutput.value = `${settings.criticalPercent}%`;

  reminderMessage.value = settings.voiceMessage;
  warningMessage.value = settings.warningMessage;
  criticalMessage.value = settings.criticalMessage;

  repeat.value = String(settings.repeatIntervalMinutes);
  repeatOutput.value = `${settings.repeatIntervalMinutes} min`;
  snooze.value = String(settings.snoozeMinutes);
  snoozeOutput.value = `${settings.snoozeMinutes} min`;
  includeTimeLeft.checked = settings.includeTimeLeft;

  quietEnabled.checked = settings.quietHoursEnabled;
  quietStart.value = String(settings.quietStartHour);
  quietStartOutput.value = hourLabel(settings.quietStartHour);
  quietEnd.value = String(settings.quietEndHour);
  quietEndOutput.value = hourLabel(settings.quietEndHour);
  criticalOverride.checked = settings.criticalOverridesQuietHours;

  unplugEnabled.checked = settings.unplugReminderEnabled;
  unplugTarget.value = String(settings.unplugTargetPercent);
  unplugTargetOutput.value = `${settings.unplugTargetPercent}%`;
  unplugMessage.value = settings.unplugMessage;
  slowEnabled.checked = settings.slowChargeWarningEnabled;
  slowMinutes.value = String(settings.slowChargeCheckMinutes);
  slowMinutesOutput.value = `${settings.slowChargeCheckMinutes} min`;
  slowMessage.value = settings.slowChargeMessage;

  connectedMessage.value = settings.chargerConnectedMessage;
  voiceSelect.value = settings.selectedVoiceURI;
  soundSelect.value = settings.selectedSoundKey;
  themeSelect.value = settings.themeMode;
  voiceEnabled.checked = settings.voiceEnabled;
  soundEnabled.checked = settings.soundEnabled;
  notificationEnabled.checked = settings.notificationEnabled;
  applySimpleVisibility();
}

function persist(): void {
  saveSettings(settings);
}

function applySimpleVisibility(): void {
  document.querySelectorAll<HTMLElement>("[data-simple]").forEach((element) => {
    element.hidden = !settings.simpleMode;
  });
  document.querySelectorAll<HTMLElement>("[data-advanced]").forEach((element) => {
    element.hidden = settings.simpleMode;
  });
}

function bindRange(
  input: HTMLInputElement,
  output: HTMLOutputElement,
  apply: (value: number) => void,
  format: (value: number) => string,
): void {
  input.addEventListener("input", () => {
    const value = Number(input.value);
    apply(value);
    output.value = format(value);
    persist();
    evaluate();
  });
}

function bindSettings(): void {
  monitoringEnabled.addEventListener("change", () => {
    if (monitoringEnabled.checked && !running) void enableMonitoring();
    if (!monitoringEnabled.checked && running) disableMonitoring();
  });

  simpleMode.addEventListener("change", () => {
    settings.simpleMode = simpleMode.checked;
    applySimpleVisibility();
    persist();
  });

  alertLevels.addEventListener("change", () => {
    settings.alertLevelsEnabled = alertLevels.checked;
    persist();
  });

  bindRange(threshold, thresholdOutput, (v) => (settings.thresholdPercent = v), (v) => `${v}%`);
  bindRange(warning, warningOutput, (v) => (settings.warningPercent = v), (v) => `${v}%`);
  bindRange(critical, criticalOutput, (v) => (settings.criticalPercent = v), (v) => `${v}%`);
  bindRange(repeat, repeatOutput, (v) => (settings.repeatIntervalMinutes = v), (v) => `${v} min`);
  bindRange(snooze, snoozeOutput, (v) => (settings.snoozeMinutes = v), (v) => `${v} min`);
  bindRange(quietStart, quietStartOutput, (v) => (settings.quietStartHour = v), hourLabel);
  bindRange(quietEnd, quietEndOutput, (v) => (settings.quietEndHour = v), hourLabel);
  bindRange(
    unplugTarget,
    unplugTargetOutput,
    (v) => (settings.unplugTargetPercent = v),
    (v) => `${v}%`,
  );
  bindRange(slowMinutes, slowMinutesOutput, (v) => (settings.slowChargeCheckMinutes = v), (v) => `${v} min`);

  const textBindings: Array<[HTMLInputElement, (value: string) => void]> = [
    [reminderMessage, (v) => (settings.voiceMessage = v)],
    [warningMessage, (v) => (settings.warningMessage = v)],
    [criticalMessage, (v) => (settings.criticalMessage = v)],
    [unplugMessage, (v) => (settings.unplugMessage = v)],
    [slowMessage, (v) => (settings.slowChargeMessage = v)],
    [connectedMessage, (v) => (settings.chargerConnectedMessage = v)],
  ];
  for (const [input, apply] of textBindings) {
    input.addEventListener("input", () => {
      apply(input.value);
      persist();
    });
  }

  const checkboxBindings: Array<[HTMLInputElement, (value: boolean) => void]> = [
    [includeTimeLeft, (v) => (settings.includeTimeLeft = v)],
    [quietEnabled, (v) => (settings.quietHoursEnabled = v)],
    [criticalOverride, (v) => (settings.criticalOverridesQuietHours = v)],
    [unplugEnabled, (v) => (settings.unplugReminderEnabled = v)],
    [slowEnabled, (v) => (settings.slowChargeWarningEnabled = v)],
    [voiceEnabled, (v) => (settings.voiceEnabled = v)],
    [soundEnabled, (v) => (settings.soundEnabled = v)],
    [notificationEnabled, (v) => (settings.notificationEnabled = v)],
  ];
  for (const [input, apply] of checkboxBindings) {
    input.addEventListener("change", () => {
      apply(input.checked);
      persist();
    });
  }

  voiceSelect.addEventListener("change", () => {
    settings.selectedVoiceURI = voiceSelect.value;
    alerts.setVoice(settings.selectedVoiceURI);
    persist();
  });

  soundSelect.addEventListener("change", () => {
    settings.selectedSoundKey = soundSelect.value;
    persist();
  });

  themeSelect.addEventListener("change", () => {
    const mode = themeSelect.value;
    settings.themeMode = mode === "light" || mode === "dark" ? mode : "system";
    applyTheme();
    persist();
  });
}

function populateVoices(): void {
  if (!("speechSynthesis" in window)) return;

  voiceSelect.replaceChildren();
  const defaultOption = document.createElement("option");
  defaultOption.value = "";
  defaultOption.textContent = "System default";
  voiceSelect.append(defaultOption);

  for (const voice of window.speechSynthesis.getVoices()) {
    const option = document.createElement("option");
    option.value = voice.voiceURI;
    option.textContent = `${voice.name} (${voice.lang})`;
    voiceSelect.append(option);
  }

  voiceSelect.value = settings.selectedVoiceURI;
}

function updateUi(): void {
  supportNote.textContent = support.supported
    ? "Battery API available. Keep this tab open while monitoring."
    : (support.reason ?? "Battery monitoring is unavailable in this browser.");
  supportNote.classList.toggle("warning", !support.supported);

  batteryLevel.textContent = battery ? `${battery.percent}%` : "—";
  powerState.textContent = battery ? (battery.charging ? "Charging" : "On battery") : "—";
  monitorState.textContent = running ? "Active" : "Off";

  enableButton.disabled = running || !support.supported;
  disableButton.disabled = !running;
  snoozeButton.disabled = !running;
  monitoringEnabled.checked = running;
}

function isQuietHours(now: number): boolean {
  if (!settings.quietHoursEnabled) return false;

  const hour = new Date(now).getHours();
  const start = settings.quietStartHour;
  const end = settings.quietEndHour;

  return start <= end ? hour >= start && hour < end : hour >= start || hour < end;
}

function severityFor(percent: number): AlertSeverity {
  if (!settings.alertLevelsEnabled) return "reminder";
  if (percent <= settings.criticalPercent) return "critical";
  if (percent <= settings.warningPercent) return "warning";
  return "reminder";
}

function decide(snapshot: BatterySnapshot, now: number): AlertDecision & { clear?: boolean } {
  if (snapshot.charging) {
    emergencyActive = false;
    lastEmergencyAt = 0;

    if (settings.simpleMode) {
      resetLowBatteryState();
      return { kind: null, severity: "reminder", silent: false, clear: true };
    }

    if (chargingSessionStartLevel === null) {
      chargingSessionStartLevel = snapshot.percent;
      chargingSessionStartAt = now;
      unplugAlerted = false;
      slowChargeAlerted = false;
      resetLowBatteryState();
    }

    const quiet = isQuietHours(now);

    if (
      settings.unplugReminderEnabled &&
      snapshot.percent >= settings.unplugTargetPercent &&
      !unplugAlerted &&
      now - chargingSessionStartAt >= 1_000
    ) {
      unplugAlerted = true;
      return { kind: "unplug", severity: "reminder", silent: quiet };
    }

    const checkMs = settings.slowChargeCheckMinutes * 60_000;
    const startLevel = chargingSessionStartLevel ?? snapshot.percent;

    if (
      settings.slowChargeWarningEnabled &&
      snapshot.percent < 95 &&
      !slowChargeAlerted &&
      now - chargingSessionStartAt >= checkMs &&
      snapshot.percent - startLevel < 1
    ) {
      slowChargeAlerted = true;
      return { kind: "slow", severity: "warning", silent: quiet };
    }

    return { kind: null, severity: "reminder", silent: false, clear: true };
  }

  chargingSessionStartLevel = null;
  unplugAlerted = false;
  slowChargeAlerted = false;

  if (emergencyActive || snapshot.percent <= settings.emergencyPercent) {
    emergencyActive = true;
    if (now - lastEmergencyAt >= 10_000) {
      lastEmergencyAt = now;
      return { kind: "low", severity: "critical", silent: false, emergency: true };
    }
    return { kind: null, severity: "critical", silent: false };
  }

  if (settings.simpleMode) {
    if (snapshot.percent > settings.thresholdPercent + 5) {
      resetLowBatteryState();
      return { kind: null, severity: "reminder", silent: false, clear: true };
    }

    if (snapshot.percent > settings.thresholdPercent) {
      return { kind: null, severity: "reminder", silent: false };
    }

    const last = lastAlertAt.get("reminder") ?? 0;
    const repeatMs = settings.repeatIntervalMinutes * 60_000;

    if (now >= snoozedUntil && now - last >= repeatMs) {
      lastAlertAt.set("reminder", now);
      return { kind: "low", severity: "reminder", silent: false };
    }

    return { kind: null, severity: "reminder", silent: false };
  }

  if (snapshot.percent > settings.thresholdPercent + 5) {
    resetLowBatteryState();
    return { kind: null, severity: "reminder", silent: false, clear: true };
  }

  if (snapshot.percent > settings.thresholdPercent) {
    return { kind: null, severity: "reminder", silent: false };
  }

  const severity = severityFor(snapshot.percent);
  const quiet = isQuietHours(now);
  const silent = quiet && !(severity === "critical" && settings.criticalOverridesQuietHours);
  const last = lastAlertAt.get(severity) ?? 0;
  const repeatMs = settings.repeatIntervalMinutes * 60_000;

  if (now >= snoozedUntil && now - last >= repeatMs) {
    lastAlertAt.set(severity, now);
    return { kind: "low", severity, silent };
  }

  return { kind: null, severity, silent };
}

function withPercent(base: string, percent: number | undefined): string {
  const cleaned = base.trim().replace(/[.!?]+$/, "");
  const batteryText = percent === undefined ? "Battery is low." : `Battery is at ${percent} percent.`;
  return `${cleaned}. ${batteryText}`;
}

function timeLeftText(): string {
  if (!settings.includeTimeLeft || !battery || battery.charging) return "";
  const seconds = battery.dischargingTime;
  if (!Number.isFinite(seconds) || seconds <= 0) return "";
  const minutes = Math.max(1, Math.round(seconds / 60));
  return ` About ${minutes} minutes left.`;
}

function messageFor(decision: AlertDecision): string {
  const percent = battery?.percent;

  if (decision.emergency) {
    return withPercent("Battery critical. Connect the charger now", percent);
  }

  if (settings.simpleMode && decision.kind === "low") {
    return withPercent("Battery low. Connect the charger", percent);
  }

  if (decision.kind === "unplug") return withPercent(settings.unplugMessage, percent);
  if (decision.kind === "slow") return withPercent(settings.slowChargeMessage, percent);

  const base = decision.severity === "critical"
    ? settings.criticalMessage
    : decision.severity === "warning"
      ? settings.warningMessage
      : settings.voiceMessage;

  return `${withPercent(base, percent).trimEnd()}${timeLeftText()}`;
}

function fireDecision(decision: AlertDecision): void {
  alerts.play(messageFor(decision), settings, decision.emergency === true || settings.simpleMode);
  const label = decision.kind === "unplug"
    ? "Unplug reminder"
    : decision.kind === "slow"
      ? "Slow-charge warning"
      : `${decision.severity} alert`;
  setStatus(`${label} sent at ${new Date().toLocaleTimeString()}.`);
}

function evaluate(): void {
  updateUi();
  if (!running || !battery) return;

  const decision = decide(battery, Date.now());
  if (decision.clear) {
    alerts.stop();
    resetLowBatteryState();
  }
  if (decision.kind) fireDecision(decision);
}

async function enableMonitoring(): Promise<void> {
  if (!support.supported) {
    setStatus(support.reason ?? "Battery monitoring is unavailable in this browser.");
    return;
  }

  try {
    await alerts.prime();
    alerts.setVoice(settings.selectedVoiceURI);

    if (settings.notificationEnabled) {
      await alerts.requestNotificationPermission();
    }

    resetAllState();
    detachBattery = await subscribeBattery((snapshot) => {
      const justConnected = snapshot.charging && lastKnownCharging === false;
      lastKnownCharging = snapshot.charging;
      battery = snapshot;

      evaluate();

      if (justConnected && running && settings.voiceEnabled) {
        alerts.speak(
          settings.chargerConnectedMessage.trim() || "Charger connected",
        );
        setStatus("Charger connected. Low-battery alerts cancelled.");
      }
    });

    running = true;
    settings.monitoringEnabled = true;
    persist();
    setStatus("Monitoring is active. Keep this tab open.");
  } catch (error) {
    setStatus(error instanceof Error ? error.message : "Could not start monitoring.");
  }

  updateUi();
}

function disableMonitoring(): void {
  detachBattery?.();
  detachBattery = null;
  running = false;
  settings.monitoringEnabled = false;
  persist();
  alerts.stop();
  resetAllState();
  setStatus("Monitoring disabled.");
  updateUi();
}

async function testAlert(): Promise<void> {
  await alerts.prime();
  alerts.setVoice(settings.selectedVoiceURI);

  if (!battery && support.supported) {
    try {
      battery = await readBatteryOnce();
    } catch {
      // A test alert can still run without a battery reading.
    }
  }

  fireDecision({ kind: "low", severity: "reminder", silent: false });
}

function snoozeAlerts(): void {
  snoozedUntil = Date.now() + settings.snoozeMinutes * 60_000;
  alerts.stop();
  setStatus(`Snoozed until ${new Date(snoozedUntil).toLocaleTimeString()}.`);
  updateUi();
}

bottomNav.querySelectorAll<HTMLButtonElement>("button").forEach((button) => {
  button.addEventListener("click", () => {
    const tab = button.dataset.tab;
    if (tab) selectTab(tab);
  });
});

resetButton.addEventListener("click", () => {
  disableMonitoring();
  Object.assign(settings, DEFAULT_SETTINGS);
  alerts.setVoice(settings.selectedVoiceURI);
  applyTheme();
  renderSettings();
  persist();
  setStatus("All settings restored to defaults.");
});

enableButton.addEventListener("click", () => void enableMonitoring());
disableButton.addEventListener("click", disableMonitoring);
testButton.addEventListener("click", () => void testAlert());
snoozeButton.addEventListener("click", snoozeAlerts);

renderSettings();
applyTheme();
populateVoices();
bindSettings();
selectTab("alerts");
updateUi();
setStatus("Ready. Enable alerts to start monitoring.");

darkModeMedia.addEventListener("change", () => {
  applyTheme();
});

if ("speechSynthesis" in window) {
  window.speechSynthesis.addEventListener("voiceschanged", populateVoices);
}

window.setInterval(evaluate, 5_000);

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    void navigator.serviceWorker.register("/sw.js").catch(() => undefined);
  });
}
