# Spock 2.4 — Spotify-first playback and prepared native handoff

Package: `io.github.unidentified24.spock`. App/interface version: 2.4. Version code: 24.

## Behavior

- A selected Spotify catalog track first uses Spotify's own protected web playback, reusing Spock's existing login cookie. Local files and saved downloads retain immediate native/offline playback.
- Spock retains its interface, account connection, navigation and queue. The browser is a playback engine only. No SpotiDuck login UI, interface, styles, icons, ad filters or full wrapper scripts are included. The independently written bridge uses desktop browser identity, scoped early initialization and the existing authenticated page. Protected audio remains handled by Spotify and Android.
- The player's normal status uses Spock branding rather than provider/codec names. Device names in supported Spotify browser-registration fields are changed to Spock. Spotify's eventual device label is server-controlled and still needs live-account verification. Internal source/quality metadata is preserved for routing; changing a label does not change the audio codec.
- While a track plays, enabled YouTube, Deezer and lossless providers resolve the next queue item concurrently. Candidates must prepare buffered native audio and pass duration checks. A separate paused, muted player retains the best prepared candidate. FLAC is preferred, with higher advertised bit depth breaking ties; MP3 320 and Opus follow. Equal-ranked candidates retain the first result. This is a selection policy, not a claim about subjective audio quality or the universally fastest server.
- At two seconds remaining, Spock locks the next track's route. If native audio is not ready, the existing Spotify browser session is retained for the next song. Late candidates cannot override that decision. The next song is not started two seconds early, and the current song is not interrupted for a quality upgrade.
- At the boundary, a prepared native player is promoted directly. Browser playback is stopped and acknowledged first. A browser that cannot acknowledge stopping is destroyed before native playback starts. Native preparation never plays audibly. Stale play attempts, cancelled provider requests and obsolete queue preparations are rejected.
- Track selections use generation checks, not just song-title equality. Queue changes and seeking invalidate preparation. Pause, repeat, manual next/previous and automatic queue advancement remain separate. Natural completion is detected outside the UI. Failures stop on the selected track with recovery controls rather than cycling through the queue.
- Browser startup is bounded (5 seconds for an initialized compatible page; up to 14 seconds for cold startup). Native recovery resolves concurrently during that wait. Browser playback must show advancing time for the selected track; a Pause button or DRM-capability response alone is insufficient. A confirmed browser stream with ten seconds of playing/buffering without progress gets one native recovery attempt at its saved position.
- The browser stays warm between requests rather than reloading for every track when its command path is available. Unsupported document-start scripting, unavailable protected playback, missing login, incompatible accounts and failed browser starts fall back to enabled native sources.
- Preparation is bounded to one transient native preparation at a time, without taking audio focus. Queued short-lived sources are refreshed near the boundary when necessary. Lossless participation follows the selected network quality; Deezer resolution is capped by the same setting and account entitlement.
- Connected music exposes Spock instant start, enabled by default. Turning it off retains the native-only playback path. Hybrid mode uses song-boundary handoffs; the existing crossfade path is used in native-only mode. Turning next-track preparation off prevents background native preparation.

## Verification

- Release assembly and package signing are checked for this delivery; the generated delivery report adds the exact APK hash and test results.
- 91 JVM unit tests: 83 app, 6 streaming, 2 Spotify. New tests exercise deadline locking, late results, reordered queues, same-track reselection, first/stale browser positions, pause near the end, natural completion and background quality selection/cancellation.
- Eleven Node checks execute the actual shipped JavaScript in an isolated browser fixture. They cover origin scoping, desktop identity, inactive audio blocking, Spock registration naming, unrelated requests, playback state, stop acknowledgement, stale commands, resume generations, song completion and ignoring decorative-video completion. CI runs these alongside Android build/unit checks.
- The signed Android build was exercised in a temporary Android API 37 emulator. Generated 20-second local tracks A and B were placed outside Spock's Music folder. With Wi-Fi and mobile data disabled, A played, automatic playback advanced to B, and the queue stopped at its end without an app crash. Final-build verification is recorded separately with the delivery artifacts.

## Validation limits

The emulator has no signed-in Spotify, Deezer or YouTube account. Actual Spotify playback, Connect branding, account restrictions, browser-to-native transitions, screen-off protected audio and a long multi-track listening session remain unverified on the user's phone. Browser fixture tests validate our bridge logic, not Spotify's private server protocol. Spotify page structure or protocol changes can require maintenance.

The two-second point is a routing decision, not a guarantee that Spotify has already buffered the next recording. The browser keeps its session warm but does not play the next track underneath the current song. DRM, server and network startup can still create a gap. If every enabled provider is unavailable, playback cannot be guaranteed.

Local release lint/annotation extraction are excluded in this build environment. GitHub performs the ordinary debug assembly, JVM tests and JavaScript checks. No live release is published: automatic APK distribution remains deferred as previously requested.

The offline/download safeguards, local discovery, Collection fixes, themes and five player presentations from 2.3 are retained. Signing keys, passwords and private backups remain excluded from source archives and GitHub.

## Technical references

- Android WebView document-start scripting: https://developer.android.com/reference/androidx/webkit/WebViewCompat
- Android Media3 preparation/preloading: https://developer.android.com/media/media3/exoplayer/preloading-media/preloadmanager
- Reference project behavior/credits: https://github.com/23fpsz/SpotiDuck-Releases
