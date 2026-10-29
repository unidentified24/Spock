# Public release preparation

The repository's visibility has not been changed.

Implemented: separate Spock app identity, Spock-owned release endpoint, stable local release key, no debug-signing fallback for release, current-tree removal of extracted Spotify fonts, GPL and fork notices, source attachments in release automation, ignored signing files, disabled Android cloud backup of account preferences, and build/tests on pull requests.

Before making this repository public:

1. Keep a private backup, then resolve the old-history font assets: approve a clean source history or remove those paths throughout history using a reviewed history-cleanup process. Deleting the current files alone does not erase older commits.
2. Add the missing intermediate fork URL and review inherited artwork/asset licensing. Keep upstream authorship and GPL notices.
3. Add the three repository Actions secrets documented in RELEASING.md. Never commit the signing backup, keystore properties, account cookies or tokens. A targeted current-source credential-pattern check is not a complete security audit or a certification of third-party service compliance.
4. Run the signed release workflow and verify a real 1.7-to-1.8 update with the same key on a device before describing over-the-air updates as verified.

The inherited YouTube PoToken integration contains an upstream Google client API key. Confirm its provenance and allowed use before public distribution; do not assume this is a credential created for Spock.

The release updater uses unauthenticated public GitHub release assets. It intentionally contains no personal GitHub token, so the endpoint will not work publicly while the repository is private or until a release has been published.
