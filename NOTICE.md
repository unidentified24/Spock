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

Spock 2.0 uses the bundled Montserrat variable family under the SIL Open Font License; see assets/licenses/Montserrat-OFL.txt (also bundled in APK assets). The bundled extracted Spotify font files were removed from the current source. The earlier development history is stored only in a separate private backup; main now begins with a clean source snapshot. GitHub may retain previously uploaded unreachable objects and cached commit URLs; removal from branch history does not guarantee server-side purging.

The Spock 2.0 interface and ribbon-S logo replace the inherited presentation. The logo was generated for this project with OpenAI image generation; the prompt is saved in assets/brand/README.md. The Spotify, YouTube, Deezer and FLAC integrations remain derived work and keep their upstream notices.
