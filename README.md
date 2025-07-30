# Playback Master Proximity

Playback Master Proximity is an Android app that demonstrates how to use the device's proximity sensor to control video playback. It integrates a proximity chip for video playback control, automatically pausing when the user covers the sensor and resuming when uncovered. The app also showcases orientation‑specific layouts, a polished user interface and Jetpack components.

## Features

- **Proximity sensor control** – Detects the device's proximity sensor to automatically pause playback when the user covers the sensor and resumes when uncovered.
- **Video playback** – Plays local or embedded video using Android's media APIs (e.g., ExoPlayer).
- **Landscape and portrait layouts** – Provides custom layouts and navigation for both landscape and portrait orientations.
- **Modern UI design** – Implements a polished user interface with custom fonts, icons and responsive design.
- **Navigation components** – Uses Jetpack Navigation for seamless fragment transitions.
- **Sensor feedback handling** – Handles proximity sensor signals to adjust playback state.

## Tech Stack

| Layer/Feature            | Technology                                      |
|--------------------------|-------------------------------------------------|
| Language                 | Kotlin                                          |
| Architecture & Patterns  | MVVM (Model‑View‑ViewModel)                     |
| Sensors & Playback       | Android Sensor APIs, ExoPlayer                   |
| UI                       | Android Jetpack Components, Material Design     |
| Dependency Injection     | Dagger‑Hilt                                     |
| Coroutines & Lifecycle   | Kotlin Coroutines, LiveData, ViewModel          |

## Getting Started

To run the app locally:

1. **Clone the repository**  
   ```bash
   git clone https://github.com/shayann07/Playback-Master-Proximity.git
   cd Playback-Master-Proximity
   ```

2. **Open in Android Studio**  
   Open the project in [Android Studio](https://developer.android.com/studio) and let it sync all dependencies.

3. **Run the app**  
   Connect an Android device or start an emulator and click **Run**. The app will build and deploy.

## Contribution

Contributions are welcome! Feel free to open issues or submit pull requests for new features, bug fixes or improvements.

## License

This project is licensed under the MIT License.
