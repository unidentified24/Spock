# Spock 2.3 — offline reliability and polish

App ID: `io.github.unidentified24.spock`. App/interface version: 2.3. Android version code: 23.

## Changes

- Downloads now has saved, pending and failed sections with progress, individual/all cancellation and Retry/Dismiss. Home includes a direct Downloads shortcut. It no longer depends on successfully loading a Spotify collection.
- Ranged downloads validate offsets, total length and individual response lengths. Interrupted responses resume from the bytes actually received. A server returning a full response to a resume request restarts the file, preventing duplicated bytes. Empty responses, HTML/JSON, inconsistent ranges and incomplete declared lengths cannot be recorded as successful downloads.
- Files are checked for a supported audio container before being added to the offline library. When Android can read duration, catalog tracks shorter than 70% are rejected as previews. Unsupported metadata readers do not veto otherwise valid transport/container checks.
- Saved files take priority over network playback and source overrides. Spotify download lookup uses the stable track identity rather than changeable search metadata. Missing/empty files no longer appear downloaded. Existing damaged files from earlier versions may need removal and re-download; the app does not silently delete all old downloads.
- YouTube downloads preserve source-client headers, use identity encoding for byte ranges, and save WebM versus MP4 with the appropriate extension. Download quality caps Deezer quality; compressed downloads avoid unnecessary FLAC attempts. A provider lookup error can fall back to another provider. Cancellation closes request-owned connections, releases the download slot and removes partial files. Failure messages are recorded per track.
- Local imports can be copied into private offline storage through Download, without looking for the song online. The original file stays in its folder. Device scanning, file/folder pickers and file-manager opening remain available. A selected local-only mode is remembered on startup; notification permission is not requested repeatedly after refusal.
- Corrected garbled punctuation in track rows and search. Retained the waveform S logo, bundled Montserrat font, eight themes, Studio/Compact/Classic layouts and five saved player views: Artwork, Record, Sound waves, Spectrum and Orbit. Animated views are decorative and follow play/pause; they do not read the microphone.
- Account has no link back to itself. Settings opens sound controls; Connected music opens provider setup. Collection uses collision-safe identities and handles failed online fetches while preserving local/offline access.
- Retained updater checks for official HTTPS release paths, numeric versions, available SHA-256 digests, package ID, newer build code and matching signing certificate. Manual checks bypass skipped versions; failures display useful errors. Android still confirms installation.

## Verification

- Signed release assembly succeeded. 69 offline regression tests passed: 61 app, 6 streaming, 2 Spotify. Twelve new tests cover download recovery, server response validation, cancellation, stable identity and missing/container files.
- Installed the signed APK on the available Android API 37 emulator. With Wi-Fi and mobile data disabled and no Spotify login, Collection opened without a crash.
- Generated a 60-second WAV test tone, placed it in Android's Downloads directory, granted audio access, scanned it and played it. Android's media session reported PLAYING with buffered audio and no error.
- Used Download on that local track, verified it appeared in the offline library, deleted the original test file, force-stopped/reopened the app and played the private saved copy while networking remained disabled. The player displayed Downloaded and Android reported PLAYING without error.
- Opened Account, Settings and Connected music as distinct destinations; observed no duplicate Account control on these pages. Selected Compact/Clean graphite and exercised Artwork, Record and Sound waves. Final APK reinstallation and startup checks are recorded with the delivery artifacts.

## Limits

The user's exact Spotify collection and signed-in Deezer/YouTube/FLAC services were not available in the emulator. This does not prove every provider/song works or reproduce the user's device-specific Collection crash. File/folder grants, background limits and update installation still need testing on the user's phone. Local release lint/annotation extraction were excluded in this environment; GitHub runs the ordinary debug build and unit tests. The previous 2.2 GitHub check passed.

Downloads currently run in-process: keep Spock open while saving. Interrupted app/process downloads can be retried; background restart persistence is not implemented. Audio/container checks reject common bad responses but cannot prove every downloaded recording is correct. Smooth online playback still depends on the enabled services, account access, recording availability and network.

The repository is public. Automatic distribution of 2.3 requires a stable public GitHub release containing the signed `Spock-2.3.apk`. Live publishing remains deferred as requested; this work updates source and provides the signed local APK without publishing a live release.
