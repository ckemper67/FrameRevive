# Maintainer signing and release checklist

## Using your own key for development

The build signs debug and release APKs with this machine's Android debug key (`~/.android/debug.keystore`,
generated per computer) unless you point it at a keystore of your own. `keystore.properties` and every
`*.jks`/`*.keystore` are gitignored; never commit them.

1. Create a keystore somewhere outside the repository:

   ```bash
   keytool -genkeypair -v -keystore ~/framerevive-dev.jks -alias framerevive \
       -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Copy `keystore.properties.example` to `keystore.properties` at the repository root and fill in `storeFile`
   (absolute, or relative to the repository root), `storePassword`, `keyAlias` and `keyPassword`.
3. `./gradlew :app:assembleDebug` and `:app:assembleRelease` now produce APKs signed with that key. Without the
   file, behaviour is unchanged: debug uses the machine key and release stays unsigned.

Check what an APK carries before installing it:

```bash
apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk
```

**Android replaces an installed app only when the new APK has the same certificate and a version code that is
not lower.** So a first install on a frame accepts any key, but every later update — through the webpage
updater or `adb install -r` — must be signed by the same key as the installed build. A different key fails with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, or the updater's "Signing key does not match this installation", and the
only fallback is uninstalling, which removes the album link, controls key, TLS identity, photo cache and
schedule. Builds from the pull-request workflow carry an ephemeral CI key and are deliberately not publishable
as updates.

## Preserve the current signing identity

The published 0.5.24 alpha APK uses the local Android debug signing identity. Its public certificate SHA-256 is:

`E9:A7:C6:2D:57:AD:E1:23:46:89:9B:9C:4E:2E:8B:6C:E6:92:60:66:E9:BF:F8:58:AA:DB:6C:E4:DF:A9:60:6F`

The fingerprint is public information. The matching private keystore must remain private. Current installs and the webpage updater require the matching signing identity. A fresh CI runner's debug key will not match. Do not delete or regenerate the maintainer key, switch keys casually, or tell users to uninstall to work around an update mismatch.

Make an encrypted offline backup of the existing keystore and preserve its alias/password securely, outside the repository, release ZIPs, public cloud links and CI artifacts. Confirm a restored copy has the same certificate fingerprint before depending on it. This checklist does not create a backup or rotate the key.

Before a stable release, prepare a non-debuggable release build with a deliberate signing/migration plan that preserves existing users' update path. Changing the keystore password does not by itself change the certificate; changing the private key does. Do not commit signing passwords in Gradle files.

## Publication

1. Build from the intended reviewed commit. Run the Android build, tests, lint and secret checks.
2. Verify the final distributed APK's package, version and signing certificate against the maintained identity; test an in-place update on the frame.
3. Package only the APK, reviewed installer scripts and public instructions. Never include a keystore, account export, real catalog, photos, TLS private key or private controls link.
4. Publish SHA-256 hashes of the APK and installer ZIP. Where GitHub displays asset digests, compare them with the locally verified files.
5. Keep alpha limitations and installation changes visible in the release notes.

The pull-request workflow deliberately uses no signing secrets and publishes no APK artifacts. Test-build success does not make a CI APK suitable for an existing installation.
