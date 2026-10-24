#!/bin/bash
# Wireless ADB helper for installing Jolt from the Mac (first install, or as a fallback) without a cable.
#
# One-time, on the phone: Settings → About phone → tap Build number 7× →
#   Settings → System → Developer options → Wireless debugging: ON.
#
#   ./adb-wireless.sh pair <ip:port> <code>   "Pair device with pairing code" screen
#                                              (its own port, shown with the code)
#   ./adb-wireless.sh connect <ip:port>       the IP & port on the main
#                                              Wireless debugging screen
#   ./adb-wireless.sh status                  connected devices
#
# Pairing is remembered; after a phone reboot or Wi-Fi change, only `connect` again
# (the port changes; Android may also switch Wireless debugging off on its own).
# Then: ./release.sh --install
set -euo pipefail
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"

case "${1:-status}" in
  pair)    "$ADB" pair "${2:?ip:port from the pairing-code screen}" "${3:?6-digit code}" ;;
  connect) "$ADB" connect "${2:?ip:port from the Wireless debugging screen}" ;;
  status)  "$ADB" devices -l ;;
  *)       sed -n '2,16p' "$0"; exit 2 ;;
esac
