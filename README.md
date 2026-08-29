# Playback-Master-Proximity

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)]()
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)]()
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> Same scheduled-playback kiosk as Playback-Master, but plays and pauses in response to an ESP32 proximity sensor connected over USB serial.

---

## 📖 Overview

Same scheduled-playback kiosk as Playback-Master, but plays and pauses in response to an ESP32 proximity sensor connected over USB serial.

---

## ✨ Key Features

- **Esp32**: Built-in support and optimized flows for esp32.
- **Exoplayer**: Built-in support and optimized flows for exoplayer.
- **Iot**: Built-in support and optimized flows for iot.
- **Kiosk**: Built-in support and optimized flows for kiosk.

---

## 🛠️ Technology Stack

| Component / Layer | Technology |
|---|---|
| **Platform** | Android |
| **Primary Language** | Kotlin |
| **Architecture** | MVVM / Clean Architecture |
| **License** | Open Source (MIT) |

---

## 🚀 Getting Started

1. **Hardware.** Flash an ESP32 (or any USB-serial board) to emit `1` / `0` bytes (one per detection event) and connect it via OTG to the Android device.
2. Open the project in Android Studio (AGP 8.8.0 / Gradle 8.10.x) and run on Android 5.0+ (`minSdk = 21`).
3. On first launch, grant:
   - `READ_MEDIA_VIDEO` (Android 13+) or legacy `READ_EXTERNAL_STORAGE` (≤ API 32),
   - The `SCHEDULE_EXACT_ALARM` permission (Android 12+),
   - The "ignore battery optimisations" prompt,
   - The USB device permission popup on first ESP32 attach.
4. Pick a local video, set start + end times, tap **Schedule**.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE) — Copyright (c) 2026 [shayann07](https://github.com/shayann07).
