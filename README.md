# Gratia 🎵

**Version 1.0.1**

Gratia is a privacy-first, premium local music player for Android featuring a beautiful glassmorphism UI, reliable background playback, and a completely local-first architecture.

---

## 📥 Download & Installation

Download the latest signed APK (`app-release.apk`) directly from the Releases page:

**[GitHub Releases](https://github.com/theonlyhussain/gratia/releases/latest)**

*Note: Gratia is currently sideload-only. You may need to enable "Install from Unknown Sources" on your Android device.*

---

## ✨ Features (v1.0.1)

- **Premium UI:** Apple Music & Spotify-inspired design with dynamic glassmorphism and smooth micro-animations.
- **Dynamic Artwork:** 2x2 cover art collages for playlists and generated initials for missing artist art.
- **Rock-Solid Playback:** Media3/ExoPlayer architecture that survives backgrounding, rotation, and deep sleep.
- **Library Management:** Browse by Albums, Artists, and Folders.
- **Playlists & Favorites:** Full support for custom playlists and favorite tracks.
- **Synced Lyrics:** Support for time-synced lyrics with a beautiful UI.
- **Adaptive Theme:** Gorgeous Light and Dark modes that adapt to your system.
- **Offline First:** No internet connection required.

---

## 🔒 Privacy-First Philosophy

Your data is yours. Gratia is built on the principle of absolute privacy:
* **No cloud uploads:** Your music never leaves your device.
* **No accounts:** Just open the app and play.
* **No telemetry:** We do not track your listening habits or app usage.

---

## 🏗️ Architecture

Gratia uses a modern Android technology stack:
- **UI:** Jetpack Compose (100%)
- **Architecture:** MVVM + Coroutines/Flow
- **Playback:** Media3 / ExoPlayer with robust `MediaSessionService`
- **Database:** Room (SQLite)

---

## 🛠️ Building from Source

```bash
git clone https://github.com/theonlyhussain/gratia.git
cd gratia
./gradlew clean assembleDebug
```

---

## 🚀 Roadmap

* Better recommendation engine
* AI-assisted local recommendations
* Word-by-word lyrics improvements
* Android Auto & Wear OS support
* Gapless playback & Crossfade
* Equalizer and DSP effects

---

## 📄 License

This project is licensed under the GNU General Public License v3.0 - see the `LICENSE` file for details.

---

## 👨‍💻 Author

Created by **Hussain Shaikh**  
GitHub: [theonlyhussain](https://github.com/theonlyhussain)
