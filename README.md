# Gratia

> A modern, private Android music player focused on beautiful design, smooth playback, and a personal listening experience.
> 
### Playback
* Gapless-inspired playback
* Queue support
* Shuffle
* Repeat
* Mini Player
* Expanded Player
* Media notification
* Lock screen controls
* Reliable playback

### Library
* Songs
* Albums
* Artists
* Folders
* Playlists
* Favorites
* Recently Played

### Experience
* Dynamic backgrounds
* Glassmorphism
* Smooth animations
* Theme switching
* Light mode
* Dark mode
* Adaptive artwork colors

### Lyrics
* Synced lyrics
* Smooth scrolling
* Tap to seek
* Manual lyric support
* Beautiful empty state

## Technology

| Component            | Technology              |
| -------------------- | ----------------------- |
| Language             | Kotlin                  |
| UI                   | Jetpack Compose         |
| Architecture         | MVVM                    |
| Database             | Room                    |
| Media                | Media3 / ExoPlayer      |
| Image Loading        | Coil                    |

## Installation

### Download APK

You can download the latest compiled APK from the GitHub Releases page:

[GitHub Releases](https://github.com/theonlyhussain/gratia/releases/latest)

### Build from source

To build Gratia locally, clone the repository and assemble the release build:

```bash
git clone https://github.com/theonlyhussain/gratia.git
cd gratia
./gradlew assembleRelease
```

## Project Structure

```text
app/src/main/java/com/gratia/music/
 ├── data/
 ├── lyrics/
 ├── player/
 ├── storage/
 └── ui/
      ├── components/
      ├── lyrics/
      ├── player/
      ├── screens/
      └── theme/
```

## Roadmap

**Completed**
* [x] Local music playback
* [x] Queue
* [x] Playlist support
* [x] Dynamic player
* [x] Light theme
* [x] Dark theme
* [x] Lyrics
* [x] Media notification

**Planned**
* [ ] Android Auto
* [ ] Wear OS
* [ ] Equalizer
* [ ] Chromecast improvements
* [ ] Smart recommendations
* [ ] Folder artwork generation
* [ ] More lyric providers

## Contributing

Pull requests are always welcome! If you're planning a major feature or significant architectural change, please open an issue first to discuss your ideas.

## License

This project is licensed under the GNU General Public License v3.0 - see the `LICENSE` file for details.

---

Built with Kotlin and Jetpack Compose.

Designed to be fast, private, and personal.
