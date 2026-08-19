<div align="center">
  
# 🎵 Gratia

**The Local-First Android Music Player**
*NOTE - THE PROJECT HAS BEEN UPDATED THAT MEANS IT'S AHAED OF THE CURRENT APK, THE APP IS STILL BEHIND WHILE THE FILES HAVE BEEM UPDATED AND FIXED BUGS*

A modern, meticulously crafted music player focused on fluid playback, stunning UI, ad-free listening experience.

[![Version](https://img.shields.io/badge/Version-2.3.7-orange.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-Jetpack-4285F4.svg?logo=android)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Media3-ExoPlayer-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

</div>

---

## ✨ Features

- 🎨 **Immersive Aesthetic**: Edge-to-edge player, dynamic backgrounds based on cover art, smooth spring animations, and a pixel-perfect Dark/AMOLED theme.
- 🎵 **Synced Lyrics Engine**: Beautiful kinetic scrolling with word-level synchronized lyrics overlay directly on the player, powered by LRCLIB and the Lyrically API.
- ✨ **"Recommended For You"**: Personalized listening suggestions powered by local listening history and high-res artist imagery pulled dynamically via the Deezer API.
- 🎧 **Device Output Selector**: Seamlessly switch music output to Bluetooth speakers, headphones, or other connected devices directly from the player.
- 📲 **Fluid Gestures**: Interactive mini-player with physics-based drag and swipe gestures to skip tracks or dismiss playback.
- 👤 **Customizable Profiles**: Beautifully animated profile editing UI to personalize your avatar and background cover image.
- 🔄 **Smart In-App Updates**: A sleek, non-intrusive update manager that detects, downloads, and installs the latest Gratia features effortlessly.
- 🏷️ **Automated Metadata**: Automatically fetches beautiful cover art and ID3 metadata (including genres) via the Deezer API to keep your library pristine.
- 🔒 **Absolute Privacy**: Complete offline listening capability and strict zero data tracking. Your music is yours.

## 🛠️ Technology Stack

| Component | Technology |
|---|---|
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose |
| **Architecture** | MVVM / Repository Pattern |
| **Database** | Room |
| **Media Playback** | Media3 / ExoPlayer (Custom Gapless Engine) |
| **API Integration** | Deezer API (Metadata & Images), LRCLIB & Lyrically (Lyrics), Wikipedia API (Artist Bios), GitHub API (Updates) |

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

Please see our [Contributing Guidelines](CONTRIBUTING.md) for more details on how to get started, and ensure you follow our [Code of Conduct](CODE_OF_CONDUCT.md) in all community interactions.

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
