# Spock 2.5 — first-ready playback and public SoundCloud

Package `io.github.unidentified24.spock`; app/interface 2.5, version code 25. Stable release signing identity retained. Live release publishing remains deferred.

## Playback changes

The old 5–14 second browser preference held back buffered native audio. Both engines now compete: native must buffer and pass duration checks; Spotify must confirm advancing playback of the requested track. A cancelled losing browser attempt is stopped/acknowledged before native audio starts. Two completed browser failures temporarily back off further browser attempts. Local/downloaded files bypass the browser.

Remote lookup previously prepared a probe, released it and prepared another player. The buffered winner is now retained and promoted. Two concurrent native preparations replace one serialized preparation; obsolete players are released on cancellation. Track generations, connection ownership, stop-on-failure, recording matching and preview rejection are retained. Next-track quality preparation is still limited to one upcoming queue item and manual selection can reuse it. Existing queue timing, metadata, audio focus and restored position are preserved.

Wi-Fi buffering: foreground 20–60 seconds with a 16 MiB target, next candidate capped at 8 seconds/2 MiB. Cellular: 10–25 seconds, 4 MiB target, next candidate 3 seconds/512 KiB. Buffer limits are targets; one in-flight allocation/segment may overshoot. At most two candidates prepare concurrently. YouTube range chunks are 4 MiB on Wi-Fi / 2 MiB cellular. Startup requires 350/500 ms; rebuffer thresholds are 1.2/1.5 seconds. This trades modest read-ahead for fewer stalls, not unlimited full-track prefetch.

## Sources

SoundCloud uses the bundled NewPipe extractor's public track search and stream handling. No private server, personal API key or new account is required. Title, artist and duration must match; unexpected covers/remixes/other editions are rejected. Cached matches avoid repeat search. HLS and progressive audio are supported; only progressive audio is offered to the existing validated offline download fallback. HLS manifests are never stored as songs. Song options link to the matched public page.

YouTube native client fallbacks now precede web-token work so token generation cannot consume the fallback budget first. Concurrent matching callers share a bounded search lock and recheck the cache. Extractor sockets/calls have deadlines and remain associated with their cancelling lookup.

FLAC adds an independent modern Monochrome direct-file adapter, two additional public HiFi candidates and automatic retrieval of a verified playable-source list. Legacy registry refresh runs in the background, never synchronously on the UI/first resolve path. Hosts failing requests back off for five minutes, and small endpoint batches compete. Search success alone is not a playback success: the winning native player must still prepare. Bit depth is not invented for the modern direct-file service.

Live probes found several old FLAC hosts no longer resolving, one returning 401, and candidate servers with working search but rejected streams. The verified public registry currently reported zero playable sources. Adding dead mirrors cannot improve reliability; the new handling skips recent outages and allows other buffered providers to win. No newly working FLAC server is claimed.

## Responsiveness

The hidden browser is created only when needed, after a brief opportunity for cached/native audio to win. Idle browser evaluation is suppressed once capability is known. Stop acknowledgement is bounded at 350 ms and destroys an unresponsive browser before native playback. Static artwork no longer runs animation polling; visualizer updates are capped at 30 fps. Diagnostics record selection, buffering and first-audio timing with millisecond precision, without account data, URLs or tokens.

## Verification and limits

A live public SoundCloud probe using the bundled extractor found TheFatRat's Unity upload, resolved HLS and retrieved an audio/mp4 segment (HTTP 206). Cold search to media segment took about 3.5 seconds on the development connection. This checks a real stream, not every catalog recording or Android decoder behavior. Timing varies by network/provider.

The delivery report records final build, unit/JavaScript checks and emulator results. Regression tests cover immediate native victory over a 14-second browser attempt, loser cancellation, outage recovery, buffering bounds and SoundCloud recording matching, alongside existing playback/offline tests.

No signed-in Spotify/Deezer account is available in the test emulator. Authenticated Spotify starts, browser-to-native handoff, device branding and a long phone listening session still require real-account/device validation. No promise that every song or community endpoint works is made. Existing local/offline safeguards, themes and player presentations are retained.

Local release lint/annotation extraction are excluded under this build environment's restrictions; GitHub runs ordinary debug assembly and unit checks. Signing files and private backups are excluded from source and GitHub.

## Final local verification

Signed release assembly succeeded. 98 JVM tests passed (90 app, 6 streaming, 2 Spotify); 11 checks execute the shipped browser bridge JavaScript. A new test invokes Media3's concrete track-selection/startup callbacks and verifies the preparation cap opens after promotion. This was added after the emulator exposed recursive default-interface callbacks in an intermediate buffering implementation; that intermediate APK was not delivered.

The final signed APK was installed in a temporary Android API 37 emulator. With Wi-Fi/mobile data disabled, generated WAV tracks in the shared Download folder were discovered and played; the queue advanced automatically from A to B with B fully buffered. A was then saved through the Download action, appeared in Offline and played after app restart with its original shared-folder file removed. The crash buffer remained empty after these checks. Collection/local/download navigation opened successfully. These generated-file checks do not verify authenticated remote downloads or every media format.

Additional live FLAC probes: both new HiFi candidates returned successful search responses followed by HTTP 403 for the track request; the modern Monochrome search timed out. The adapter and outage handling are implemented, but no new playable FLAC source was verified. SoundCloud's public extractor/media-segment probe succeeded as described above; signed-in remote playback on the user's phone remains unverified. No changes were copied back into Documents/Spock for this delivery, per the user's updated preference.

## References

- https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/soundcloud
- https://github.com/kipavy/spotube-plugin-lossless-sources
- https://developer.android.com/reference/androidx/media3/exoplayer/DefaultLoadControl.Builder
- https://developer.android.com/develop/ui/compose/performance/phases
