#!/usr/bin/env bash
# Upload a build to a frame over WiFi and install it (macOS). Never prints the controls link or its key.
#
#   FRAME_LINK='https://framerevive-xxxx.local:8765/?key=...' ./tools/deploy-frame.sh [app.apk]
#   ./tools/deploy-frame.sh            (asks for the link, hidden, when FRAME_LINK is not set)
#
# The APK defaults to the debug build. The frame's certificate must match one trusted in your keychain
# (`security find-certificate -a -c framerevive -Z`), or the fingerprint in FRAME_FINGERPRINT (64 hex digits, colons
# optional); otherwise nothing is sent. See docs/wireless-updates.md.
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
if [ -z "${FRAME_LINK:-}" ] && [ -t 0 ]; then
  read -rsp "Paste the frame's controls link (hidden): " FRAME_LINK; echo
fi
[ -n "${FRAME_LINK:-}" ] || { echo "Set FRAME_LINK to the frame's controls link first (export it, or put it before the command on one line)."; exit 1; }
[ -f "$APK" ] || { echo "No APK at $APK."; exit 1; }
HOSTPORT=$(printf '%s' "$FRAME_LINK" | sed -E 's#^https://([^/?]+).*#\1#')
[ "$HOSTPORT" != "$FRAME_LINK" ] || { echo "FRAME_LINK must start with https://."; exit 1; }
HOST=${HOSTPORT%%:*}; PORT=${HOSTPORT##*:}; [ "$PORT" = "$HOSTPORT" ] && PORT=443
PEM=$(mktemp); trap 'rm -f "$PEM"' EXIT
openssl s_client -connect "$HOST:$PORT" -servername "$HOST" </dev/null 2>/dev/null | openssl x509 -outform PEM > "$PEM"
[ -s "$PEM" ] || { echo "Could not read the frame's certificate (is it awake and on the network?)."; exit 1; }
hex() { tr -d ': \n' | tr 'a-f' 'A-F'; }
FRAME_FP=$(openssl x509 -in "$PEM" -noout -fingerprint -sha256 | sed 's/.*=//' | hex)
TRUSTED=$( { security find-certificate -a -c framerevive -Z 2>/dev/null | grep "SHA-256 hash" | sed 's/.*: //'; printf '%s\n' "${FRAME_FINGERPRINT:-}"; } | tr -d ': ' | tr 'a-f' 'A-F')
if ! printf '%s\n' "$TRUSTED" | grep -qx "$FRAME_FP"; then
  echo "The frame's certificate ($FRAME_FP) is not one you trust. Not sending the key."; exit 1
fi
echo "Certificate matches a trusted one ($FRAME_FP)."
# The frame's reply is a small HTML page: keep its first paragraph and decode the entities it escapes.
text() { sed -e 's#</p>.*##' -e 's/<[^>]*>/ /g' -e "s/&#39;/'/g" -e 's/&quot;/"/g' -e 's/&lt;/</g' -e 's/&gt;/>/g' -e 's/&amp;/\&/g' | tr -s ' \n' | head -c 400; echo; }
echo "Uploading $(basename "$APK")..."
curl -sS --cacert "$PEM" -H "Content-Type: application/vnd.android.package-archive" --data-binary @"$APK" "${FRAME_LINK}&action=uploadApk" | text
echo "Installing..."
curl -sS --cacert "$PEM" -d action=installApk "$FRAME_LINK" | text
echo "Waiting for the install, then reading the version back..."
sleep 20
curl -sS --cacert "$PEM" "$FRAME_LINK" | grep -o '[0-9]\.[0-9]*\.[0-9]*-alpha[^< ]*' | sort -u
