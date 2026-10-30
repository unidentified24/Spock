# Spock 2.8 — original identity and playlist downloads

App/interface 2.8; version code 28; package io.github.unidentified24.spock. Work is confined to the Codex working copy; the original Documents/Spock folder is untouched. Live release/tag publishing remains deferred.

## Branding

The original seven sound bars return as compact vector artwork. In-app colors derive from each of the eight existing themes. A neutral graphite/light launcher and splash mark fits every theme, with a monochrome adaptive-icon layer for supported Android themed launchers. The geometry respects Android's 66 dp safe area in a 108 dp viewport. The large generated branding bitmap is removed from runtime resources. The editable SVG and theme preview are included with the delivery.

## Playlist downloads

Playlist pages have a Download playlist action with a bounded three-transfer queue, progress, cancellation and retry through the existing Downloads machinery. Ordered playlist metadata persists in private preferences. Repeated entries share a single saved file, but remain repeated in the offline queue. Offline playback maps each entry to the actual saved track's query, preserving its downloaded-file identity. Downloads lists offline playlists independently of Spotify; the offline screen reads local preferences/files and supports removing the grouping while keeping shared audio files.

The action waits until all playlist pages finish. A partial page fetch is surfaced as an error for refresh instead of silently accepting the first 100 items. Refresh generation guards prevent earlier requests overwriting the newly requested playlist. Download requests register title/artist/duration/edition metadata even when a track has never been played. SongPlayer.kt, stream resolution, race budgets and native buffering are unchanged from 2.7.

Keep Spock open while downloading. Background/process-restart download scheduling is a future feature: completed files and playlist membership persist, while interrupted transfers can be restarted with Download remaining tracks. This update does not claim persistent background download jobs. Offline grouping/audio files are device data and are not included in portable .spockdata backups.

## Verification

Signed release assembly and 113 JVM tests (103 app, 8 streaming, 2 Spotify), plus 13 shipped JavaScript checks. Four new tests cover persisted order/duplicates/matching metadata, missing-only deduplication, saved-file identity, and corrupt/unsupported records.

A separate signed Android instrumentation driver exercised the actual release download engine. A four-entry playlist with three unique local files preserved its order and duplicate after persistence. One intentionally missing source produced a visible failure. Repairing it and retrying downloaded only that missing file, leaving previous files untouched. The originals were deleted; with Wi-Fi and mobile data disabled, the engine decoded the downloaded file and its media URI matched the saved path.

Two real public songs, TheFatRat's Unity and Billie Eilish's bad guy, were downloaded completely. Saved recording lengths were 249301 ms and 194101 ms. Decoding advanced at the start and again after seeking near the ending. This tests actual saved audio, not URL discovery or a preview. No signed-in Spotify/Deezer accounts were available. An authenticated large Spotify playlist and every provider/device combination have not been certified.

The real UI download action was exercised on an unsaved duplicate-track playlist: confirmation started one unique transfer and the listing changed from 0 of 2 to 2 of 2 saved. Final-package UI results, hash/signing and GitHub checks are appended to the delivery report. Test-driver APKs and music files are excluded from the app and source archive.

Local release lint/annotation extraction is excluded under the existing environment restrictions. GitHub runs ordinary Android assembly and JVM checks. The prior 2.6 12-track timing results in PLAYBACK_TESTS.md are historical; no new playback-speed claim is made here.

## References

- [Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
- [Media3 offline downloading](https://developer.android.com/media/media3/exoplayer/downloading-media)
