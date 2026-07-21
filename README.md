# Gratia (under devlopment)

> A modern, private Android music player focused on smooth playback and a personal listening experience.
> 
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


## Contributing

Pull requests are always welcome! If you're planning a major feature or significant architectural change, please open an issue first to discuss your ideas.

## License

This project is licensed under the GNU General Public License v3.0 - see the `LICENSE` file for details.

---

Built with Kotlin and Jetpack Compose.

Designed to be fast, private, and personal.
