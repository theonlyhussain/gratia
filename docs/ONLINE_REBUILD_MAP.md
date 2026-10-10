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
- The Home/Explore *page* caches are **in-memory only**, so a cold start offline
  has nothing cached. Explore's per-category **artwork** index is now on disk
  (see §9); the shelves themselves are not.

## 8. AutoPlay is a radio, not a "next" button (this change)

The reported bug: enable Infinity Play on a remote song and the next track was
the next row of the search results. AutoPlay was implemented as "when the queue
runs out, generate one more track" — the radio endpoint was never asked, and the
seed was the queue's next item rather than the track being heard.

- **`RadioQueue`** holds the rules as pure functions, so they are unit-testable
  without a player, a network or a database (`player/RadioQueueTest.kt`).
- **Seed** is always the *currently playing* remote track's `videoId`; the
  remote answer is YouTube Music's own watch queue (`next` / `RDAMVM<videoId>`) —
  already present as `Innertube.next`, now actually used as the continuation.
- **Filtering** drops the seed itself, anything already queued, anything already
  suggested, anything played recently, and *every* local file (a local track
  cannot continue a radio).
- **Queue separation.** The queue reads `… context … | current | explicit tracks
  | AutoPlay suggestions | rest of the context …`. Tracks the listener queued by
  hand (`playNext` / `addToQueue`) always precede suggestions; a list a song was
  played *from* (search results, a shelf) is a context, not a queue, so a
  suggestion follows the track being heard and the rest of the search results do
  not jump ahead of it. This is the `§101` acceptance test.
- **Refill happens early** — while three suggestions are still ahead — so the
  next batch is fetched behind the music rather than in the silence after it.
- **Dedup** is session-level: `autoplayPlayedIds` is every id AutoPlay has ever
  offered, `autoplayQueuedIds` is the same set restricted to what is in the queue.
- A **local** playback seed falls back to the existing local generator, and its
  output never mixes into a remote chain.
- Development logging: `AUTOPLAY_ENABLED`, `AUTOPLAY_SEED_CHANGED`,
  `AUTOPLAY_RADIO_REQUEST`, `AUTOPLAY_RESULTS`, `AUTOPLAY_FILTERED_SEED`,
  `AUTOPLAY_FILTERED_DUPLICATES`, `AUTOPLAY_APPENDED`, `AUTOPLAY_FETCH_MORE`,
  `AUTOPLAY_REFILL_SKIPPED`, `AUTOPLAY_EXHAUSTED`, `AUTOPLAY_ERROR`.

## 9. Explore artwork: cache first, refresh later (this change)

- The per-category cover index (`category id → url → fetchedAt`) is persisted in
  `SettingsDataStore` (`explore_category_artwork`, 7-day TTL) and warmed into
  `ProviderManager` once per process (`primeCategoryArtwork`).
- Explore seeds its cover map from that index **before** rendering, so a return
  visit draws real artwork on the first frame and refreshes behind it.
- The first eight covers are preloaded into Coil as they resolve — several
  screenfuls of lead, not a download queue.
- Coil disk keys are normalized to **the asset, not the drawn size**
  (`artworkCacheKey`), so the row-size and header-size requests of one cover
  share one download. Memory keys stay size-aware.
- Logging: `EXPLORE_CACHE_HIT/MISS/REFRESH`,
  `CATEGORY_ARTWORK_CACHE_HIT/LOAD/FAIL`.

## 10. Smart Update removed; updates are manual (this change)

- The feature is gone end-to-end: `SmartUpdateScreen`, `SmartUpdateSheets`,
  `UpdateBanner`, the `smartUpdate` route, the settings row, and both DataStore
  keys (deleted once, on first launch of this build).
- `GratiaApp.scheduleUpdateCheck()` — which enqueued a 24-hour periodic update
  check on **every cold start** — is replaced by
  `UpdateCheckWorker.cancelLegacySchedule()`, so an install that already had the
  work on disk has it cancelled. A run that fires before that lands does nothing.
- `UpdateScreen` replaces it: current version, latest version from the server,
  release notes, download size and real byte progress, and a message for each
  way it can fail (offline, HTTP, invalid release, storage, unverified APK,
  installer unavailable, permission needed).

