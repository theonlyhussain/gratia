# Gratia YouTube Music Provider Backend Service

FastAPI-based backend service providing full YouTube Music integration for the Gratia Android Music Player using [`ytmusicapi`](https://github.com/sigma67/ytmusicapi) and [`yt-dlp`](https://github.com/yt-dlp/yt-dlp).

## Architecture

```
┌─────────────────────────┐          HTTP/JSON          ┌───────────────────────────┐
│      Gratia Android     │ ──────────────────────────> │   FastAPI Backend Server  │
│  (Kotlin, Jetpack Compose│ <────────────────────────── │  (Python 3.11, ytmusicapi)│
│    Media3 / ExoPlayer)  │                             └─────────────┬─────────────┘
└─────────────────────────┘                                           │
             │                                                        ▼
             │                                           ┌──────────────────────────┐
             │       Direct Audio Stream URL             │      YouTube Music       │
             └─────────────────────────────────────────> │   (Audio CDN / Googlevideo)
```

1. **Provider Independence**: Android app defines a generic `MusicProvider` interface. YouTube Music is just one provider.
2. **Just-In-Time Playback Resolution**: Stream URLs are resolved on-demand when a track is requested to play and are never permanently saved to Room DB.
3. **No Fake Local Files**: Tracks stream directly through Android's native Media3 / ExoPlayer pipeline.

---

## Requirements

- Python 3.10+
- Internet access

---

## Quick Start

### 1. Create Virtual Environment & Install Dependencies

```bash
cd services/ytmusic
python -m venv .venv

# Windows
.\.venv\Scripts\activate
pip install -r requirements.txt

# macOS / Linux
source .venv/bin/activate
pip install -r requirements.txt
```

### 2. Run the Service

```bash
# Windows
.\.venv\Scripts\uvicorn app:app --host 0.0.0.0 --port 8000 --reload

# macOS / Linux
uvicorn app:app --host 0.0.0.0 --port 8000 --reload
```

The service will start on `http://0.0.0.0:8000`.

---

## API Reference

| Endpoint | Method | Description |
|---|---|---|
| `/api/ytmusic/health` | GET | Health check and status |
| `/api/ytmusic/search?q={query}&filter={filter}` | GET | Search songs, artists, albums, playlists |
| `/api/ytmusic/songs/{videoId}` | GET | Detailed track metadata |
| `/api/ytmusic/playback/{videoId}` | GET | Resolves playable audio stream URL via yt-dlp |
| `/api/ytmusic/artists/{channelId}` | GET | Artist bio, top songs, albums, singles, related |
| `/api/ytmusic/albums/{browseId}` | GET | Album metadata and full track list |
| `/api/ytmusic/playlists/{playlistId}` | GET | Playlist metadata and full track list |
| `/api/ytmusic/lyrics/{videoId}` | GET | Native YouTube Music lyrics |
| `/api/ytmusic/related/{videoId}` | GET | Related songs for infinite Autoplay |
| `/api/ytmusic/home?limit={n}` | GET | Home sections, top charts, and new releases |

---

## Android Configuration

In Gratia Android:
- **Emulator default**: `http://10.0.2.2:8000`
- **Physical device on local network**: `http://<YOUR_COMPUTER_LAN_IP>:8000` (e.g. `http://192.168.1.50:8000`)
- **Production deployment**: Your public domain or VPS IP (e.g. `https://ytmusic.yourdomain.com`)

The backend URL can be adjusted dynamically in the app via `SettingsDataStore`.

---

## Authentication Setup (Phase 6 / Future)

The service runs unauthenticated by default, accessing the entire public YouTube Music catalogue without requiring any user credentials.

To enable user-specific features (user playlists, personal library, liked songs):
1. Run `ytmusicapi oauth` in terminal to generate an `oauth.json` token.
2. Set the environment variable:
   ```bash
   export YTM_AUTH_FILE=/path/to/oauth.json
   ```
3. Restart the service. `YTMusicService` will automatically detect and use the authenticated session.
