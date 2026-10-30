# Spock

An independent Android music app, rebuilt around a listening room, a personal collection and a listening story. Spock 2.6 uses app ID `io.github.unidentified24.spock`, version code 26 and interface revision 2.6. Android 8.0+.

## Spock 2.6 verified music playback

Real Android tests reproduced the popular-track failures and drove fixes in YouTube transport compatibility, web-helper concurrency, recording matching and candidate arbitration. Twelve popular recordings now start in 1.14–3.95s in the measured test and decode near their end. See [PLAYBACK_TESTS.md](PLAYBACK_TESTS.md) for individual timings and limits.

The player uses OkHttp, matching the stream-check network implementation. A direct-audio client and matching CDN headers restore native compatibility. Metadata-only formats are skipped; the browser fallback warms shared helpers, keeps callbacks local to each request and uses a cancellable token flow. Timestamp and signature decoding use the same fetched player. YouTube candidates must buffer and pass recording-length checks before cancelling competitors. Prepared next-track ownership survives manual transfer.

Include FLAC over Wi-Fi now confirms its saved state and explicitly supports metered Wi-Fi while keeping mobile quality separate. The public FLAC pool includes 10 published community candidates, with complete search-to-full-FLAC verification competing per server and outage backoff. Probes found no newly working public FLAC server; enabling the option cannot make an unavailable upstream work. No personal server is required.

105 JVM tests and 13 shipped-JavaScript checks pass. Live release publication remains deferred.

## Spock 2.5 faster audio starts (previous update)

The first buffered native source or confirmed Spotify playback starts immediately. A slow browser no longer holds ready audio for 5–14 seconds. Spock promotes the player that already buffered the stream, permits two concurrent preparations and cancels competing requests. The next queue item still prepares quietly while listening; manual selection can reuse its prepared audio too.

Public SoundCloud tracks join Deezer, YouTube and FLAC without an account, personal API key or hosted server. Matching checks title, artist and duration and rejects unexpected alternate recordings. SoundCloud HLS streams play through Media3; progressive audio is also available as a download fallback. HLS playlists are never saved as offline audio. The song menu links to the matched SoundCloud page.

FLAC discovery now includes the modern Monochrome protocol, extra public HiFi candidates, a refreshed verified-server list and five-minute outage backoff. These are community services: probes during development found failing/blocked servers, so this is not a claim that more working FLAC servers are currently available. FLAC lookups cannot hold up another buffered provider. Wi-Fi permits more foreground read-ahead with a bounded next-track buffer; cellular has smaller limits. Browser startup is lazy, idle page polling is reduced and player animation updates are capped at 30 fps.

See BUILD_REPORT.md for measured checks and limits. Live release publishing remains deferred.

## Spock 2.4 playback handoff (previous behavior)

Selected Spotify tracks first use your existing Spotify session for browser playback. While listening, Spock prepares the next track from enabled native sources, preferring the best prepared quality. Two seconds before the end it locks the route: buffered native audio, or the warm Spotify browser session. Local files and downloads remain native and offline. The interface and playback device registration use Spock branding. Connected music → Spock instant start controls the hybrid route; crossfade remains in native-only mode.

Browser/account support varies. The signed-in Spotify route and browser-to-native transitions require validation on the user's phone; no claim that every track will play is made. See BUILD_REPORT.md for the startup limits, checks and known constraints. SpotiDuck's streaming approach informed an independent playback bridge; its interface and account connection are not incorporated.

## Spock 2.3 offline reliability

Downloads show progress, cancellation and retryable failures, with a direct Home shortcut. Saved audio wins over remote source overrides and stays linked when Spotify search metadata changes. Missing/empty files no longer appear saved. Transfers validate byte ranges, restart safely when a server ignores resume, and reject incomplete responses and obvious non-audio files. Downloads respect the selected quality and use the correct YouTube client headers. Local imports can be copied into private offline storage through Download. Existing theme, player and navigation choices from 2.2 are retained. See BUILD_REPORT.md for validation evidence and limits.

