# Spock 2.6 real playback checks

The final signed release engine was exercised in a temporary Android API 37 emulator using public source access without signed-in Spotify, YouTube or Deezer accounts. A separate signed instrumentation driver supplied catalog-like title/artist/duration metadata and selected tracks through SongPlayer. It is not bundled into the product APK. No personal server or listener API key was used.

Success required actual player output with advancing decoded position, a matching recording length, then a seek to twelve seconds before the end followed by advancing decoded audio. Receiving a URL or playing a short preview was insufficient. These checks sample the start and end; they do not represent continuous listening to each entire song or a long phone listening session.

All twelve passed below five seconds. Range: 1.14–3.95 seconds; median: 1.45 seconds. All winners in this run were YouTube. This is a measured result on this connection/device, not an availability guarantee for every recording, network or account.

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

The 2.5 baseline failed Blinding Lights, Shape of You, Levitating and bad guy; Unity played through SoundCloud in about 2.2 s. Intermediate tests caught both inaccessible-prefix streams and a wrong-length Blinding Lights selection. The final build retains the full-stream range guard, checks recording length and validates native preparation inside the YouTube candidate race.

A prepared remote next-track selection survived transfer and cancellation of pending source work, with advancing playback checked 762 ms after the switch. Existing local/offline checks and unit checks are recorded in BUILD_REPORT.md. Live Deezer/Spotify account playback remains unverified because no signed-in accounts are available in the test emulator.

The Include FLAC over Wi-Fi action now saves the request/quality/source in one write and shows confirmation. Unit tests cover explicit metered-Wi-Fi participation and keeping mobile data separate. The attempted metered emulator runtime check was inconclusive: that network lost validation/availability to the instrumented application. It is not counted as a passed network-level test. The setting's persisted values and UI are checked separately.
