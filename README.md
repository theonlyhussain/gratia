<div align="center">
  
# 🎵 Gratia

**The Local-First Android Music Player**

A modern, meticulously crafted music player focused on fluid playback, stunning UI, ad-free listening experience.

[![Version](https://img.shields.io/badge/Version-2.3.6-orange.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-Jetpack-4285F4.svg?logo=android)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Media3-ExoPlayer-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

</div>

---

## ✨ Features

- 🎨 **Immersive Aesthetic**: Edge-to-edge player, dynamic backgrounds based on cover art, smooth spring animations, and a pixel-perfect Dark/AMOLED theme.
- 🎵 **Synced Lyrics Engine**: Kinetic scrolling synced LRC lyrics overlay directly on the player, with a built-in lyrics editor and formatting tools.
- ✨ **"Recommended For You"**: Personalized listening suggestions powered by local listening history and high-res artist imagery pulled dynamically via the Deezer API.
- 🎛️ **Gapless Crossfade**: Custom dual-engine ExoPlayer implementation for seamless, DJ-style crossfading between tracks.
- 🏷️ **Automated Metadata**: Automatically fetches beautiful cover art and ID3 metadata (including genres) via Deezer API to keep your library pristine.
- 🔒 **Absolute Privacy**: Complete offline listening capability and strict zero data tracking. Your music is yours.

## 🛠️ Technology Stack

| Component | Technology |
|---|---|
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose |
| **Architecture** | MVVM / Repository Pattern |
| **Database** | Room |
| **Media Playback** | Media3 / ExoPlayer (Custom Gapless Engine) |
| **API Integration** | Deezer (Cover Art & Artist Metadata) |

## 🚀 Installation

### Download APK

You can download the latest compiled, production-ready APK directly from the GitHub Releases page:

[**Download Latest Release**](https://github.com/theonlyhussain/gratia/releases/latest)

### Build from source

To build Gratia locally, clone the repository and assemble the release build:

```bash
git clone https://github.com/theonlyhussain/gratia.git
cd gratia
./gradlew assembleRelease
```
The APK will be generated in `app/build/outputs/apk/release/`.

## 📁 Project Structure

```text
app/src/main/java/com/gratia/music/
 ├── data/         # Repositories, DAOs, and API services
 ├── lyrics/       # Advanced LRC parsing and timing engine
 ├── player/       # Dual-engine crossfade ExoPlayer logic
 ├── storage/      # Scoped storage and MediaStore indexing
 └── ui/
      ├── components/ # Reusable Compose buttons, cards, and sliders
      ├── lyrics/     # Interactive lyrics overlay UI
      ├── player/     # Main player and expanded player views
      ├── screens/    # Full-screen navigation routes
      └── theme/      # Gratia Typography, Motion tokens, and Colors
```

## 🤝 Contributing & Feedback

We welcome community contributions! Gratia strives for high-quality, polished UI interactions. If you are planning a major feature or significant architectural change, please open an issue first to discuss your ideas and ensure they align with the project's design philosophy.

### Bug Reports & Feature Requests
Found a bug or have an idea to make Gratia even better? We'd love to hear it! 
- [**Report a Bug**](https://github.com/theonlyhussain/gratia/issues) 🐛
- [**Request a Feature**](https://github.com/theonlyhussain/gratia/issues) 💡

## 📄 License

This project is licensed under the GNU General Public License v3.0 - see the `LICENSE` file for details.

---
<div align="center">
Built with Kotlin and Jetpack Compose.<br>
<i>Designed to be fast, private, and deeply personal.</i>
</div>
