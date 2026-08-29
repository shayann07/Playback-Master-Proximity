# ⚡ Playback-Master-Proximity — IoT Interactive Kiosk with ESP32 Proximity Triggers

[![Platform](https://img.shields.io/badge/Platform-Android_%7C_USB_Host_OTG-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin_2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Hardware](https://img.shields.io/badge/IoT_Hardware-ESP32_%2F_Arduino-E7352C?logo=espressif&logoColor=white)](https://www.espressif.com)
[![Serial Protocol](https://img.shields.io/badge/Serial-USB--UART_115200_8N1-blue)]()
[![Media Engine](https://img.shields.io/badge/Media_Engine-ExoPlayer_2.19-blue?logo=google)]()
[![Target SDK](https://img.shields.io/badge/Target_SDK-35-green?logo=android)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **Playback-Master-Proximity** is an interactive IoT digital signage and kiosk video system for Android. Connected to an ESP32 or Arduino proximity/motion sensor over USB OTG Serial, the app dynamically turns on the display and plays targeted video whenever a visitor steps near, pausing and conserving energy when they walk away.

---

## 📖 Overview & Hardware Concept

In experiential retail, museums, and interactive showrooms, continuous video playback drains power and causes screen burn-in while unattended. 

**Playback-Master-Proximity** bridges physical microcontrollers with Android's media subsystem:
1. An **ESP32**, **Arduino**, or **STM32** running a proximity sensor (PIR motion, Ultrasonic HC-SR04, or ToF VL53L0X) is plugged into the Android device via a USB-C / Micro-USB OTG cable.
2. The Android application's `UsbProximityService` opens a serial stream (115200 baud, 8N1) via `usb-serial-for-android`.
3. When someone approaches, the microcontroller sends an ASCII byte (`1`). The background service triggers `ACTION_PROXIMITY_DETECTED`, waking the screen and launching high-definition ExoPlayer playback.
4. When the visitor walks away, the sensor sends `0`. The app receives `ACTION_PROXIMITY_LOST`, cleanly pausing video and releasing the `PowerManager` screen wake-lock to enter power-saving standby.
5. All proximity interactions are gated by configured operational business hours (e.g. 09:00 to 18:00) using Android's `AlarmManager`.

---

## 🏗️ Hardware & Software Architecture

```mermaid
flowchart TD
    subgraph Hardware ["Microcontroller Hardware (IoT)"]
        Sensor["Proximity / PIR Sensor\n(HC-SR04 / ToF / PIR)"]
        MCU["ESP32 / Arduino / CH340 / CP2102\nUART Serial Bridge @ 115200 8N1"]
        Sensor -->|Distance / Motion Event| MCU
    end

    subgraph USBPhysical ["Physical USB-OTG Connection"]
        OTG[USB Type-C / Micro-USB OTG Cable]
        MCU -->|Serial Stream\n'1' = Near, '0' = Away| OTG
    end

    subgraph AndroidLayer ["Android Host System (SDK 35)"]
        UsbHost[Android USB Host API\nUsbManager & UsbSerialPort]
        UsbService[UsbProximityService\nBackground Serial Listener Thread]
        TimeCheck{Within Business\nHours Schedule?}
        Broadcast[Intent Broadcaster\nACTION_PROXIMITY_DETECTED / LOST]
        
        OTG --> UsbHost
        UsbHost --> UsbService
        UsbService --> TimeCheck
        TimeCheck -->|Yes| Broadcast
        TimeCheck -->|No (Off-Hours)| Ignore[Ignore Sensor Trigger]
    end

    subgraph KioskUI ["Interactive Kiosk Media Engine"]
        ProxRecv[ProximityReceiver]
        PlayService[PlaybackService / VideoFragment\nForeground Media Service]
        WakeLock[PowerManager WakeLock\nScreen Bright & Awake]
        Exo[Google ExoPlayer 2.19.1\nFullscreen Video Stream]

        Broadcast --> ProxRecv
        ProxRecv -->|Action Play/Stop| PlayService
        PlayService --> WakeLock
        PlayService --> Exo
    end
```

---

## ⚡ ESP32 / Arduino Firmware Sample

Upload the following lightweight C++ sketch to your ESP32 or Arduino board:

```cpp
// ESP32 / Arduino Proximity Trigger Firmware
#define SENSOR_PIN 4        // Digital PIR or Ultrasonic Trigger Pin
#define DETECTION_THRESHOLD 100 // cm for ultrasonic or HIGH for PIR

bool lastState = false;

void setup() {
  Serial.begin(115200);
  pinMode(SENSOR_PIN, INPUT);
}

void loop() {
  bool currentState = digitalRead(SENSOR_PIN) == HIGH;

  if (currentState != lastState) {
    if (currentState) {
      Serial.print("1");  // Proximity detected -> Play video
    } else {
      Serial.print("0");  // Proximity lost -> Stop video
    }
    lastState = currentState;
    delay(200); // Debounce
  }
  delay(50);
}
```

---

## ✨ Core Features

- 🔌 **Plug-and-Play USB-Serial Driver**: Integrated `usb-serial-for-android` supporting CH340, CP2102, FTDI FT232, Prolific PL2303, and CDC/ACM devices.
- 🎯 **Motion & Proximity-Triggered Playback**: Wakes screen and launches full-screen ExoPlayer playback on proximity `1`, stops on `0`.
- 🕒 **Scheduled Active Window Filtering**: Built-in schedule validator ensures sensor events only trigger playback during defined business hours.
- 💡 **Dynamic Screen WakeLock**: Manages `PowerManager.SCREEN_BRIGHT_WAKE_LOCK` with `ACQUIRE_CAUSES_WAKEUP` to automatically turn the display on/off.
- 🛡️ **Foreground Service Architecture**: Runs `UsbProximityService` and `PlaybackService` with `foregroundServiceType="mediaPlayback"` to guarantee continuous serial listening without being killed by Android memory killer.
- 🔌 **Hotplug Auto-Recovery**: Listens for `android.hardware.usb.action.USB_DEVICE_ATTACHED` to automatically request permissions and re-establish the serial stream when plugged in.
- 🔄 **Reboot Resilience**: Built-in `BootReceiver` restarts scheduled alarms and services following power outages or system restarts.

---

## 📱 Key Components Breakdown

| Component | Path | Responsibility |
|---|---|---|
| **USB Serial Service** | `com.shayan.playbackmaster.services.UsbProximityService` | Manages USB connection, reads serial baud rate 115200, broadcasts detection intents |
| **Proximity Receiver** | `com.shayan.playbackmaster.receivers.ProximityReceiver` | Intercepts `ACTION_PROXIMITY_DETECTED` and `ACTION_PROXIMITY_LOST` broadcasts |
| **Media Playback Service** | `com.shayan.playbackmaster.services.PlaybackService` | Foreground playback orchestrator managing ExoPlayer lifecycle |
| **Kiosk Video UI** | `com.shayan.playbackmaster.ui.fragments.VideoFragment` | Fullscreen landscape video player with wake-lock holding and immersive sticky UI |
| **Main Activity** | `com.shayan.playbackmaster.ui.MainActivity` | USB permission requests, battery optimization prompts, and fragment router |
| **Schedule Utilities** | `com.shayan.playbackmaster.utils.AlarmUtils` | Computes active business hour windows via `AlarmManager` |
| **Boot Listener** | `com.shayan.playbackmaster.receivers.BootReceiver` | Re-initializes timers and USB listeners upon system boot |

---

## 🛠️ Technical Stack Matrix

| Layer | Technology / Library | Version | Purpose |
|---|---|---|---|
| **Platform** | Android (with USB Host OTG) | SDK 21 – 35 (Java 11) | Core operating system runtime |
| **Language** | Kotlin | `2.0.21` | Modern, expressive application logic |
| **Serial Communication** | `mik3y/usb-serial-for-android` | `3.7.0` | USB-to-UART driver for CH340, CP2102, FTDI, CDC |
| **Video Engine** | Google ExoPlayer | `2.19.1` | Low-latency local video decoding and looping |
| **Navigation** | AndroidX Navigation Component | `2.8.5` | Fragment transaction management |
| **Services** | Android Foreground Service | Media Playback | Persistent background USB listening and media state |
| **Hardware** | ESP32 / Arduino / PIR / HC-SR04 | 115200 Baud | External physical presence detection |

---

## 🚀 Getting Started & Hardware Wiring

### 1. Hardware Requirements
- **Android Display**: Tablet or TV Box with USB OTG Host support (Android 5.0+).
- **Microcontroller**: ESP32, ESP8266, Arduino Nano/Uno, or Raspberry Pi Pico.
- **Sensor**: PIR Motion Sensor (HC-SR501 / AM312) or Ultrasonic Distance Sensor (HC-SR04).
- **OTG Cable**: USB-C or Micro-USB to USB-A Female adapter.

---

### 2. Software Installation

1. **Clone the repository**:
   ```bash
   git clone https://github.com/shayann07/Playback-Master-Proximity.git
   cd Playback-Master-Proximity
   ```
2. **Build and install the APK**:
   ```bash
   ./gradlew assembleDebug
   ```
3. **Connect Hardware & Grant Permissions**:
   - Plug the ESP32 into the Android device via the USB OTG adapter.
   - Accept the **USB Device Permission** prompt ("Open PlaybackMaster when this USB device is connected").
   - Grant media storage permissions and disable **Battery Optimization**.
4. **Configure Operating Hours**:
   - Select your showcase video file.
   - Configure the active **Start Time** and **End Time**.
   - Tap **Schedule**. When visitors step within range of the sensor, the kiosk will instantly wake and play!

---

## 📄 License

This project is licensed under the [MIT License](LICENSE) — Copyright (c) 2026 [shayann07](https://github.com/shayann07).
