# Gratia 🎵

Gratia is a privacy-first Android music player inspired by modern local music players, rebuilt with a custom Gratia design system and kinetic lyrics engine.

**Your music. Your storage. Your rules.**

## Download

The latest APK is available from the GitHub Releases page.

1. Open the latest release.
2. Download `gratia-v0.1.0-alpha.apk`.
3. On Android, allow “Install unknown apps” if prompted.
4. Install and open Gratia.

### Supported Storage Providers

| Provider | Status |
|----------|--------|
| 📱 **Local Device** | ✅ Working |


## Features

- 🎶 **Real audio playback** — Media3/ExoPlayer
- 📂 **Local file picker** — Android SAF (Storage Access Framework)
- 💾 **Room database** — songs, favorites, play counts all stored locally
- 🔍 **Full-text search** — title, artist, album, mood, language, tags, lyrics
- 📻 **Gratia Radio** — shuffle your own library
- ❤️ **Favorites** — heart your best songs
- 📝 **Lyrics** — add and view lyrics per song
- 🎨 **Cosmic dark theme** — UI with glassmorphism and emerald accents

## Building

```bash
./gradlew assembleDebug
```

## Architecture

```
com.gratia.music/
├── data/           # Room database, entities, DAOs, repository
├── player/         # Media3 ExoPlayer, ViewModel, playback service
├── storage/        # Storage provider interface + implementations
├── ui/
│   ├── theme/      # Gratia design system (colors, typography)
│   ├── screens/    # Home, Search, Radio, Favorites, Playlists, Upload, Lyrics
│   └── components/ # PlayerBar, ExpandedPlayer, MusicCard, SongRow
```

## Privacy

Gratia does **not**:
- Connect to any external servers
- Track your listening habits remotely
- Upload your data anywhere
- Require an account

All data stays on your device in a local Room database.
## Upcomming fetures : On device AI algorithm 
## currently working : On device AI lyrics which will scan the song and give word to word lyrics 
## On it             : Without even draining your battry 


## Note
guys im currenly working and trying to make it better, maybe its not perfect but ill do my best . thaink you for understanding  
## Credits & Legal Notices

This project includes or adapts open-source components and implementation ideas from GPLv3-licensed Android music player projects. See Open-source licenses for details.

## License

GNU General Public License v3 (GPLv3)

