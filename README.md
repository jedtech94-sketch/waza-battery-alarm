# Waza Battery Alarm

A low-battery reminder that speaks, plays an alarm, and shows a notification.

This repository contains two clients:

- **`android/`** — Kotlin Android app. This is the reliable version: it monitors battery in the background, speaks via TextToSpeech, plays a tone, repeats until charging starts, and provides a widget plus Quick Settings tile.
- **`web/`** — Vite + TypeScript PWA. This is a companion that works in Chromium browsers over HTTPS while its tab is open.

## Behavior

Both clients use the same rules:

- Escalating alerts: reminder at **20%**, warning at **10%**, critical at **5%** — all configurable.
- Quiet hours: **22:00–07:00** by default. Normal alerts become notification-only; critical alerts still speak.
- Repeat every `repeatIntervalMinutes` until charging starts.
- Snooze and Stop actions.
- Re-arm after charging starts or the battery rises above `threshold + 5%`.
- When the charger is connected, stop any active alert and say **“Charger connected.”**
- Unplug reminder at **80%**, once per charging session.
- Slow-charge warning after **7 minutes** with no battery rise.
- Estimated time-left added to low-battery voice alerts when available.
- Voice selection and alarm sound selection.
- Dark mode setting with System, Light, and Dark options.
- Alerts use maximum app-side volume. On Android the alarm stream is raised to its maximum; in a browser the page uses maximum gain but still follows the device/system volume.

## Settings layout

Both clients use five bottom-navigation tabs with a persistent status card:

- **Alerts** — monitoring, 20/10/5 levels, messages, timing, quiet hours
- **Charging** — charger-connected message, unplug reminder, slow-charge warning
- **Voice** — voice picker, sound picker, alert toggles, Test and Snooze
- **Appearance** — System / Light / Dark
- **Advanced** — notification permission, battery-optimization shortcut, reset settings, app info

## Android

Open `android/` in Android Studio (Giraffe+) and run the `app` configuration on a device or emulator.

If you do not use Android Studio and already have a Gradle installation:

```bash
cd android
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

The generated APK appears at `android/app/build/outputs/apk/debug/app-debug.apk`.

To simulate a low battery in an emulator:

```bash
adb shell dumpsys battery unplug
adb shell dumpsys battery set level 19
adb shell dumpsys battery reset
```

Home-screen widget and Quick Settings tile can toggle monitoring without opening the app.

> Android 13+ requires notification permission, which the app requests on first launch.
> For dependable alerts on real devices, use **Settings → Battery → Unrestricted** for this app. The app also has a shortcut to that setting.
>
> The foreground service is declared as `specialUse` because battery monitoring is not covered by a more specific Android 14 foreground-service type. If you publish to Google Play, declare this type in Play Console with the supplied justification.

## Web / PWA

```bash
cd web
npm install
npm run dev
```

Open the printed URL. Chrome requires a secure context for `navigator.getBattery()`; `localhost` counts as secure.

Production build:

```bash
npm run build
npm run preview
```

Deploy the contents of `web/dist/` to any static HTTPS host. The service worker caches the app shell so it can be installed as a PWA.

> Browser support is limited: the Battery Status API is available in Chromium browsers only. Firefox and Safari do not expose it. A browser tab cannot reliably monitor battery or repeat alarms while closed, so the Android app is the dependable client. Widgets and Quick Settings tiles are Android-only.

## Generate assets

The alarm tone and PWA icons are generated from small scripts so the repo stays text-based:

```bash
node scripts/generate-alarm-tone.mjs
node scripts/generate-icons.mjs
```
