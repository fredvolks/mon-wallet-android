#!/usr/bin/env bash
# Single shell invocation: android-emulator-runner executes each YAML script line
# separately and cannot handle an inline multi-line for/if/done block.
set -euo pipefail

mkdir -p qa
adb shell wm size 1440x3120
adb shell wm density 560
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Non-blocking launch: am start -W may time out waiting for remote WebView.
adb shell am start -n ca.desadeux.jeu/.ConnectedLoveRollActivity
sleep 10

for attempt in 1 2 3; do
  if adb exec-out screencap -p > qa/loveroll-shared-s25-android.png; then
    if test "$(stat -c%s qa/loveroll-shared-s25-android.png)" -gt 15000; then
      break
    fi
  fi
  sleep 3
done
test -s qa/loveroll-shared-s25-android.png
test "$(stat -c%s qa/loveroll-shared-s25-android.png)" -gt 15000

adb shell dumpsys activity activities > qa/activities.txt
grep 'ca.desadeux.jeu/.ConnectedLoveRollActivity' qa/activities.txt || grep 'ConnectedLoveRollActivity' qa/activities.txt
# UIAutomator cannot inspect the WebView DOM reliably, so web Playwright CI
# checks exact HTML/dice placement separately.
adb logcat -d -v brief -s AndroidRuntime:E > qa/runtime_errors.txt || true
if grep -q 'FATAL EXCEPTION' qa/runtime_errors.txt; then
  cat qa/runtime_errors.txt
  exit 1
fi
echo "LOVEROLL_S25_EMULATOR_OK screenshot=$(stat -c%s qa/loveroll-shared-s25-android.png)"
