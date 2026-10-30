# Spock 2.6 — real-track playback and candidate validation

Package io.github.unidentified24.spock; app/interface 2.6; version code 26. The original Documents/Spock project is not modified for this update. Live release publishing remains deferred.

## Actual music verification

| Recording | Start | Ending audio decoded |
|---|---:|---|
| Blinding Lights The Weeknd | 3.95 s | Yes |
| Shape of You Ed Sheeran | 1.61 s | Yes |
| Levitating Dua Lipa | 1.52 s | Yes |
| bad guy Billie Eilish | 1.45 s | Yes |
| Unity TheFatRat | 1.14 s | Yes |
| Flowers Miley Cyrus | 1.34 s | Yes |
| As It Was Harry Styles | 1.30 s | Yes |
| Believer Imagine Dragons | 1.38 s | Yes |
| Dance Monkey Tones and I | 1.45 s | Yes |
| Espresso Sabrina Carpenter | 1.25 s | Yes |
| Happier Than Ever Billie Eilish | 1.63 s | Yes |
| Bohemian Rhapsody Queen | 1.60 s | Yes |

All 12 final samples produced advancing decoded audio within five seconds (median 1.45 s, maximum 3.95 s) and decoded again after seeking near the end. A signed, separate instrumentation driver called the release playback engine in a temporary Android API 37 emulator; it supplied title/artist/duration metadata as the catalog normally would. No signed-in provider accounts were available. These are start/end samples, not continuous full-song listening or a guarantee about all recordings/phones. PLAYBACK_TESTS.md and the delivery CSV/JSON contain the method and individual observations.

## Corrected failures

The 2.5 baseline failed four popular recordings. Newer iOS YouTube responses contained format metadata without normal audio URLs. A live-supported direct response now participates with matching client identity, while the token-enabled browser-shaped fallback is prioritized and warmed. Metadata-only formats are not mistaken for transport URLs. Media3 uses OkHttp to align networking with validation. Prefix GET success alone was found insufficient: some streams reject later ranges. The full-stream/end-range guard remains enabled.

The shared token bridge assigned asynchronous identifiers to globals; overlapping requests could deliver a token to the wrong continuation. Request-local JavaScript bindings fix this. A Node test executes the shipped snippet with out-of-order success/failure callbacks and reproduces the old failure. The public token function is now suspending instead of wrapping another coroutine in blocking runBlocking, so cancellation can meet the foreground budget. Shared helper initialization begins after the initial UI frame and is bounded. The signature timestamp and primary decoder use the same fetched player.js.

Intermediate Android testing caught a 237 s Blinding Lights selection against a 200 s expected recording. Both ranking and native preparation now enforce a duration tolerance of max(3s,3%). Native preparation runs within the YouTube candidate competition, so a wrong-length/undecodable early URL cannot cancel the other candidates. The winning buffered player is retained. Players handed to the queue cannot subsequently be released by cancelled lookup cleanup; a remote prepared-track transfer was verified with advancing playback 762 ms after selection.

Foreground source competition has a 4.5 s budget, leaving time for promotion/stop acknowledgement. Network/provider conditions can still produce a timeout or no accessible full stream. The faster route does not replace a full song with a preview or an obvious alternate recording. SoundCloud caches multiple candidates, prioritizes artist uploads, rejects unexpected alternate/featured editions and can try another match after preparation failure.

## FLAC and source settings

Include FLAC over Wi-Fi now atomically saves the source, lossless quality and explicit Wi-Fi request, with a visible checkmark/description. The policy supports an explicit request on metered Wi-Fi and leaves cellular separate. Unit coverage verifies those cases. A runtime metered-emulator check was inconclusive because the network became unvalidated/unavailable to the instrumented app; it is not represented as a passed device-network test.

Ten published community HiFi candidates are available in the built-in pool alongside modern Monochrome and the refreshed verified registry. Each public endpoint competes through search, match, track request and a 42-byte full-FLAC header check. A quick search on a server with broken streaming cannot win. Failed hosts back off, and only verified full-FLAC requests reset health. Android 8-compatible bounded reads handle partial reads without depending on InputStream.readNBytes from newer Android releases.

Live added-mirror probes encountered DNS failures, TLS verification errors, connection resets and stream 403s; no newly working public FLAC source was verified. TLS checking remains enabled. Enabling FLAC allows participation when servers work; it does not imply the servers are available. Deezer and YouTube race independently of the old primary-source preference; Deezer avoids an unnecessary Spotify ISRC request when catalog text is already available. Authenticated Deezer/Spotify playback still needs real-account phone validation.

## Checks

Release assembly succeeded. 105 JVM tests passed (95 app, 8 streaming, 2 Spotify). Eleven shipped browser checks plus two shipped token-bridge concurrency/error checks passed. Local release lint/annotation extraction remains excluded in this environment; GitHub performs ordinary Android assembly/unit checks. The delivery report appends the package hash, signing verification and final smoke checks. Signing credentials, test-driver APKs and private backups are excluded from source.

## Final device smoke checks

On the final 2.6 release, local WAV files selected outside Spock’s folder played with Wi-Fi and cellular disabled. The local queue advanced from the first track to the second. Saving a local track through Download changed the menu to “Downloaded — remove”; selecting it from Offline produced playing audio with the full 20-second file buffered. The crash buffer stayed empty. The connected-music button visibly changed to “FLAC included over Wi-Fi ✓” after activation.

## References

- https://developer.android.com/reference/androidx/media3/datasource/okhttp/OkHttpDataSource.Factory
- https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide
- https://github.com/MetrolistGroup/Metrolist/blob/main/app/src/main/kotlin/com/metrolist/music/utils/potoken/PoTokenGenerator.kt
- https://github.com/monochrome-music/monochrome/blob/main/INSTANCES.md
