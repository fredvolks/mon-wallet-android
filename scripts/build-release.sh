#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
version="${1:?Usage: scripts/build-release.sh 0.2.0 [--quick]}"
[[ "$version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || { echo 'Invalid semantic version' >&2; exit 2; }
major="${BASH_REMATCH[1]}" minor="${BASH_REMATCH[2]}" patch="${BASH_REMATCH[3]}"
(( minor < 1000 && patch < 1000 )) || exit 2
code=$((10#$major * 1000000 + 10#$minor * 1000 + 10#$patch))
[[ -n "${MONWALLET_STORE_FILE:-}" && -n "${MONWALLET_STORE_PASSWORD:-}" &&
   -n "${MONWALLET_KEY_ALIAS:-}" && -n "${MONWALLET_KEY_PASSWORD:-}" ]] || {
  echo 'Signing variables are required' >&2; exit 2;
}
gradle="${MONWALLET_GRADLE:-./gradlew}"
tasks=(:app:testDebugUnitTest :app:assembleRelease :app:bundleRelease)
[[ "${2:-}" == "--quick" ]] && tasks=(:app:assembleRelease :app:bundleRelease)
# AGP can reuse stale project dex after Kotlin incremental compilation; discard only generated dex.
rm -rf app/build/intermediates/project_dex_archive/release app/build/intermediates/dex/release
if ! "$gradle" "${tasks[@]}" -PversionCode="$code" -PversionName="$version" \
  --no-build-cache --no-daemon --console=plain; then
  echo 'Incremental build failed; retrying from a clean build.' >&2
  "$gradle" :app:clean "${tasks[@]}" -PversionCode="$code" -PversionName="$version" \
    --no-build-cache --no-daemon --console=plain
fi
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk "dist/MonWallet-v$version.apk"
cp app/build/outputs/bundle/release/app-release.aab "dist/MonWallet-v$version.aab"
"${ANDROID_HOME:?}/build-tools/35.0.0/apksigner" verify --verbose "dist/MonWallet-v$version.apk"
"${JAVA_HOME:?}/bin/jarsigner" -verify "dist/MonWallet-v$version.aab" >/dev/null
sha256sum "dist/MonWallet-v$version.apk" "dist/MonWallet-v$version.aab"
