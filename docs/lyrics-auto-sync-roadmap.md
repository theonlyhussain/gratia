# Lyrics Auto Sync & Forced Alignment Roadmap

This roadmap outlines the plan for adding local, privacy-first forced alignment to Gratia, allowing the app to automatically synchronize plain text lyrics with audio playback.

## Architecture Vision
The auto-sync engine will take raw audio bytes and plain text lyrics on the device and produce precise time alignments down to the word level (outputting in either Enhanced LRC or JSON word-level sync format). All operations must run locally to maintain privacy and offline usability.

---

## Phased Rollout Plan

### Phase 1: Manual Plain Lyrics (Complete)
- Support for manual copy/pasting of plain text lyrics.
- Static, readable text rendering with paragraph spacing.
- No auto-scroll or seeking hooks.

### Phase 2: Manual Line-Synced LRC (Complete)
- Support for standard LRC formats (`[mm:ss.xx]`).
- Kinetic scroll behavior vertically centering the active line.
- Text scale, opacity, and blur changes for active, past, and future lines.
- Tapping on a lyric line seeks playback to the line's timestamp.

### Phase 3: Manual Word-Synced ELRC / JSON (Complete)
- Support for Enhanced LRC (`[mm:ss.xx] <mm:ss.xx> word`) and custom JSON word-level syntax.
- Rendering of individual word tokens using Compose `FlowRow`.
- Word glow highlights, opacity fading, and scale transformations.
- Single text input with real-time format detection and validation.

### Phase 4: Local Forced Alignment Research (Future)
- **Audio Pre-processing**: Extract log-mel spectrogram features natively in Kotlin/Java.
- **Model Selection**: Evaluate lightweight ONNX models (e.g., Wav2Vec2-Align or Whisper-Tiniest) optimized for on-device execution.
- **Viterbi Decoding**: Implement the CTC alignment search algorithm locally to trace the optimal path matching phonemes to audio frames.

### Phase 5: On-Device Model Experimentation (Future)
- **ONNX Runtime Mobile**: Integrate ONNX Runtime Android libraries to execute pre-trained alignment models on local hardware.
- **Background Worker**: Align tracks in the background using Android `WorkManager` so the UI remains responsive.
- **Exporting**: Save the generated timestamps back into the Room database under `lyricsSynced` in ELRC or JSON format.
