# Spock

An independent Android music app and downstream fork of a fork of spotui. Spock combines a personal catalog, local files, playback, lyrics, and listening statistics with its own visual identity.

**1.7** uses application ID `io.github.unidentified24.spock` and interface revision `1.7`. It installs separately from the previous spotui-ID APK. Android 8.0 or newer is required.

## What's new

- Four persistent themes: Ember, Aurora, Sandstone, and Deep sea. Find them in Settings → Appearance.
- Editorial discovery cards, a floating navigation dock, independent typography and waveform branding.
- Actual active playback time per song, artist, album and day. Pauses, buffering, seeks and suspension do not add an entire song's duration. Stats refresh live; up to five seconds can be lost on abrupt process termination before the next persistence flush.
- Automatic release detection from this repository, with verified APK download and Android-confirmed installation. App and UI versions have a single source in version.properties.

## Build

Use Java 17 and Android SDK platform 34. Set JAVA_HOME and ANDROID_HOME, then run:

```sh
bash gradlew :app:assembleDebug :app:testDebugUnitTest :innertube:testDebugUnitTest :spotify:test
```

Release builds use a private stable signing key: see [RELEASING.md](RELEASING.md). Private signing files are excluded from the repository.

## Provenance and public release

See [NOTICE.md](NOTICE.md) for inherited components and [LICENSE](LICENSE) for the original GNU GPL version 3 terms. Changing the app ID does not erase the project's upstream history or copyrights. This app is not affiliated with Spotify.

[PUBLIC_RELEASE.md](PUBLIC_RELEASE.md) records the remaining steps before switching the repository to public. Current source cleanup is not a history cleanup or a full security audit. Spotify sign-in and provider playback require account/network configuration and remain separate from offline unit tests.
