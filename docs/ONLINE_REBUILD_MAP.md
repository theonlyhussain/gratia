# Gratia Online-First Rebuild — Architecture Map & Gap Analysis

Status snapshot taken against the **local working tree** (branch `beta`), not the
public repository. Phase 0 of the rebuild spec explicitly warns that the two
differ, and they do: a large part of the remote foundation already exists here.

## 1. What already exists

### Build
- Single Gradle module `:app`. Kotlin 1.9.22, Compose, KSP, Room, WorkManager,
  Media3, OkHttp, Ktor, Coil, NewPipe Extractor. `versionCode 18 / 2.3.8`.
- `services/ytmusic/` (legacy Python backend) still on disk but unreferenced.
  See `docs/YTMUSIC_MIGRATION.md`.

### Data layer (`data/`)
- Room **v11**, 10 migrations. Tables: `songs`, `playlists` / `playlist_songs`,
  `collections` / `collection_songs`, `user_profile`, `listening_events`,
  `lyrics`, `artists`, `albums`, `artwork_cache`, `sync_queue`.
- **`SongEntity` is already the unified track model.** It carries
  `storageProvider`, `providerTrackId/ArtistId/AlbumId`, `artworkUrl`,
  `isDownloaded`, `downloadPath`, `localUri`, plus listen stats.
- Remote identity is stable: `id = "ytm_$videoId"` (`RemoteTrackMapper`), with
  `localUri` left **null** for remote tracks — the expiring stream URL is never
  persisted as identity.
- `RecommendationManager` (daily-mix scoring), `ListeningEventRepository` +
  `ListeningEventDao`, `SongIdentityEngine`.

### Provider / remote layer (`provider/`)
- `MusicProvider` interface, `ProviderManager` (in-memory stream cache with
  expiry, `resolvePlayback`, `resolveForDownload`), `YouTubeMusicProvider`.
- `RemoteModels`: `RemoteTrack`, `RemoteArtist`, `RemoteAlbum`,
  `RemotePlaylist`, `RemotePage<T>` (continuation/pagination),
  `RemoteSearchResponse`, `RemoteHomeSection`, `PlaybackSource`, `RemoteLyrics`.
- `ytmusic/innertube`: `Innertube`, `InnertubeParser`, `PlayerClient`, and a
  production-grade **`StreamResolver`** (client walk, cipher solving, 403 /
  expiry / bot-check / permanently-unplayable handling, probe validation).
  **Do not rewrite this.**

### Playback (`player/`)
- **One authority: `PlayerManager`.** Mixed `List<SongEntity>` queue, prefers a
  completed download over streaming, re-resolves on 403, surfaces failures.
- `PlaybackService` (MediaSessionService), `GratiaPlayerEngine` (dual-player
  crossfade), `TransitionController`, `PreloadManager` (read-ahead + remote
  pre-resolve), `PlaybackDataSources` / `ChunkedDataSource`, `AutoPlayEngine`,
  `RetentionManager`, `SleepTimerManager`, `MediaOutputManager`, Equalizer.

### Lyrics (`lyrics/`, `ui/lyrics/`)
- TTML, JSON word lyrics, word-level timing, animated word fill, internal blur,
  active-word state, auto-scroll. **Out of scope — untouched by this rebuild.**

### Downloads (`download/`)
- `DownloadManager` (one-shot WorkManager) + `DownloadWorker` (resolve `m4a`,
  OkHttp fetch into `filesDir/downloads`, artwork, lyrics pre-fetch, writes
  `isDownloaded` / `downloadPath`).

### UI
- Bottom tabs: Home, Browse, Library, Search. Remote artist / album / playlist
  screens exist and are wired into the nav graph.

## 2. Gap analysis (mapped to the rebuild spec)

| Spec area | Status |
|---|---|
| Stages 1–4 — remote playback, mixed queue, remote search, artist/album/playlist pages | **Largely done** |
| Stage 5 — remote artwork/metadata cache | Partial (Coil + disk only; no catalog cache) |
| Stage 6 — **Home online catalog** | **Broken:** `HomeScreen` fetched `getHome()` and then never rendered it; Home was local-first |
| Stage 7 — Explore / categories | **Done** — see §5. (`RemoteVideo` still absent; videos are dropped from browse shelves as dead ends.) |
| Stages 8 / Phase 13, 15, 42 — personalization states | **Absent:** Daily Mix rendered with no minimum-data threshold |
| Stages 9/10 — downloads + On Device library | Partial: On Device split **done** (§6); download queue/progress/pause/cancel still absent |
| Phases 27–30 — offline/error states, cache, resilience | Partial: connectivity monitor + Home/Explore offline states **done** (§7); catalog cache is memory-only, Search and playback offline states still open |
| Phase 23 — favorites as separate states | Overloaded onto `SongEntity.isFavorite` |
| Phase 2/3 — `PlayableSource` / `RemotePlaybackResolver` naming | Functionally present under different names (`PlaybackSource`, `StreamResolver`) |

## 3. Order of work

Per Phase 40, but starting from what is actually built:

