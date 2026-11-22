# Updating the frame over the network (silent installs)

A frame on the wall should not need a cable to receive a new build. Uploading over HTTPS always worked; the
on-frame confirmation was the step that needed a pointer. This records how that was removed, what was tried
first and why it failed, so the next person does not repeat it.

## The flow as it stands

1. Open the controls link (see [HTTPS pairing](security-pairing.md) if a browser still warns).
2. **Debug & updates** → choose the APK → **Upload & verify APK**. The frame checks the package name, that the
   version code is not lower, and that the signing certificate matches its own install, then stages the file.
   Limit: 40 MiB.
3. **Install on frame**. Android raises its normal installation prompt.
4. `FrameInstallClicker`, an accessibility service inside the app, reads that prompt and clicks **INSTALL**.

Verified on the frame: 0.5.25 build 44 went from upload to installed in about three seconds with nothing
touching the panel, and the same worked again after a full reboot.

## Uploading from a script (macOS)

The page's own JavaScript does two POSTs; a script can do the same. Take the controls link from the frame
(it carries the key, so keep it out of shared logs and chats).

1. Fetch the certificate the frame presents and compare its SHA-256 fingerprint with the one on the frame (or
   the copy already trusted in your keychain, `security find-certificate -a -c framerevive -Z`). Send the key
   only after they match.
   `echo | openssl s_client -connect <host>:8765 2>/dev/null | sed -n '/BEGIN CERT/,/END CERT/p' > frame.pem`
   then `openssl x509 -in frame.pem -noout -fingerprint -sha256`.
2. Upload: `curl --cacert frame.pem -X POST -H 'Content-Type: application/vnd.android.package-archive'
   --data-binary @app-debug.apk '<controls link>&action=uploadApk'`. The reply says "verified" or why not.
3. Install: `curl --cacert frame.pem -X POST -d action=installApk '<controls link>'`, then read the Debug page.

A sandboxed shell may fail to resolve the `.local` name (`DNSServiceGetAddrInfo failed -65563`); that is the
sandbox blocking mDNS, not the frame. The version shown on the page comes from `FrameWeb.VERSION`, which must
be kept in step with `versionName`.

## What Android 10 allows, and what it does not

- A normal app cannot install silently. `PackageInstaller` requires a user action unless the caller is
  privileged, a device owner, or the installer of record.
- **Device owner looked like the answer and is not, here.** The frame had no accounts, so
  `settings put global device_provisioned 0` followed by `dpm set-device-owner
  com.framerevive.app/.FrameAdminReceiver` succeeded. But the silent path needs
  `PackageInstaller.SessionParams.setRequireUserAction(boolean)`, a non-SDK interface. With a modern
  `targetSdk` the call is blocked, the app reports "This Android version will not allow a silent install", and
  it must fall back to the prompt. Device owner was therefore released again.
- Releasing the owner **also clears the device admin**, which the frame needs for real screen-off sleep.
  Restore it with `adb shell dpm set-active-admin com.framerevive.app/.FrameAdminReceiver`.

## The mechanism that works: an accessibility service

`app/src/main/java/com/framerevive/app/FrameInstallClicker.kt`, declared in the manifest with
`android.permission.BIND_ACCESSIBILITY_SERVICE` and configured by `res/xml/frame_install_clicker.xml`.

- The XML sets `android:packageNames="com.android.packageinstaller"`, so the service only receives the system
  installer's windows.
- On each event it reads the window's text and returns unless that text mentions **FrameRevive**, so it cannot
  confirm an installation of anything else.
- It then finds a node labelled `INSTALL`, `UPDATE` or `CONTINUE`, checks it is enabled, and clicks it.
- It records the action in `updateResult`, which the Debug page shows as "Android's install prompt was
  confirmed automatically."

`android:description` in that XML is **required**. Without it the service fails to parse, the system kills it
in a restart loop, and the `enabled_accessibility_services` setting silently reverts to empty — which looks
exactly like "the setting does not stick".

## One-time setup per frame

Both settings survive reboots. Only reinstalling the app clears them.

```bash
adb shell appops set com.framerevive.app REQUEST_INSTALL_PACKAGES allow
adb shell settings put secure accessibility_enabled 1
adb shell settings put secure enabled_accessibility_services com.framerevive.app/.FrameInstallClicker
```

Verify it bound, rather than trusting the setting:

```bash
adb shell dumpsys accessibility | grep -A2 'Bound services'
# Bound services:{Service[label=FrameRevive, ... eventTypes=[TYPE_WINDOW_STATE_CHANGED, ...]}]}
```

To turn it off again:

```bash
adb shell settings put secure enabled_accessibility_services ""
adb shell appops set com.framerevive.app REQUEST_INSTALL_PACKAGES default
```

## Failure modes seen on this frame

| Symptom | Cause | Fix |
| --- | --- | --- |
| `Scheduled restart of crashed service`, then the accessibility setting is empty again | missing `android:description` in the service XML | add the description, reinstall, set the setting again |
| "This Android version will not allow a silent install" | device owner without the hidden API | expected on Android 10; use the prompt + clicker |
| Upload returns "Couldn't connect to server" right after an install | Android killed the app process; only the accessibility service was restarted | the home activity brings it back; otherwise `am start -n com.framerevive.app/.MainActivity` |
| `pm uninstall` fails with `DELETE_FAILED_DEVICE_POLICY_MANAGER` | an active device admin blocks removal | `pm disable-user --user 0 com.framerevive.app`, then uninstall |
| Screen-off sleep degrades to a black screen | device admin was cleared when the owner was released | `dpm set-active-admin com.framerevive.app/.FrameAdminReceiver` |

## Security notes

- The clicker only acts on the system installer window that names FrameRevive, and any APK the frame installs
  must still be signed with the same certificate as the running app. It cannot be used to install anything
  else.
- The controls link key stays the access token for the page. Anyone holding it can push an APK, which is why it
  is not written into files in synced folders.
- Enabling an accessibility service is a privileged convenience; the commands above are the audit trail for it,
  and the disable commands are one line each.

## What still needs a cable

Recovery only: re-enabling WiFi, a frame whose controls page is unreachable, or moving to a different signing
key (which requires uninstalling — see [release signing](release-signing.md)).
