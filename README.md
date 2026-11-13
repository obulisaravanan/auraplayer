# AuraPlayer — Offline Android Music Player

![Android](https://img.shields.io/badge/Platform-Android-green?logo=android)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple?logo=kotlin)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue)
![ExoPlayer](https://img.shields.io/badge/Audio-Media3%20ExoPlayer-orange)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
A full-featured native Android music player designed for personal, offline-first listening. Built with modern Android development standards including **Kotlin**, **Jetpack Compose (Material 3)**, **AndroidX Media3 ExoPlayer**, **MediaSessionService**, **Room Database**, and **DataStore Preferences**.

---

## 🌟 Key Highlights

- **100% Offline & Private**: Zero user accounts, zero analytics, zero trackers, zero advertisements, and no cloud uploads. All audio files and listening statistics stay on your device.
- **Accurate Unplayed Songs Section**: Dedicated section that displays all newly discovered tracks that have not yet met your listening threshold. Shows real-time remaining count and automatically moves songs out of Unplayed as you listen.
- **Configurable Played Thresholds**:
  1. *When playback starts* (immediate or >= 1 second)
  2. *After 30 seconds* (or full duration for shorter tracks)
  3. *After at least 50% of the song*
  4. *Only after the entire song finishes*
- **Persistent Listening History**: Tracks play count, first-played timestamp, last-played timestamp, and progress across app restarts and device reboots. Routine rescans **never** erase or reset your history.
- **Full Playback Controls**: Background playback via foreground `MediaSessionService`, notification controls, lock screen controls, queue management (reorder/remove), shuffle, repeat (Off, All, One), and playback speeds (0.5x to 2.0x).
- **Audio Equalizer & Bass Boost**: Built-in 5-band frequency equalizer with sound presets (Rock, Pop, Jazz, Flat, Dance, Classical, Folk, Heavy Metal, Hip Hop) and adjustable bass boost.
- **Sleep Timer**: Timed sleep countdown (15m, 30m, 45m, 60m) that automatically pauses playback when finished.
- **Backup & Restore**: Export and restore your complete listening history, playlists, and settings to a JSON file using Android's Storage Access Framework.

---

## 📱 Architecture & Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose with Material 3 (Dark Theme default, Light Theme support) |
| **Audio Engine** | AndroidX Media3 ExoPlayer (`1.3.1`) |
| **Media Service** | AndroidX `MediaSessionService` with lock-screen notification and audio focus |
| **Local Database** | Room (`2.6.1`) with KSP code generation |
| **Settings / State** | AndroidX Preferences DataStore |
| **Audio Discovery** | Android `MediaStore` Audio API with automatic tag parsing |
| **Image Loading** | Coil Compose for embedded album artwork |
| **Architecture Pattern** | MVVM with Repository Pattern |

---

## 🚀 How to Build and Install

### Prerequisites
- Android Studio Hedgehog / Iguana / Jellyfish or later (or JDK 17 / JDK 21 with Android SDK installed).
- Android SDK Platform `android-34` and Build Tools `34.0.0`.
- An Android device running Android 8.0 (API level 26) or later.

### Step 1: Open in Android Studio
1. Launch Android Studio.
2. Select **Open** and navigate to this folder (`c:\gemini`).
3. Allow Gradle to sync dependencies automatically.

### Step 2: Build the Debug APK via Terminal
You can build the APK directly using the included Gradle wrapper:

```bash
# On Windows PowerShell:
.\gradlew.bat assembleDebug

# On macOS/Linux:
./gradlew assembleDebug
```

The compiled APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Step 3: Install onto Your Personal Phone
#### Option A: Using ADB (Android Debug Bridge)
1. Enable **Developer Options** and **USB Debugging** on your phone.
2. Connect your phone via USB.
3. Run:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

#### Option B: Direct File Transfer
1. Copy `app-debug.apk` to your phone via USB cable, Google Drive, or Bluetooth.
2. On your phone, tap the `.apk` file using your file manager to install it.
3. If prompted, allow "Install unknown apps" for your file manager.

---

## 🎧 Listening History & Thresholds

### How Unplayed Songs Work:
1. **Discovery**: When you first launch the app, it scans your phone for local audio files. Every newly discovered song starts with `isPlayed = false`.
2. **Threshold Monitoring**: As you listen to a track, a background ticker checks whether your selected threshold (e.g., 30 seconds) has been reached.
3. **Automatic Update**: As soon as the condition is met, the song is marked as `Played`, its play count is incremented, and its `lastPlayedTimestamp` is recorded in Room.
4. **Manual Toggle**: You can manually toggle any song between Played and Unplayed at any time from the song's options menu (`⋮`) or the Full Player view.
5. **Routine Rescans**: Adding new music or rescanning your library will discover new tracks while preserving existing listening counts and played statuses.

---

## 💾 Backup and Restore

1. Go to the **Settings** tab.
2. Tap **Export Library Data**.
3. Choose a location on your phone or SD card to save the JSON backup file.
4. If you reinstall the app or switch phones, tap **Restore Library Data** and select your backup file to immediately recover all your history, playlists, and favorites.

---

## 🧪 Running Unit Tests

Run unit tests to verify played threshold logic and database reconciliation:
```bash
.\gradlew.bat test
```
- `PlayedThresholdTest`: Tests START, 30s, 50%, and END conditions.
- `LibraryReconciliationTest`: Verifies that rescans preserve played statuses and play counts.

<!-- AuraPlayer Audio Engine verified -->