1. ~~Remote playback~~ (done)
2. ~~Unified queue~~ (done)
3. ~~Remote search domain~~ (done)
4. ~~Artist / album / playlist pages~~ (done)
5. Remote catalog cache (**in progress** — Home cache below)
6. ~~Home online catalog + honest personalization states~~ (done — §4)
7. ~~Explore / moods / genres~~ (done — §5)
8. Favorites + history + lightweight personalization
9. Downloads / offline subsystem
10. ~~On Device library redesign~~ (split done — §6; download manager still to come)
11. Advanced recommendations / radio / autoplay
12. Optional account sync

## 4. Home rework (this change)

- Render the remote Home shelves that were previously fetched and discarded.
- Add `HomePersonalization` (`FIRST_RUN` / `EARLY_USE` / `PERSONALIZED`).
  - `FIRST_RUN`: discovery only — remote shelves, On Device. No fake "Top Picks"
    or "Daily Mix".
  - `EARLY_USE`: adds Recently Played and (real, play-count-backed) Top Picks.
  - `PERSONALIZED`: adds Daily Mix once enough listening data exists.
- Daily Mix is gated behind `MIN_PERSONALIZED_PLAYS` distinct played tracks and
  `MIN_PERSONALIZED_ARTISTS` distinct artists.
- On Device (Downloads / Local Music) moves to a lower section, so Home reads as
  discovery first.
- Loading / empty / offline states on Home instead of silent failure, plus a
  stale-while-refresh memory cache so Home does not re-request every shelf on
  every visit.

## 5. Explore (this change)

- `BrowseScreen` is no longer a local genre grid. It is the online Explore
  landing, built from YouTube Music's own Moods & genres taxonomy
  (`FEmusic_moods_and_genres`) — fetched, never hard-coded — cached in
  `ProviderManager` with the same stale-while-refresh rule as Home.
- `BrowseCategoryScreen` renders one category (Hindi, Chill, 1990s, …) as the
  shelves the catalogue returns, each independently scrollable, with loading /
  empty / error + Retry states.
- New domain types: `BrowseCategory`, `BrowseSection`, `BrowsePage`, and a
  typed `BrowseEntry` (`Track` / `Album` / `Artist` / `Playlist`) so every card
  routes to the right screen, and tracks go into the unified player.
- `InnertubeParser` gained `parseCategories` (from
  `musicNavigationButtonRenderer`, id **and** params) and `parseBrowseShelves`
  (walked, so category/explore/mood pages all parse without a fixed path),
  classifying cards by their stated page type with id-prefix fallbacks.
- `MusicProvider` gained `getBrowseCategories()` / `getBrowsePage(category)`
  with defaults, so the UI never learns how a browse request is shaped.
- The bottom-tab label changed from "Browse" to "Explore"; local genre browsing
  remains available through Search and the Library (Phase 37).

### Not done here
- **Continuation/pagination of category shelves (Phase 18).** A category page
  currently renders the first batch of each shelf; the token is available but
  the UI does not yet request more.
- **`RemoteVideo` / video shelves.** Deliberately dropped as dead ends rather
  than parsed.

## 6. On Device library (this change)

Library now opens on an **On Device** section with three distinct views, which
is the separation Stage 10 / Phase 11 asks for:

| View | Membership |
|---|---|
| Downloads | `isDownloaded` — remote tracks Gratia saved for offline |
| Local Music | `storageProvider == "local" && !isDownloaded` — files already on the phone |
| All On Device | the union of the two |

The two sets are disjoint: a Gratia-managed download never reads as a
user-imported MP3, and a local file is never counted as a download (Phase 26).
Each view has its own copy for the empty state, because "no downloads" and "no
local music" are different facts. Rows carry live counts.

Also fixed while here: the selection toolbar now operates on the rows the
current sub-view lists (`activeSongs`) rather than the whole library, so
"select all" and delete no longer act on songs that are not on screen.

Home's On Device row now opens **Local Music** rather than all Songs.

### Still to come (Stage 9)
- Download queue with progress / pause / cancel / retry / delete, and a
  downloaded badge in the expanded player.

## 7. Offline detection (this change)

- `data/network/NetworkMonitor.kt` — one source of truth for connectivity,
  initialised from `Application.onCreate`, exposing `isOnline: StateFlow<Boolean>`
  plus a synchronous `isCurrentlyOnline()`. Uses `NET_CAPABILITY_INTERNET`
  rather than `VALIDATED` (see the class doc for why). `onLost` re-reads state
  instead of assuming offline, so a Wi-Fi → cellular handover is not a false
  offline.
- `ACCESS_NETWORK_STATE` added to the manifest.
- **Home** and **Explore** (landing and category pages) are re-keyed on
  connectivity, so they **refill themselves the moment a network returns**
  rather than waiting for the user to navigate away and back.
- With no network and no cached page, each shows an honest **"You're offline"**
  state with a Retry — instead of a spinner, and instead of the old empty state
  that read as "your library was wiped".
- With no network but a **cached page present**, the cache is shown and no
  offline notice appears: the content is real, so nothing needs saying.
- Home points at what still works offline — "Downloads and local music are
  still available below" — and the On Device section keeps rendering from the
  database.

### Still open (Phases 27–28)
- **Search** does not yet mark remote results unavailable offline.
- **Playback**: a non-downloaded remote track still walks the resolver offline
  and fails; `NetworkMonitor` could short-circuit it with a clearer message.
- The catalog cache is **in-memory only**, so a cold start offline has nothing
  cached; a disk/database cache (Phase 29) is still to do.
