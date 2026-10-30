# Spock 2.1 reliability changes

Version 2.1, code 21, interface 2.1. Stable Spock application identity and signing key retained.

- Enabled sources now must prepare buffered audio in a muted Media3 player before winning the race. Failed preparation and obvious short previews are rejected while other contenders continue. Winning preparation warms the shared media cache.
- Music and video searches resolve concurrently with recording checks intact. Known-bad playback URLs are excluded from recovery, and IO recovery waits 350ms instead of 3s.
- Empty, premature, and preview-like stream ends do not advance the playlist. Five regression tests cover the completion policy. Normal completion and manual next remain available.
- Next-track preparation now includes short-lived providers close to transition (8 seconds), while ordinary preloading starts within 30 seconds. Expiring URLs and upstream delays can still prevent gapless playback.
- Startup buffer reduced to 750ms, with 1500ms rebuffer threshold and a 15–45 second target buffer. This trades quicker start against increased sensitivity to poor networks.
- Local audio can be discovered through Android MediaStore with user-granted audio permission, without restricting files to Spock's folder. File/folder pickers remain available. Local URIs bypass network chunk inspection.
- Account Settings opens Sound; Connected Music opens Sources. Sources, sound, appearance and about remain separately labelled tabs.
- New native vector S uses parallel coral/apricot music lines from the old identity. No generated bitmap is required for this revision.
- Settings → About → Copy playback report provides up to 40 local diagnostic events without URLs, tokens or account details.

Validation: signed release assembly; offline tests including the completion guard. Final package, signer, hashes and test count are recorded in the delivered report. Device scanning, live provider playback, decoder preparation and audible transitions cannot be verified on a phone in this restricted environment. These changes do not establish a measured success rate or a guarantee of uninterrupted playback. Android provider permissions, unavailable recordings, expired sessions and network failure remain possible.

Implementation references: [Android shared audio access](https://developer.android.com/training/data-storage/shared/media), [Media3 buffer configuration](https://developer.android.com/reference/androidx/media3/exoplayer/DefaultLoadControl.Builder).

Source changes are in the working copy and GitHub; the original Documents/Spock folder is outside the writable workspace. No live release is published.
