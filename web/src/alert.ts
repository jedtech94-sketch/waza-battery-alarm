import type { AppSettings } from "./types";

type AudioContextConstructor = typeof AudioContext;

function audioContextConstructor(): AudioContextConstructor | undefined {
  const webkit = (window as unknown as { webkitAudioContext?: AudioContextConstructor })
    .webkitAudioContext;
  return window.AudioContext ?? webkit;
}

export class AlertController {
  private audioContext: AudioContext | null = null;
  private readonly oscillators = new Set<OscillatorNode>();
  private voiceURI: string | null = null;

  async prime(): Promise<void> {
    const AudioContextClass = audioContextConstructor();

    if (AudioContextClass) {
      this.audioContext ??= new AudioContextClass();
      if (this.audioContext.state === "suspended") {
        await this.audioContext.resume();
      }
    }

    if ("speechSynthesis" in window) {
      const warmup = new SpeechSynthesisUtterance("");
      warmup.volume = 0;
      window.speechSynthesis.speak(warmup);
    }
  }

  setVoice(voiceURI: string): void {
    this.voiceURI = voiceURI || null;
  }

  async requestNotificationPermission(): Promise<NotificationPermission> {
    if (!("Notification" in window)) return "denied";
    if (Notification.permission === "granted") return "granted";
    if (Notification.permission === "denied") return "denied";
    return Notification.requestPermission();
  }

  play(message: string, settings: AppSettings): void {
    if (settings.voiceEnabled) this.speak(message);
    if (settings.soundEnabled && settings.selectedSoundKey !== "silent") {
      this.playTone(settings.selectedSoundKey);
    }
    if (settings.notificationEnabled) this.notify(message);
  }

  stop(): void {
    if ("speechSynthesis" in window) window.speechSynthesis.cancel();
    this.stopTone();
  }

  speak(message: string): void {
    if (!("speechSynthesis" in window)) return;

    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(message);
    utterance.rate = 1;
    utterance.pitch = 1;
    utterance.volume = 1;

    if (this.voiceURI) {
      const match = window.speechSynthesis.getVoices().find((v) => v.voiceURI === this.voiceURI);
      if (match) utterance.voice = match;
    }

    window.speechSynthesis.speak(utterance);
  }

  private playTone(soundKey: string): void {
    const context = this.audioContext;
    if (!context) return;
    if (context.state === "suspended") void context.resume();

    const isChime = soundKey === "notification";
    const beepSeconds = isChime ? 0.12 : 0.18;
    const gapSeconds = 0.12;
    const count = isChime ? 2 : 3;
    const frequency = isChime ? 660 : 880;
    const firstStart = context.currentTime + 0.03;

    for (let index = 0; index < count; index += 1) {
      const start = firstStart + index * (beepSeconds + gapSeconds);
      const oscillator = context.createOscillator();
      const gain = context.createGain();

      oscillator.type = isChime ? "sine" : "square";
      oscillator.frequency.setValueAtTime(frequency, start);
      gain.gain.setValueAtTime(0.0001, start);
      gain.gain.exponentialRampToValueAtTime(1, start + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, start + beepSeconds - 0.02);

      oscillator.connect(gain);
      gain.connect(context.destination);
      oscillator.start(start);
      oscillator.stop(start + beepSeconds);

      this.oscillators.add(oscillator);
      oscillator.addEventListener(
        "ended",
        () => {
          this.oscillators.delete(oscillator);
          gain.disconnect();
        },
        { once: true },
      );
    }
  }

  private stopTone(): void {
    for (const oscillator of this.oscillators) {
      try {
        oscillator.stop();
      } catch {
        // Already stopped; nothing to do.
      }
    }
    this.oscillators.clear();
  }

  private notify(message: string): void {
    if (!("Notification" in window) || Notification.permission !== "granted") return;

    const options: NotificationOptions = {
      body: message,
      tag: "waza-battery-alarm",
      requireInteraction: true,
      silent: true,
    };

    new Notification("Waza Battery Alarm", options);
  }
}
