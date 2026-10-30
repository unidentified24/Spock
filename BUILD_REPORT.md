# Spock 2.0 build and implementation report

App version 2.0, version code 20, UI revision 2.0. Application ID and private release signer remain the established Spock identity.

## Interface

Rebuilt discovery, collection, search, albums, playlists, artists, favorites, downloads, local import, lyrics, queue, settings and the full/mini player. New generated ribbon-S branding and Montserrat variable typography (explicit weight axes, OFL notice bundled). Five persistent themes. Account is labelled and available at the top right, including playlist/detail screens. Settings opens on Sources; sound quality, appearance and updates have separate tabs. Local listening rankings, minutes and daily activity appear in Account and its listening story. The player has progress seeking without elapsed/duration time labels or listening-total counters.

The provider-owned sign-in/verification pages and inherited audio/metadata integrations remain. This is a presentation rebuild on an inherited playback base, not a claim of newly authored upstream integrations. GPL and upstream notices are retained.

## Playback and performance

- Enabled Deezer, FLAC at lossless quality and YouTube race for the first usable stream; losing owned transports close. Local/download alternatives retain their direct path.
- YouTube ranks one initial search page before broadening on a miss, caches recording IDs separately from transport URLs and resolves at most two verified candidates concurrently. A missing explicit badge is not treated as confirmed clean metadata. Title, artist, duration and alternate-recording checks remain.
- Native clients do not fetch the web signature timestamp. Authenticated web fallback retains its original session and token flow. Endpoint requests have individual deadlines, validation must succeed, and transport cache entries expire. Short-lived Deezer/FLAC URLs resolve at play time.
- Failed resolution leaves the current selection with Retry/Sources actions; automatic queue-skipping on failures is removed.
- Palette extraction on player redraw, unused Canvas video and duplicate player/global-feed fetches are removed. The disabled hidden Spotify audio browser no longer loads during normal startup/sign-in. Only the next recording match is warmed, and the preference is respected.

Fastest-start mode can choose compressed audio while a slower FLAC result is still pending. It does not later upgrade the current stream. Unlabeled YouTube editions remain uncertain; identity/duration checks reduce mismatches without proving explicit/clean identity.

## Validation and limits

Local signed release assembly and 42 tests (34 app, 6 streaming, 2 Spotify). APK signature and package/version metadata are verified by the delivery script, and the signer is compared with the delivered 1.8 APK. New policy tests cover missing explicit metadata, named clean/radio edits, duration/title evidence and known explicit/clean choices. Extractor fallback calls use the same request-local cancellation ownership as other transports, with an isolation regression test. Existing tests cover provider concurrency, first valid response, blocking-transport cleanup, cancellation, timeout, update versioning and listening clocks.

Local release lint/annotation extraction are excluded because the environment blocks the worker/network setup. The normal GitHub Android check workflow runs on the published source; see the final delivery report for its result.

No Android device/emulator installation, frame-time capture, visual device test or authenticated provider playback benchmark was possible here. The Android bridge could not open its local service in this restricted environment. Compilation and offline tests do not guarantee that all recordings are available or demonstrate a measured startup improvement.

Source and APK delivery are in the chat outputs folder and the working project copy. The original Documents/Spock folder is outside this chat's writable paths. Live release publication and repository visibility changes remain deferred.

See RESEARCH.md for the primary sources consulted, NOTICE.md for credits, and assets/brand/README.md for the built-in image generation prompt and saved logo location.
