# Gratia Architecture Migration: Native YouTube Music + Local First-Class Providers

## Executive Summary

Gratia is transitioning from a local-only music player to an online-first architecture where Local Music and YouTube Music coexist as equal, first-class providers.

Previously, a Python-based `ytmusicapi` backend service was considered. However, this introduced unnecessary middleware complexity, required a local server on the device, and was not native to Android. We are pivoting to a Native Kotlin implementation inspired by **BitChord**. 

This document outlines the architectural boundaries, provider contracts, and the deprecation of the Python service.

## 1. Native Innertube Implementation (The BitChord Approach)

Instead of relying on a Python backend, Gratia will implement a lightweight, native Kotlin Innertube client directly inside the Android app. 

*   **No Python Middleware:** The app will make direct HTTPS requests to `music.youtube.com/youtubei/v1` and `www.youtube.com/youtubei/v1`.
*   **Player Clients:** A robust `StreamResolver` will be implemented to cycle through various YouTube client identities (e.g., `ANDROID_MUSIC`, `TVHTML5`, `IOS`) to fetch stream URLs reliably without triggering bot checks.
*   **Signature Decryption:** We will use NewPipe Extractor's backend mechanisms (or similar native equivalents) to solve JavaScript player signatures when required by ciphered URLs, ensuring reliable stream resolution.
*   **Data Models:** The repository layer will parse Innertube JSON responses into strongly-typed Kotlin domain models (`Song`, `HomeFeed`, `LibraryPage`, `Playlist`).

## 2. Provider Abstraction

The core of Gratia will rely on a strict provider abstraction:

*   **`MusicProvider` Interface:** 
    *   `id: String` (e.g., `"local"`, `"ytmusic"`)
    *   `search(query: String)`
    *   `getHomeFeed()`
    *   `resolveStream(trackId: String): Stream`
    *   `getLyrics(trackId: String)`
*   **Identity Management:** 
    *   Local tracks: `local_{MediaStore_ID}`
    *   YouTube Music tracks: `ytm_{Video_ID}`
*   **ProviderManager:** A singleton/injected component that delegates requests (e.g., search, resolving stream URLs) to the appropriate provider based on track identity.

## 3. UI and State Layer 

The UI will not care where the music comes from. 
*   **Unified Queues:** A queue can seamlessly contain a mix of Local tracks and YouTube Music tracks.
*   **Dynamic Resolution:** The `PlayerManager` will ask the `ProviderManager` to resolve the stream URL right before playing. If a YouTube URL expires (returns 403), `PlayerManager` will intercept the ExoPlayer error and re-resolve the track silently.

## 4. Offline Downloads

*   Downloaded YouTube Music tracks will be stored securely.
*   The `ProviderManager` will transparently route playback to the local downloaded file if it exists, rather than streaming from the network, saving data and providing offline playback capability.
*   Bitrate/Quality settings will dictate download size. We will prefer `m4a` extraction as required by Android MediaStore limitations for audio inserts.

## 5. APK Installation Issue

*   **Root Cause:** The `AndroidManifest.xml` contains a literal `` `n `` string appended to `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`, making the XML malformed and crashing the PackageInstaller.
*   **Resolution:** Remove the malformed characters to restore standard APK compilation and installation.

## 6. Migration Steps

1.  **Cleanup:** Remove all references to the Python backend service, `uvicorn`, and related Python dependencies in Gratia.
2.  **Fix Manifest:** Repair the APK installation loop issue.
3.  **Implement Innertube Native:** Port the core Innertube HTTP and StreamResolver logic from BitChord into Gratia (`com.gratia.music.provider.ytmusic.innertube`).
4.  **Connect Provider:** Hook the new native client into the `MusicProvider` abstraction.
5.  **Refactor ExoPlayer:** Ensure ExoPlayer resolves streams via the new native Kotlin client.
6.  **Implement Downloads:** Build the offline downloading system using WorkManager or native download APIs, linking downloaded tracks to their `ytm_` identities.
