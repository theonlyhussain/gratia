# Gratia 🎵

**Your music. Your storage. Your rules.**

Gratia is a privacy-first Android music player inspired by modern local music players, rebuilt with a custom Gratia design system and kinetic lyrics engine.

## Download APK

**Latest release:** [Download Gratia v0.1.0-alpha APK](https://github.com/theonlyhussain/gratia/releases/download/v0.1.0-alpha/gratia-v0.1.0-alpha.apk)

Or open the full releases page:

[GitHub Releases](https://github.com/theonlyhussain/gratia/releases)

### Install

1. Download `gratia-v0.1.0-alpha.apk`.
2. Open the APK on your Android device.
3. Allow **Install unknown apps** if Android asks.
4. Install and open Gratia.

> This APK is not from Google Play. Android may show a warning before installation.

## Supported Storage Providers

| Provider            | Status    |
| ------------------- | --------- |
| 📱 **Local Device** | ✅ Working |

## Features

* 🎶 **Real audio playback** — powered by Media3 / ExoPlayer
* 📂 **Local file picker** — Android Storage Access Framework
* 💾 **Room database** — songs, favorites, play counts, and metadata stored locally
* 🔍 **Full-text search** — search by title, artist, album, mood, language, tags, and lyrics
* 📻 **Gratia Radio** — shuffle your own local library
* ❤️ **Favorites** — save your best songs
* 📝 **Lyrics support** — plain lyrics, LRC synced lyrics, and enhanced word-level lyrics
* 🎧 **Mini-player and expanded player**
* 🔔 **Media notification and lock-screen controls**
* 🎨 **Custom Gratia UI** — dark, warm, glass-style interface

## Building

```bash
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

## Architecture

```text
com.gratia.music/
├── data/           # Room database, entities, DAOs, repository
├── lyrics/         # Lyrics models, parser, timing engine
├── player/         # Media3 ExoPlayer, ViewModel, playback service
├── storage/        # Storage provider interface + implementations
├── ui/
│   ├── theme/      # Gratia design system
│   ├── screens/    # Home, Search, Radio, Favorites, Upload, Lyrics, Settings
│   ├── lyrics/     # Kinetic lyrics UI
│   └── components/ # PlayerBar, ExpandedPlayer, MusicCard, SongRow
```

## Privacy

Gratia does **not**:

* Connect to external servers
* Track your listening habits remotely
* Upload your songs or lyrics
* Require an account

All data stays on your device in a local Room database.

## Upcoming Features

* On-device music recommendation system
* On-device lyrics alignment
* Better word-by-word synced lyrics
* More stable local library scanning
* More polished Gratia glass UI

## Current Work

I am currently working on improving Gratia step by step. The app may not be perfect yet, but I will keep fixing bugs, improving performance, and adding new features.

Thank you for understanding and supporting the project.

## Credits & Legal Notices

This project includes or adapts open-source components and implementation ideas from GPLv3-licensed Android music player projects. See Open-source licenses for details.

## License

GNU General Public License v3.0
