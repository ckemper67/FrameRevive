#!/usr/bin/env bash
# Re-sign an APK that was built with someone else's key, so a development frame locked to your key can install
# it. Typical use: taking an upstream FrameRevive release without rebuilding it from source.
#
#   ./tools/resign-apk.sh --in FrameRevive-0.5.26-alpha.apk --out FrameRevive-0.5.26-dev.apk \
#                         --properties keystore.properties [--apksigner <path>]
#
# The output carries YOUR certificate, not the original publisher's. Never redistribute a re-signed build as
# if it came from the original publisher, and remember Android still requires a versionCode that is not lower
# than the installed one.
set -euo pipefail

IN=""; OUT=""; PROPS=""; APKSIGNER=""
while [ $# -gt 0 ]; do
  case "$1" in
    --in) IN="$2"; shift 2;;
    --out) OUT="$2"; shift 2;;
    --properties) PROPS="$2"; shift 2;;
    --apksigner) APKSIGNER="$2"; shift 2;;
    *) echo "Unknown argument: $1" >&2; exit 2;;
  esac
done
fail() { echo "$1" >&2; exit 2; }
[ -f "$IN" ] || fail "--in must point at an APK"
[ -n "$OUT" ] || fail "--out must name the re-signed APK"
[ -f "$PROPS" ] || fail "--properties must point at keystore.properties"
[ "$(cd "$(dirname "$IN")" && pwd)/$(basename "$IN")" = "$(cd "$(dirname "$OUT")" && pwd)/$(basename "$OUT")" ] && fail "--out must differ from --in"

if [ -z "$APKSIGNER" ]; then
  for root in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk"; do
    [ -n "$root" ] && [ -d "$root/build-tools" ] && APKSIGNER=$(ls -d "$root"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1) && break
  done
fi
[ -x "${APKSIGNER:-}" ] || fail "apksigner not found; pass --apksigner or set ANDROID_HOME"

prop() { sed -n "s/^$1=//p" "$PROPS" | head -1; }
STORE=$(prop storeFile); STORE_PASS=$(prop storePassword); ALIAS=$(prop keyAlias); KEY_PASS=$(prop keyPassword)
[ -n "$STORE" ] && [ -n "$ALIAS" ] || fail "$PROPS must define storeFile and keyAlias"
case "$STORE" in /*) ;; *) STORE="$(cd "$(dirname "$PROPS")" && pwd)/$STORE";; esac
[ -f "$STORE" ] || fail "keystore not found at $STORE"

WORK=$(mktemp -d) || exit 1
trap 'rm -rf "$WORK"' EXIT
cp "$IN" "$WORK/app.apk"
# Android rejects a second signature: strip any v1 (JAR) signature entries before signing again.
zip -q -d "$WORK/app.apk" 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.EC' 'META-INF/*.DSA' >/dev/null 2>&1 || true

"$APKSIGNER" sign --ks "$STORE" --ks-key-alias "$ALIAS" \
  --ks-pass "pass:$STORE_PASS" --key-pass "pass:${KEY_PASS:-$STORE_PASS}" \
  --out "$OUT" "$WORK/app.apk" 2>/dev/null

"$APKSIGNER" verify --print-certs "$OUT" 2>/dev/null | sed -n 's/^Signer #1 certificate DN: /signed by:    /p;s/^Signer #1 certificate SHA-256 digest: /SHA-256:     /p'
BADDING=$(dirname "$APKSIGNER")/aapt
[ -x "$BADDING" ] && "$BADDING" dump badging "$OUT" 2>/dev/null | sed -n '1s/^package: //;1s/^/badging:     /p'
echo "written:     $OUT"