## 11. Crossfade: why it never fired (this change)

Root cause: `TransitionController` found the next track by reading the
**player's own timeline** — `timeline.getNextWindowIndex(...)` — but
`PlayerManager` keeps exactly one `MediaItem` in the player at a time
(`controller.setMediaItem` per track). That call always returned
`C.INDEX_UNSET`, `nextMediaItem` was always null, and the scheduler returned at
its "No next track" branch on every track. The fade code was never reached.

Fix: the next track is a question about the queue, so it is asked of the queue.
`GratiaPlayerEngine.setNextItemProvider` is set by `PlaybackService` to
`PlayerManager.resolveNextMediaItemForCrossfade`, which resolves the next row
(downloaded file → local URI → remote stream, in that order of preference) into
a ready `MediaItem` without touching playback. `addTrackTransitionedListener`
reports the incoming media id back so the queue index, now-playing state and the
AutoPlay seed follow a crossfade — previously the app would have described the
song that had already faded out.

Also here: crossfade duration `0` returns **before** resolving anything (a
stream fetched for a transition that will not happen is a wasted round trip);
the fade loop aborts when the listener pauses, so the outgoing track cannot keep
playing under a paused screen; and `CROSSFADE_PREPARE / READY / NEXT_PREPARE /
TRIGGER / START / PROGRESS / IN / OUT / SWAP / COMPLETE / CANCEL / CANCELLED /
DEGRADED / ERROR` make each stage verifiable in logcat.

Not verified on a device: the audible overlap. See "Remaining limitations".

## 12. Home pull-to-refresh (this change)

- `GratiaPullToRefresh` wraps Material3's `PullToRefreshBox` with Gratia's
owner indicator colours — the platform gesture, so the drag physics and the
threshold are the ones users already know.
- Cached shelves stay on screen while the indicator turns; the refresh is
feedback, not a loading screen, and the scroll position is untouched.
- A request **generation** guards the write: if a pull lands while an earlier
fetch is running, the older result is discarded rather than allowed to overwrite
the newer one. A pull also forces a real network refresh (`forceRefresh`).
- `HomeScreen`'s Top Picks effect is keyed on the **seed artist list** rather
than on `mostPlayed`, so a play-count write no longer restarts a catalogue
search per seed artist.

## 13. Daily Mix: history signals with recency decay (this change)

Root cause: the Daily Mix was scored from **lifetime aggregates on the `songs`
row** — `playCount`, `totalListenTime`, `completedCount`, `skipCount`. Those are
running totals with no decay, so a track with hundreds of plays two years ago
outranked the track played this morning; the mix was a lifetime top-ten wearing
a "Daily" label. It also shuffled with `Math.random()`, so the same listener
never got the same mix twice and the ranking could not be tested at all.

Fix: the Daily Mix now reads the **playback timeline** (`playback_history`),
where every row is one real play with a `playedAt` and a `listenedSeconds`.

- `data/repository/DailyMixScoring.kt` — the pure model. Each play's weight is
  its measured engagement (a completed listen counts full; a partial one scales
  with how much was heard) multiplied by an exponential **decay** with a 14-day
  half-life. Signals roll up into per-track and per-artist affinity; an unplayed
  track by a returning artist earns a discovery bonus; the most recent play of a
  track adds a small freshness nudge.
- `RecommendationManager.getDailyMix` reads the newest 1000 plays, maps them to
  `DailyMixSignal`s, and ranks. Candidates are the songs table **plus** tracks
  that only ever existed as a play (rebuilt from the history row's own snapshot),
  so a streamed-but-unsaved track can appear in the mix instead of being invisible.
- Ordering interleaves by artist and caps each artist at 3 slots, replacing the
  old shuffle — deterministic, so it is unit-testable and so a listener sees a
  stable mix within a day.
- No clock of its own: `now` is passed in, which is what makes the decay testable.

Pinned by `DailyMixScoringTest` (decay curve, recency beating lifetime counts,
completion weighting, artist discovery, per-artist cap, alternation, exclusions,
determinism).
