# Playback-Master-Proximity

Native Kotlin Android **scheduled video player** with **USB-attached proximity control**. Sibling of [`Playback-Master`](https://github.com/shayann07/Playback-Master) — same daily-window logic and same `applicationId = com.shayan.playbackmaster` — but instead of an unconditional play, the video pauses and resumes inside the scheduled window based on bytes streamed from an **ESP32** (or any USB-serial board) over OTG. While the ESP32 emits `1`, the foreground service plays; while it emits `0`, it pauses. Single-Activity + Jetpack Navigation, MVVM-lite (`AppViewModel` + `LiveData` + SharedPreferences), no Hilt / Coroutines / Compose / Room / Firebase. ~13 Kotlin files. ExoPlayer 2.19.1 + `mik3y/usb-serial-for-android` 3.7.0.

## ⚠ Heads-up: previous README claims that aren't true

The previous README listed Dagger-Hilt, Kotlin Coroutines, the Android proximity sensor, custom landscape/portrait layouts, and an MIT licence. None of those are in the code:

- **No Hilt** (no `@HiltAndroidApp` / `@AndroidEntryPoint` / `@Inject`, no Hilt artefacts in `gradle/libs.versions.toml`).
- **No Kotlin Coroutines** (no `kotlinx-coroutines-*` dependency; threading is `Thread {}` and `Handler.postDelayed`).
- **Not the Android proximity sensor.** The proximity signal comes from a USB-serial device (ESP32) at 115200 baud, parsed in `services/UsbProximityService.kt:149-210`.
- **No `layout-land` / `layout-port` resource folders.** Only `app/src/main/res/layout/` (4 orientation-generic files).
- **No `LICENSE` file** at the repo root.
- The previous README also carried 13 `<!-- gitpulse:contribution -->` marker comments (lines 49-61) and the recent git history is dominated by `Update repository metadata (GitPulse)` commits — those have been removed from this rewrite.

## Status

- Working tree clean on `master`. Recent commits are all `Update repository metadata (GitPulse)` (`f4a686b`, `ffb02ed`, `4aa72c0`, …); the real product commits (`Complete`, `Fixed Proximity Signals`) sit below them.
- Remote: `https://github.com/shayann07/Playback-Master-Proximity.git`.

## How it works

### Schedule a window

`HomeFragment` lets the user pick a local video URI and a start + end time via `TimePickerHelper`. On "schedule":

1. URI + start/end are persisted via `data/preferences/PreferencesHelper` (SharedPreferences).
2. `utils/AlarmUtils.kt:17-61` registers a daily alarm with `AlarmManager.setExactAndAllowWhileIdle(...)`. (Improvement over `Playback-Master`, which used the inexact `setRepeating`.)
3. The user is prompted to disable battery optimisation via `BatteryOptimizationHelper` — required so OEM Doze does not drop the alarm.

### Survive reboot

`receivers/BootReceiver.kt:18-60` reads the saved state on `BOOT_COMPLETED` and re-arms the alarm.

### Foreground playback

When the alarm fires, `services/PlaybackService.kt:25-230` runs as a foreground service of type `mediaPlayback`, posts a media-style notification, holds a `SCREEN_BRIGHT_WAKE_LOCK`, and listens for `ACTION_PLAY_VIDEO` / `ACTION_STOP_VIDEO` to drive ExoPlayer 2.19.1.

### USB proximity

`services/UsbProximityService.kt:23-266` enumerates USB devices, opens a serial port at 115200/8N1, and reads single-byte frames. Inside the active schedule window:

- `1` byte ⇒ broadcast `ACTION_PROXIMITY_DETECTED` ⇒ `receivers/ProximityReceiver` ⇒ `PlaybackService.ACTION_PLAY_VIDEO`.
- `0` byte ⇒ broadcast `ACTION_PROXIMITY_LOST` ⇒ `receivers/ProximityReceiver` ⇒ `PlaybackService.ACTION_STOP_VIDEO`.

Outside the window the proximity bytes are ignored.

### Architecture

`ui/viewmodel/AppViewModel : AndroidViewModel` exposes `videoUri`, `startTime`, `endTime` as `MutableLiveData`. There is no Repository or UseCase layer — the ViewModel reads / writes `PreferencesHelper` directly.

## Tech stack

- **Build:** AGP 8.8.0, Kotlin 2.0.21, Java 11, version catalog at `gradle/libs.versions.toml`. View Binding enabled.
- **App config:** `applicationId = com.shayan.playbackmaster` (shared with the sibling `Playback-Master`), `compileSdk = 35`, `minSdk = 21`, `targetSdk = 35`, `versionCode = 1`, `versionName = "1.0"`.
- **AndroidX / Jetpack:** core-ktx 1.15.0, appcompat 1.7.0, material 1.12.0, navigation-fragment-ktx + navigation-ui-ktx 2.8.5.
- **Media:** ExoPlayer 2.19.1 (legacy artefact — Media3 is the modern replacement).
- **USB:** `com.github.mik3y:usb-serial-for-android` 3.7.0.
- **Permissions:** `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `READ_MEDIA_VIDEO` (33+), `READ_EXTERNAL_STORAGE` (≤ 32), `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `DEVICE_POWER`, `SYSTEM_ALERT_WINDOW`.

## Project layout

```
Playback-Master-Proximity/
├── app/
│   ├── build.gradle.kts                              applicationId com.shayan.playbackmaster
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/shayan/playbackmaster/
│           ├── data/
│           │   ├── models/Video.kt
│           │   └── preferences/PreferencesHelper.kt
│           ├── receivers/
│           │   ├── BootReceiver.kt                   reschedules on BOOT_COMPLETED
│           │   └── ProximityReceiver.kt              ⚠ exported=true, no signature-perm
│           ├── services/
│           │   ├── PlaybackService.kt                ⚠ exported=true; ExoPlayer + foreground notification
│           │   └── UsbProximityService.kt            ⚠ exported=true; reads ESP32 over USB serial
│           ├── ui/
│           │   ├── MainActivity.kt                   NavHost
│           │   ├── viewmodel/AppViewModel.kt         MutableLiveData × 3
│           │   └── fragments/                        HomeFragment, VideoFragment, ExitPlaybackListener
│           └── utils/                                AlarmUtils, BatteryOptimizationHelper, Constants, TimePickerHelper
├── .gitignore                                        partial — most of `.idea/` is still tracked
└── README.md
```

## Setup / run

1. **Hardware.** Flash an ESP32 (or any USB-serial board) to emit `1` / `0` bytes (one per detection event) and connect it via OTG to the Android device.
2. Open the project in Android Studio (AGP 8.8.0 / Gradle 8.10.x) and run on Android 5.0+ (`minSdk = 21`).
3. On first launch, grant:
   - `READ_MEDIA_VIDEO` (Android 13+) or legacy `READ_EXTERNAL_STORAGE` (≤ API 32),
   - The `SCHEDULE_EXACT_ALARM` permission (Android 12+),
   - The "ignore battery optimisations" prompt,
   - The USB device permission popup on first ESP32 attach.
4. Pick a local video, set start + end times, tap **Schedule**.

## Honest limitations

- **Three services / receivers are `exported="true"` with no signature-level permission.** `PlaybackService`, `UsbProximityService`, and `ProximityReceiver` will accept broadcasts from any installed app — `ACTION_PLAY_VIDEO` / `ACTION_STOP_VIDEO` / `ACTION_PROXIMITY_DETECTED` / `ACTION_PROXIMITY_LOST` can be forged. Set `android:exported="false"` (USB attach is system-mediated via the manifest filter) or add a custom signature permission.
- **`.gitignore` is partial.** It excludes `local.properties`, `.gradle`, `/build`, and a few `.idea/*` files, but leaves `.idea/.name`, `.idea/compiler.xml`, `.idea/gradle.xml`, `.idea/migrations.xml`, `.idea/misc.xml`, `.idea/vcs.xml`, etc. tracked. Add `/.idea/` and `git rm --cached` everything currently shadowed.
- **`PowerManager.WakeLock` without timeout** in `VideoFragment` — bound it to the remaining playback window so a missed `release()` doesn't drain the battery.
- **ExoPlayer 2.x is in maintenance mode.** Migrate to `androidx.media3:media3-exoplayer:1.x`.
- **Hardcoded USB baud rate** (115200) and serial parameters in `UsbProximityService.kt:136`. Surface via settings if you ever ship a non-ESP32 controller.
- **No `LICENSE` file** at the repo root despite the previous README claiming MIT.
- **No tests** beyond the default `ExampleUnitTest` / `ExampleInstrumentedTest`. Time-window arithmetic and `PreferencesHelper` are easy to unit-test; the USB layer is mockable behind a small interface.
