# Spock

An independent Android music app, rebuilt around a listening room, a personal collection and a listening story. Spock 2.2 uses app ID `io.github.unidentified24.spock`, version code 22 and interface revision 2.2. Android 8.0+.

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

No live authenticated provider timing, Android device installation or visual device test has been performed in the restricted build environment. Unit tests and compilation do not prove those flows.
