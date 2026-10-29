# Spock and its upstreams

Spock is an independent downstream fork of a fork of **spotui**. It is not a ground-up rewrite, an official Spotify app, or affiliated with Spotify AB.

The application previously used the `com.music.spotui` identifier. Spock 1.7 moves application-owned source to `io.github.unidentified24.spock`. This identity change does not remove or replace authorship of inherited code.

The inherited project credits:

- spotui and its intermediate downstream fork (the precise intermediate repository URL has not been supplied).
- Neptune, the original Jetpack Compose music interface: https://github.com/navneet851/spotify-clone-jetpack-compose
- Meld/Metrolist-derived Spotify metadata and YouTube playback integrations.
- NewPipeExtractor: https://github.com/TeamNewPipe/NewPipeExtractor
- SpotiFLAC-related stream resolving and SimpMusic-related audio processing.

Retain the original GNU GPL version 3 license in LICENSE, source copyright notices, and third-party license notices. Release workflows attach the matching source to each APK release. The precise intermediate fork URL and inherited asset provenance still need to be documented before claiming a complete provenance review.

Spock now uses Android's system fonts. The bundled extracted Spotify font files were removed from the current source. Earlier private commits still contain those assets unless history is cleaned before public release.
