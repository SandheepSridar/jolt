#!/bin/bash
# One-command release of Jolt.
#
#   ./release.sh                     bump patch (1.0.0 -> 1.0.1), build, verify, publish
#   ./release.sh minor|major         bump that part instead
#   ./release.sh 1.2.0               set an exact version
#   ./release.sh --notes "Fix X"     text shown on the phone's "Update available" card
#                                    (default: commit subjects since the last release)
#   ./release.sh --install           ...and also install on a phone connected over (wireless) ADB
#
# Publishing = a GitHub release on SandheepSridar/jolt with two assets, jolt.apk and
# latest.json. The app checks releases/latest/download/latest.json when it opens and
# offers a one-tap update.
#
# Needs the release key at ~/.config/jolt/keystore.properties (outside the repo; back it
# up). Android refuses updates signed with any other key.
set -euo pipefail

cd "$(dirname "$0")"
KEYPROPS="${JOLT_KEYSTORE_PROPS:-$HOME/.config/jolt/keystore.properties}"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)apksigner"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
REPO="SandheepSridar/jolt"

BUMP="patch"; INSTALL=0; NOTES=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --install) INSTALL=1 ;;
    --notes) NOTES="${2:?--notes needs text}"; shift ;;
    patch|minor|major) BUMP="$1" ;;
    [0-9]*.[0-9]*.[0-9]*) BUMP="$1" ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

[[ -f "$KEYPROPS" ]] || { echo "ERROR: release key config not found at $KEYPROPS" >&2; exit 1; }
prop() { grep "^$1=" "$2" | cut -d= -f2-; }

# The release is tagged at HEAD, so HEAD must be exactly what gets built.
if [[ -n "$(git status --porcelain)" ]]; then
  echo "ERROR: uncommitted changes. Commit them first so the release matches the code." >&2
  git status --short >&2
  exit 1
fi

# --- bump version (rolled back if anything below fails) ----------------------
cp version.properties version.properties.bak
trap 'mv version.properties.bak version.properties; echo "Release failed; version not changed." >&2' ERR
OLD_CODE=$(prop VERSION_CODE version.properties)
OLD_NAME=$(prop VERSION_NAME version.properties)
if git rev-parse -q --verify "refs/tags/v$OLD_NAME" >/dev/null; then
  NEW_CODE=$((OLD_CODE + 1))
  IFS=. read -r MA MI PA <<<"$OLD_NAME"
  case "$BUMP" in
    patch) NEW_NAME="$MA.$MI.$((PA + 1))" ;;
    minor) NEW_NAME="$MA.$((MI + 1)).0" ;;
    major) NEW_NAME="$((MA + 1)).0.0" ;;
    *)     NEW_NAME="$BUMP" ;;
  esac
else
  # Nothing released yet: publish the current version as-is.
  NEW_CODE=$OLD_CODE; NEW_NAME=$OLD_NAME
fi
printf "# Bumped by release.sh. VERSION_CODE must increase for Android to accept an update.\nVERSION_CODE=%s\nVERSION_NAME=%s\n" \
  "$NEW_CODE" "$NEW_NAME" > version.properties
echo "==> $OLD_NAME ($OLD_CODE) -> $NEW_NAME ($NEW_CODE)"

if [[ -z "$NOTES" ]]; then
  LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || true)
  NOTES=$(git log --format='%s' ${LAST_TAG:+"$LAST_TAG"..HEAD} | grep -v '^Release Jolt' | head -5 | paste -sd ';' - | sed 's/;/; /g')
  NOTES="${NOTES:-Jolt $NEW_NAME}"
fi

# --- build + test --------------------------------------------------------------
./gradlew :app:testDebugUnitTest :app:assembleRelease -q --console=plain
APK="app/build/outputs/apk/release/app-release.apk"
[[ -f "$APK" ]] || { echo "ERROR: no signed release APK (is the release key configured?)" >&2; false; }

# --- verify it's signed with OUR release key ----------------------------------
EXPECTED=$("$JAVA_HOME/bin/keytool" -list -v -keystore "$(prop storeFile "$KEYPROPS")" \
  -storepass "$(prop storePassword "$KEYPROPS")" -alias "$(prop keyAlias "$KEYPROPS")" 2>/dev/null \
  | awk -F': ' '/SHA256:/{print $2}' | tr -d ': ' | tr 'A-F' 'a-f')
ACTUAL=$("$APKSIGNER" verify --print-certs "$APK" | awk -F': ' '/certificate SHA-256 digest/{print $2; exit}')
[[ -n "$EXPECTED" && "$EXPECTED" == "$ACTUAL" ]] || {
  echo "ERROR: APK signature ($ACTUAL) doesn't match the release key ($EXPECTED)" >&2; false; }
echo "==> signed with release key ${ACTUAL:0:16}…"

# --- stage assets ---------------------------------------------------------------
mkdir -p dist
cp "$APK" dist/jolt.apk
SHA=$(shasum -a 256 dist/jolt.apk | cut -d' ' -f1)
SIZE=$(stat -f%z dist/jolt.apk)
python3 - "$NEW_CODE" "$NEW_NAME" "$SHA" "$SIZE" "$NOTES" > dist/latest.json <<'PY'
import datetime, json, sys
code, name, sha, size, notes = sys.argv[1:]
print(json.dumps({
    "version_code": int(code), "version_name": name, "sha256": sha, "size": int(size),
    "notes": notes, "built_at": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
}, indent=2))
PY

# --- commit, tag and publish ----------------------------------------------------
if ! git diff --quiet version.properties; then
  git commit -q -m "Release Jolt $NEW_NAME" version.properties
fi
rm version.properties.bak
trap - ERR
git push -q origin HEAD
gh release create "v$NEW_NAME" dist/jolt.apk dist/latest.json \
  --repo "$REPO" --target "$(git rev-parse HEAD)" --title "Jolt $NEW_NAME" --notes "$NOTES"
echo "==> published v$NEW_NAME ($((SIZE / 1024 / 1024)) MB)"

# --- optional: install now over ADB (USB or wireless) ---------------------------
if [[ $INSTALL -eq 1 ]]; then
  DEVICES=$("$ADB" devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1}')
  if [[ -z "$DEVICES" ]]; then
    echo "==> --install: no phone connected. Pair/connect first: ./adb-wireless.sh" >&2
  else
    for d in $DEVICES; do
      echo "==> installing on $d"; "$ADB" -s "$d" install -r dist/jolt.apk | tail -1
    done
  fi
fi

echo
echo "Done. Open Jolt on the phone: it shows \"Update available · v$NEW_NAME\"."
