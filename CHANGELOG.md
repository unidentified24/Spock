# Changes

## 2.8

- Return to the original sound-bars logo with theme-aware in-app colors, neutral launcher/splash branding and a monochrome Android icon layer.
- Download complete playlists and keep ordered offline playlist listings, including repeated tracks sharing one audio file.
- Add per-track progress, cancellation, missing-only retry and safe removal of playlist listings without deleting shared songs.
- Wait for complete playlist pagination before enabling the download action; surface incomplete page loads for refresh.
- Register matching metadata for downloads started before a song has ever played. Keep the playback resolver unchanged.

## 2.7

- New image-based Spock wave logo across launcher, splash and app branding.
- Use actual song art and its palette in Record, Waves, Spectrum and Orbit; remove instructional player copy and alternate-stream controls.
- Fit Collection shortcuts and filters on narrow screens; reserve navigation space and support rotation and tablet player panes.
- Import/export validated `.spockdata` preferences, favorites, library and listening-history backups. Exclude credentials and music files.
- Keep foreground native playback alive after dismissing the activity; acquire playback wake locks through Media3.
- Add prominent GitHub Releases links in the README and About screen. Keep live publishing deferred.

## 2.6

- Reproduced popular-track failures with real Android decoding; twelve final samples started in 1.14–3.95s and decoded near their end.
- Unified stream checks/playback on OkHttp; added verified direct-audio client/header handling and skipped metadata-only formats.
- Fixed mixed identifiers in concurrent token callbacks, removed a blocking token wrapper, warmed shared helpers and aligned the signature timestamp with its decoder.
- Native recording-length checks and preparation now run inside the YouTube candidate race; wrong/unplayable candidates cannot cancel good alternatives. Preserve queue ownership during prepared-player handoff.
- Explicit Wi-Fi FLAC confirmation/policy, public mirror pool expansion and full-file validation per competing host. No new working FLAC service is claimed.
- Avoid redundant Deezer-to-Spotify mapping when text metadata is available. Android 8-compatible bounded header reads replace newer InputStream APIs.

## 2.5

- Race confirmed Spotify playback against already-buffered native audio; remove mandatory browser preference delay. Retain the prepared winning player instead of reopening its stream.
- Two concurrent native preparations, bounded Wi-Fi/cellular read-ahead, manual reuse of prepared next audio, shared YouTube search locking and native-first YouTube fallbacks.
- Public SoundCloud extraction, strict recording matching, HLS support, attribution links and progressive-download fallback. No listener API keys or private server.
- Modern Monochrome adapter, extra HiFi candidates, verified-list refresh off the UI thread and per-host outage backoff. Public FLAC availability remains experimental.
- Lazy Spotify browser creation, reduced idle polling, bounded extractor timeouts and fewer animation updates. Millisecond startup diagnostics remain local and exclude URLs/tokens.

## 1.8

- Enabled Deezer, configured FLAC sources, and YouTube now resolve concurrently. The first usable result wins; misses/errors cannot win the race.
- Cancelled lookup connections are disconnected, rather than waiting for slow HTTP reads to time out. Only the winning result can publish the stream URL, source badge, or quality label.
- Changing songs, stopping, or releasing the player cancels pending playback lookups. Local files, downloads, explicit alternatives, and eligible cached results keep their direct playback paths.
- A connected and enabled Deezer account participates even if YouTube is the saved primary choice. Disabled sources and network-quality settings are still respected.
- Playback keeps the first winner. It no longer waits for or swaps to a slower lossless result, so fastest-start playback can be lossy even when FLAC is enabled.
- Added deterministic concurrency/cancellation tests. Real provider startup times still depend on the account, match, network, and source availability.

Live release publishing remains deferred. The release APK uses the existing Spock signing key; application ID stays io.github.unidentified24.spock, version code becomes 18, interface revision remains 1.7.
