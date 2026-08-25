<div align="center">
  
# 🎵 Gratia

**The Premium, Local-First Android Music Player**

A modern, meticulously crafted music player focused on fluid playback, stunning aesthetics, and an ad-free, private listening experience.

[![Version](https://img.shields.io/badge/Version-2.4.0-orange.svg)](https://github.com/theonlyhussain/gratia/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-Jetpack-4285F4.svg?logo=android)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Media3-ExoPlayer-brightgreen.svg)](https://developer.android.com/media/media3)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

</div>

---

## ✨ Features

- 🎨 **Immersive Fluid Aesthetic**: Edge-to-edge design, liquid glass navigation bar, dynamic background glow based on cover art, smooth spring animations, and full Light/Dark/AMOLED theme support.
- 🎵 **Synced Lyrics Engine**: Kinetic scrolling with word-level synchronized lyrics, offset adjustment controls, and instant toggle between standard player and lyrics mode (powered by LRCLIB & Lyrically).
- 🖼️ **Interactive Horizontal Song Preview**: Swipe artwork smoothly across the expanded player to preview adjacent queue tracks before jumping to them.
- 💊 **Floating Pill Mini-Player**: Physics-based interactive mini-player with quick play/pause, gesture controls, and seamless full-screen expansion.
- 🎧 **Native Media Output Routing**: Quick-switch playback across Bluetooth devices, headphones, and system speakers via Android's native Media Output panel.
- ✨ **"Recommended For You" & Smart Discovery**: Personalized listening suggestions powered by local listening history and high-res artist imagery pulled dynamically via Deezer & Wikipedia.
- 👤 **Customizable Profiles**: Beautifully animated profile editing UI to personalize your avatar and background cover image.
- 🔄 **Smart In-App Updates**: Built-in non-intrusive update manager that detects, downloads, and installs the latest Gratia releases directly from GitHub.
- 🔔 **Media3 Notification**: Clean, monochrome silhouette notification with full playback controls and artwork integration.
- 🔒 **Absolute Privacy**: Complete offline-first architecture with zero analytics and zero telemetry. Your library stays strictly on your device.

---

## 🛠️ Technology Stack

| Component | Technology |
|---|---|
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose (Material3 & Custom Liquid Glass System) |
| **Architecture** | MVVM / Repository Pattern / Unidirectional Data Flow (StateFlow) |
| **Database** | Room (Local SQLite ORM) |
| **Media Playback** | AndroidX Media3 / ExoPlayer (Gapless Playback Engine) |
| **Image Loading** | Coil (with hardware bitmap caching & color palette extraction) |
| **External APIs** | Deezer API (Metadata & Images), LRCLIB (Synced Lyrics), Wikipedia API (Artist Bios), GitHub API (Updates) |

---

## 🚀 Installation

### Download APK

You can download the latest compiled, ready-to-install debug/release APK directly from the GitHub Releases page:

[**Download Latest Release**](https://github.com/theonlyhussain/gratia/releases/latest)

### Build from source

To build Gratia locally, clone the repository and assemble the build:

```bash
git clone https://github.com/theonlyhussain/gratia.git
cd gratia
./gradlew assembleDebug
```
The APK will be generated in `app/build/outputs/apk/debug/Gratia.apk`.

---

## 📁 Project Structure

```text
app/src/main/java/com/gratia/music/
 ├── data/         # Room Database, DAOs, Repositories, and Network APIs
 ├── lyrics/       # LRC parsing, word-level sync, and timing engine
 ├── player/       # Media3 PlaybackService, PlayerManager, and Queue handling
 ├── storage/      # Scoped storage indexing and MediaStore integration
 ├── utils/        # Artist metadata parser and formatting utilities
 └── ui/
      ├── components/ # Reusable Compose buttons, sheets, cards, liquid glass effects
      ├── lyrics/     # Interactive synced lyrics overlay & editor
      ├── player/     # ExpandedPlayer, MiniPlayer, and gesture controllers
      ├── screens/    # Library, Search, Home, Browse, and Profile screens
      └── theme/      # Gratia Typography, Motion springs, and Color tokens
```

---

## 🤝 Contributing & Feedback

Contributions are always welcome! Gratia is built to Apple and modern Android UX standards—fluid, deliberate, and calm.

- [**Report a Bug**](https://github.com/theonlyhussain/gratia/issues) 🐛
- [**Request a Feature**](https://github.com/theonlyhussain/gratia/issues) 💡

## 📄 License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

---
<div align="center">
Built with Kotlin and Jetpack Compose.<br>
<i>Designed to be fast, private, and deeply personal.</i>
</div>
