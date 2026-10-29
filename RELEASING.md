# Spock releases and updates

Edit version.properties for each release: VERSION_NAME is the app version, VERSION_CODE is a strictly increasing Android integer, UI_VERSION is the interface revision. Version names compare numerically across 1.7, 1.8, 1.10, 2.0 and later; versions are not a predefined list. Android's build code has platform limits, so literal infinite installable versions are not possible.

The app displays its installed version and interface revision in Settings. It automatically checks Spock's latest stable GitHub release at launch. Tags must be v1.7, v1.8 and so on; the APK asset must be named Spock-1.7.apk, Spock-1.8.apk, etc. Drafts, prereleases and mismatched asset names are ignored.

Download and install downloads the APK, checks the app ID, strictly newer build code and matching signing certificate, then opens Android's installer. Android requires the user's installation confirmation. When prompted, allow Spock to install updates and tap the update action again. There is no silent installer or embedded private-repository access token.

## Stable signing setup

The locally generated Spock release signing backup is delivered separately and is not in Git. Protect and retain it: losing it prevents updates to existing installs. The alias is spock; the same generated password is used for the key and keystore.

Add these Actions secrets under the Spock repository's Settings → Secrets and variables → Actions:

- SPOCK_KEYSTORE_BASE64: base64 of the delivered spock-release.jks.
- SPOCK_STORE_PASSWORD: the delivered signing password.
- SPOCK_KEY_PASSWORD: the same signing password.

Once configured, create/push the version tag or run Signed Spock release. It tests, signs, and publishes the versioned APK, release.json with app/UI versions and checksum, and matching source archive. Missing signing secrets stop the workflow instead of publishing an unsigned or randomly signed APK.

For local release builds, create ignored keystore.properties with storeFile, storePassword, keyAlias and keyPassword, then run gradlew.bat :app:assembleRelease. Copy app/build/outputs/apk/release/app-release.apk to Apk/Spock-VERSION.apk.

The new application ID installs alongside the old spotui-ID build. Its account/session data is separate. Subsequent Spock releases must retain io.github.unidentified24.spock and the same release key.
