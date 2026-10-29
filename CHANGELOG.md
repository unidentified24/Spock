# Changes

## 1.8

- Enabled Deezer, configured FLAC sources, and YouTube now resolve concurrently. The first usable result wins; misses/errors cannot win the race.
- Cancelled lookup connections are disconnected, rather than waiting for slow HTTP reads to time out. Only the winning result can publish the stream URL, source badge, or quality label.
- Changing songs, stopping, or releasing the player cancels pending playback lookups. Local files, downloads, explicit alternatives, and eligible cached results keep their direct playback paths.
- A connected and enabled Deezer account participates even if YouTube is the saved primary choice. Disabled sources and network-quality settings are still respected.
- Playback keeps the first winner. It no longer waits for or swaps to a slower lossless result, so fastest-start playback can be lossy even when FLAC is enabled.
- Added deterministic concurrency/cancellation tests. Real provider startup times still depend on the account, match, network, and source availability.

Live release publishing remains deferred. The release APK uses the existing Spock signing key; application ID stays io.github.unidentified24.spock, version code becomes 18, interface revision remains 1.7.