## Spock 2.2 polish

Collection identity/error-handling fixes; separate Account, Settings and Connected Music routes; file-manager audio opening alongside device scans and import. Five saved player views: Artwork, Record, Sound Waves, Spectrum, Orbit. Eight colour themes and Studio/Compact/Classic layouts, including Original green and Clean graphite. Improved update diagnostics, checksum verification and safe official-asset validation. See BUILD_REPORT.md for tested scope and device-validation limits.

## Spock 2.1 reliability update

Sources must prepare buffered audio before winning. Failed/preview streams do not automatically skip through the queue. Device-wide audio discovery is available from Local music with permission, alongside file/folder picking. Settings opens Sound; Connected Music opens Sources. The S logo now uses parallel music lines. See BUILD_REPORT.md for validation limits and tradeoffs. Settings → About can copy a local playback report for diagnosing failed starts.

## Spock 2.0

- New ribbon-S logo, bundled Montserrat typography, five coordinated themes and rebuilt navigation.
- New library, search, album, playlist, artist, favorites, downloads, local import, lyrics, queue and player layouts.
- A labelled **Account** button at the top right of collection and detail pages. **Account → Settings** exposes YouTube, Deezer, FLAC, sound quality, themes and updates. The Spock name is branding rather than a hidden settings link.
- Account includes a personal listening story with actual minutes, distinct tracks/artists, rankings and daily activity for 7 days, 30 days or all time. Listening totals stay out of the player; seeking shows progress as a percentage.
- Connected/enabled Deezer, FLAC at lossless quality, and YouTube resolve concurrently. Wi-Fi defaults to Lossless for settings that haven't been customized; saved quality choices and mobile data settings are respected. Sources has a direct **Include FLAC over Wi-Fi** action. First usable stream wins and lookup-owned requests stop. Fastest can mean compressed audio, even with Lossless selected. No later quality swap.
- YouTube ranks the first search page before broadening, caches recording IDs separately from expiring URLs, tries up to two verified candidates at once, and skips web-player timestamp work for native clients. Strong title/artist/duration checks and alternate-recording rejection remain.
- A failed lookup stays on the selected track with Retry and Sources. It no longer cycles through the playlist automatically.
- Player redraws no longer start image palette extraction. Unused Canvas video, duplicate global-feed requests and disabled hidden Spotify-player startup are removed from the normal UI flow.

## Build and install

Use Java 17 and Android SDK platform 34. Set JAVA_HOME and ANDROID_HOME, then:

```sh
bash gradlew :app:assembleDebug :app:testDebugUnitTest :innertube:testDebugUnitTest :spotify:test
```

Release builds use a private stable signing key; see [RELEASING.md](RELEASING.md). Never commit signing files or account credentials. A release signed with the existing Spock key updates 1.7/1.8 without changing the application ID. The legacy spotui-ID install is separate.

## Sources and updates

Spotify supplies the account-backed catalog/library connection. Playback services require working endpoints and appropriate account access. Local files and existing downloads play directly. FLAC community services remain experimental. Sources, accounts and network conditions affect availability; no provider is guaranteed fastest or guaranteed to contain every recording.

The signed-release updater checks this repository, verifies downloaded packages and asks Android to install. Live release publication remains deferred; building an APK does not publish a release or make the repository public.

## Credits

This is an independent downstream fork of a fork of spotui. The UI rebuild does not erase the inherited playback code or its authorship. [NOTICE.md](NOTICE.md) retains upstream credits and [LICENSE](LICENSE) retains GPL v3. Montserrat's OFL notice is bundled in the source and app. [RESEARCH.md](RESEARCH.md) records the performance research and its limits. [PUBLIC_RELEASE.md](PUBLIC_RELEASE.md) lists remaining public-release checks.

The build report separates automated/emulator checks from live signed-in provider tests. Compilation and public-source probes do not prove reliable playback for every listener or recording.
