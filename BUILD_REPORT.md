# Spock 3.0 — final validation report

Version name: 3.0  
Version code: 30  
UI version: 3.0  
Application ID: `io.github.unidentified24.spock`

This build was created from the Codex working copy at `work/spock`; the original `Documents/Spock` folder was not edited. The final APK is in `Apk/Spock.apk` and `Apk/Spock-3.0.apk`.

## Playback continuity and local music

- Added a native-stream progress watchdog that refreshes a stalled stream once from its saved position, while respecting intentional pauses, audio-focus pauses, repeat-one, and crossfades.
- Kept the playback service alive through temporary buffering when the user dismisses the app, and added explicit audio-focus handling for Spotify Web playback.
- Start radio discovery before the final queued track ends so the existing source-preparation path can prepare a continuation. Offline-only queue exhaustion uses other downloaded/local tracks without going online.
- The player widget follows play/pause intent; local entries whose file permission has expired can be relinked while preserving library identity.
- Added storage overview information for offline tracks, streaming cache and available space.

## Validation

- Release APK build: **successful**.
- Unit tests: **141 passed, 0 failed** (131 app, 8 YouTube/Innertube, 2 Spotify).
- APK signature: **verified** with APK Signature Scheme v2.
- Upgrade certificate: **matches Spock 2.8** (`1a9e5c4a5af8434f0c40fdc61a830021d8cb692b6f06e6807a973d9055c35161`).
- Emulator playback test: **not run**. The configured API 37 emulator exited before appearing in ADB, so background buffering and continuous-play behavior still need device/emulator verification.
- GitHub source synchronization and live release publishing are not included in this build report; release publishing remains deferred.

## APK checksum

SHA-256: `078265580426B6F90A955B07891E4097ADA7629CBBEEB0785D7E7068AEC8DCB1`
