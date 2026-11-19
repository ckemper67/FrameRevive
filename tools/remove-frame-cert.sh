#!/usr/bin/env bash
# List or remove FrameRevive frame certificates from a macOS keychain.
#
#   ./tools/remove-frame-cert.sh                          list certificates found in the default search list
#   ./tools/remove-frame-cert.sh --all --yes              delete every framerevive certificate from the login keychain
#   ./tools/remove-frame-cert.sh --sha1 <HASH> --yes      delete one certificate by SHA-1
#   ./tools/remove-frame-cert.sh --all --system --yes     also the System keychain (needs sudo)
#
# Deleting the certificate also drops the "Always Trust" setting attached to it, which is what
# makes Safari/Chrome stop warning. The frame's current identity is recorded in
# docs/security-pairing.md and in the exported certificate notes; anything else is stale.
set -euo pipefail

LABEL=framerevive
LOGIN="$HOME/Library/Keychains/login.keychain-db"
SYSTEM="/Library/Keychains/System.keychain"
ALL=0; YES=0; USE_SYSTEM=0; SHA1=""
while [ $# -gt 0 ]; do
  case "$1" in
    --all) ALL=1; shift;;
    --yes) YES=1; shift;;
    --system) USE_SYSTEM=1; shift;;
    --sha1) SHA1="$2"; shift 2;;
    --label) LABEL="$2"; shift 2;;
    -h|--help) sed -n '2,12p' "$0"; exit 0;;
    *) echo "Unknown argument: $1" >&2; exit 2;;
  esac
done

list() { security find-certificate -a -c "$LABEL" -Z "$@" 2>/dev/null | sed -n 's/^SHA-1 hash: //p'; }

echo "Certificates matching '$LABEL'"
echo
echo "login keychain ($LOGIN)"
LOGIN_HASHES=$(list "$LOGIN" || true)
[ -n "$LOGIN_HASHES" ] && printf '  %s\n' $LOGIN_HASHES || echo "  none"
if [ "$USE_SYSTEM" -eq 1 ]; then
  echo
  echo "System keychain ($SYSTEM)"
  SYSTEM_HASHES=$(list "$SYSTEM" || true)
  [ -n "$SYSTEM_HASHES" ] && printf '  %s\n' $SYSTEM_HASHES || echo "  none"
fi
echo
echo "Also check for trust settings left behind:"
echo "  security dump-trust-settings | grep -i $LABEL"
echo

TARGETS=()
if [ -n "$SHA1" ]; then
  TARGETS=("$SHA1")
elif [ "$ALL" -eq 1 ]; then
  # shellcheck disable=SC2206
  TARGETS=($LOGIN_HASHES)
  if [ "$USE_SYSTEM" -eq 1 ] && [ -n "${SYSTEM_HASHES:-}" ]; then
    # shellcheck disable=SC2206
    TARGETS+=($SYSTEM_HASHES)
  fi
fi
[ "${#TARGETS[@]}" -gt 0 ] || { echo "Nothing selected. Use --all or --sha1 <HASH>."; exit 0; }

echo "Will delete:"
printf '  %s\n' "${TARGETS[@]}"
if [ "$YES" -ne 1 ]; then
  echo "Dry run. Re-run with --yes to delete."
  exit 0
fi
for hash in "${TARGETS[@]}"; do
  if security delete-certificate -Z "$hash" "$LOGIN" 2>/dev/null; then
    echo "deleted $hash from login keychain"
  elif [ "$USE_SYSTEM" -eq 1 ] && sudo -n security delete-certificate -Z "$hash" "$SYSTEM" 2>/dev/null; then
    echo "deleted $hash from System keychain"
  else
    echo "could not delete $hash (try: sudo security delete-certificate -Z $hash $SYSTEM)" >&2
  fi
done
echo
echo "Remaining:"
remaining=$(list "$LOGIN" || true)
[ -n "$remaining" ] && printf '  %s\n' $remaining || echo "  none in the login keychain"
echo "Quit and reopen Safari/Chrome so they forget the old exception."
