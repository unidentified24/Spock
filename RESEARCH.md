# Playback and UI performance research — 2026-10-29

No public documentation establishes a universal fastest service for every listener. The choice depends on network, catalog availability, tokens and account access. Racing the configured services is an implementation decision; no measured real-provider latency claim is made.

Android recommends keeping costly work out of composable bodies and remembering reusable calculations. The inherited player started palette extraction on redraw and created duplicate feed fetches; the new UI removes those paths and isolates its progress updates. [Android Compose performance guidance](https://developer.android.com/develop/ui/compose/performance/bestpractices).

Media3 documents preloading nearby content to reduce transition delay. Spock's existing Media3 integration remains; the new matching warm-up prepares only the next recording ID, and short-lived Deezer/FLAC URLs are resolved at play time. This adapts the principle to the existing source contracts, rather than introducing a new player dependency. [Media3 preloading documentation](https://developer.android.com/media/media3/exoplayer/preloading-media/preloadmanager).

The yt-dlp project documents client-specific PO-token requirements, including possible 403 failures. This is evidence that a returned URL alone does not prove that playback will work, rather than a guarantee that any native client remains supported. Spock retains the existing client-bound authentication and token code, defers unnecessary web setup and validates public stream candidates. [yt-dlp PO Token Guide](https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide).

The Spotify-to-YouTube matching implementations in spotDL and the current Metrolist source were consulted as background. A wholesale extractor migration was not made: it would add unverified dependencies to a working build. Existing recording checks remain, while an absent explicit badge is no longer treated as confirmed clean metadata. [spotDL source](https://github.com/spotDL/spotify-downloader), [Metrolist source](https://github.com/MetrolistGroup/Metrolist/blob/main/app/src/main/kotlin/com/metrolist/music/utils/InnerTubeXPlayer.kt).

The Google Fonts distribution identifies Montserrat as OFL-licensed. A copy is included in source and APK assets. [Montserrat OFL](https://github.com/google/fonts/blob/main/ofl/montserrat/OFL.txt).

Limits: tests exercise orchestration, cancellation, edition handling and the existing data logic offline. No provider-wide benchmark, account entitlement test, device frame-time trace or live audio playback measurement was possible here. Unlabeled recordings can have uncertain explicit/clean metadata; recording and duration checks reduce mismatches but do not prove edition identity.
