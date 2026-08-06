# Gratia Project Brain

## Project Overview
Gratia is a local-first, beautiful, and fluid music player for Android. It emphasizes offline performance, elegant aesthetics inspired by Apple Music, and a gesture-driven user experience.

## App Philosophy
- **Local-first**: No reliance on cloud streaming for core playback; data remains local.
- **Aesthetic Excellence**: Focus on typography (SpaceGrotesk, Inter), micro-animations, glassmorphism, and a polished UI.
- **Fluidity**: Interactions must have immediate visual and haptic feedback. Use spring animations and bouncy clicks.
- **Complexity Conservation**: The developer absorbs complexity. The UI must remain clean, hiding advanced features behind progressive disclosure.

## Current Architecture
- **Pattern**: Manager → Repository → StateFlow → Compose UI
- **UI Layer**: Jetpack Compose
- **Playback Engine**: Jetpack Media3 (ExoPlayer) via PlaybackService`n- **Database**: Room Database (SongDao, etc.)
- **Image Loading**: Coil
- **Asynchronous Flow**: Kotlin Coroutines & Flow

## Folder Structure (pp/src/main/java/com/gratia/music)
- /audio: Audio processing/equalizer logic.
- /data: Room DB, DAOs, repositories, metadata fetching, and WorkManager workers.
- /lyrics: Lyrics parsing and API integrations.
- /player: PlaybackService, PlayerViewModel, Media3 integrations.
- /storage: File system and storage access.
- /ui: Compose screens, components, theme, and navigation.
- /updater: Custom OTA GitHub updates logic.

## Navigation Flow
Managed by GratiaAppRoot (which controls a bottom navigation bar and full-screen routes).
Primary tabs: Home, Search, Library.

## Screens
- **HomeScreen**: Overview, recently played, suggested.
- **SearchScreen**: Local search, persistent search history, and distinct "Browse Categories" displayed simultaneously.
- **LibraryScreen**: Playlists, favorites, folders, artists, albums.
- **PlayerScreen**: Now playing, lyrics, queue.
- **SettingsScreen**: Preferences, OTA updates.
- **AboutScreen**: Developer info, licenses, open-source links.
- **ProfileScreen**: User stats and customization.

## APIs and Services
- **Deezer API**: High-quality cover art, artist imagery, genre data.
- **LRCLIB**: Open source synced lyrics provider.
- **Custom OTA Update**: Checks GitHub releases for updates via WorkManager.

## Background Tasks
- UpdateCheckWorker: Periodically checks for app updates.
- MediaScannerWorker: Scans local storage for new music files.

## Permissions
- READ_EXTERNAL_STORAGE / READ_MEDIA_AUDIO: Access local music.
- FOREGROUND_SERVICE / FOREGROUND_SERVICE_MEDIA_PLAYBACK: Background playback.
- POST_NOTIFICATIONS: Playback controls in notification.
- INTERNET: Fetch metadata, lyrics, and app updates.
- REQUEST_INSTALL_PACKAGES: For OTA updates.

## Theme and Design Language
- **Colors**: Rich, high-contrast, dark mode focused.
- **Typography**: SpaceGrotesk (headings), Inter (body text).
- **Motion**: Staggered list animations, ounceClick for all interactable elements.

## Current Known Limitations
- Background notification stability on older Android versions.
- Occasional playback glitches on app launch.

## Future Roadmap
- Enhance playlist management (editing names).
- Fix background notification issues (Android 12).

