# Spock 1.8 validation

Spock uses io.github.unidentified24.spock and UI revision 1.7. App 1.8 uses Android version code 18 and the same stable signing key as the delivered Spock 1.7 release APK. Live release publishing remains deferred.

The Deezer/FLAC/YouTube resolver now starts enabled lookups concurrently, accepts the first usable result, and cancels the others. Cancellation covers resolver-owned HttpURLConnection and OkHttp requests plus unfinished YouTube token initialization. Only the winning result publishes stream metadata; the old quality-upgrade path is retired.

Regression tests cover concurrent startup, misses/errors, first usable winner, lossless not delaying a faster source, deadlines, skipping/stopping, blocking HTTP abortion, request isolation, and OkHttp cancellation. GitHub Actions runs the regular Android build and test suite on main.

Detailed validation results and checksums are supplied alongside the delivered 1.8 APK. Local release compilation uses cached dependencies with release lint/annotation extraction excluded under this chat's restricted environment; the ordinary GitHub build/test job supplies a separate check. No real-provider startup-time benchmark or account-backed playback test was performed in this session. A resolved URL still needs the player's usual connection/buffering step; a failing stream continues to use the existing retry path.
