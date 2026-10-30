# Changes

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
