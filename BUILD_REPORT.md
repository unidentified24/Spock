# Spock 2.7 — interface polish and portable backups

App/interface 2.7, version code 27, package `io.github.unidentified24.spock`. The original Documents/Spock folder is untouched. Live release/tag publishing remains deferred.

## Changes

- Generated a new navy/ivory/amber wave-ribbon S image and installed it in the launcher, splash and in-app brand mark.
- Album covers and their extracted colors now anchor all five player styles. Record uses the cover as its spinning label; Orbit uses it at the center; Waves and Spectrum show a cover thumbnail and album-derived colors. Visual motion is decorative, not a measured audio analyzer. Palette work uses a small cached bitmap off the main/audio thread.
- Removed the animated-artwork explanation, track-options explanation, first-launch instruction card and alternate-stream menu/editor from the listening room. Normal playback no longer displays the generic source-status block; errors retain Retry and Sources actions.
- Collection Downloads/On device shortcuts use compact vertical cards. Collection filters and appearance choices can wrap. The app shell reserves actual navigation height instead of drawing over page controls. Bottom navigation stacks icon/label on narrow screens.
- Removed forced portrait orientation, capped very wide page widths, added two-pane player layouts for wide/landscape windows, and sized artwork to leave room for controls on short phone screens. Scrolling remains available for larger text and long track names.
- Settings → Data exports `.spockdata` through Android's file picker. Import reads and validates the entire bounded, versioned JSON document before asking to restore. Portable preference sections are explicitly allowed; account credentials, audio files, downloads, local file permissions and arbitrary preference sections are excluded. Favorites, appearance/sound preferences, cached library/followed artists and listening history are included. Import replaces the portable sections present in the backup and keeps other device/account state. Failed preference commits attempt rollback. Listening-meter cache is reset after restore so old data cannot overwrite restored statistics.
- Added Media3 network wake management and permissions for native background playback. Dismissing the activity/task no longer deliberately pauses active audio. Android audio focus/headset-disconnect behavior is retained.
- Added prominent GitHub Releases links in README and About. This adds navigation without publishing a release.

## Playback preservation

A source comparison verifies `SongPlayer.kt` differs from the tested 2.6 version only by the Media3 wake-mode setting. Source resolution, time budgets, racing, matching, buffering and YouTube/Deezer/FLAC/SoundCloud behavior are unchanged. The 12-track timing results in PLAYBACK_TESTS.md belong to 2.6; they are not reported as a new 2.7 performance run.

## Verification

Signed release assembly and 109 JVM tests (99 app, 8 streaming, 2 Spotify), plus 13 shipped JavaScript checks. Four new backup tests cover typed round trips, credential exclusion, unsupported versions/sections, invalid types/dates and oversized files.

Temporary Android API 37 emulator checks (320 × 568 dp narrow phone and approximately 1067 × 667 dp landscape tablet windows): real `.spockdata` file export/restore recovered a theme and favorite, omitted a credential sentinel and kept the device's existing sentinel unchanged. Real native audio advanced for eight seconds after removing the activity/task. Narrow-phone Collection and tablet/landscape player screenshots were inspected; the short-phone artwork was reduced after the initial visual check found that it pushed controls below the fold. Wide-player artwork also reserves room for the view selector and window insets. Final device results and delivery hashes are appended to the delivered report. A temporary System UI not-responding dialog during display resizing was dismissed with Wait; the app crash buffer remained empty.

These tests simulate multiple window sizes on one emulator. They are not certification across physical phones/tablets or prolonged background tests on every manufacturer's battery policy. Calls and other apps requesting audio focus may still pause audio normally.

## References

- [Android background playback](https://developer.android.com/media/media3/session/background-playback)
- [Media3 ExoPlayer wake management](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java)
