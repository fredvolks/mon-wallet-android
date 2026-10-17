#!/usr/bin/env bash
# Single shell invocation: android-emulator-runner executes each YAML script line
# separately and cannot handle an inline multi-line for/if/done block.
set -euo pipefail

mkdir -p qa
adb logcat -c
adb logcat -v brief -s AndroidRuntime:E > qa/runtime-live.txt &
logcat_pid=$!
# Preserve emulator/host evidence even if adb disconnects.
trap 'kill "$logcat_pid" 2>/dev/null || true; adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1 || true; free -m > qa/host-memory.txt; df -h > qa/host-disk.txt' EXIT
# Same 412dp-wide S25 proportions, FHD pixel buffers for stable CI software rendering.
adb shell wm size 1080x2340
adb shell wm density 420
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Test the native failure view on an isolated fresh emulator, without user accounts.
adb shell cmd connectivity airplane-mode enable
adb shell am start -n ca.desadeux.jeu/.ConnectedLoveRollActivity
sleep 20
adb shell uiautomator dump /sdcard/loveroll-network-error.xml
adb pull /sdcard/loveroll-network-error.xml qa/loveroll-network-error.xml
adb exec-out screencap -p > qa/loveroll-network-error.png
python3 - <<'PY_NATIVE'
import xml.etree.ElementTree as ET
root=ET.parse('qa/loveroll-network-error.xml').getroot()
buttons=[n for n in root.iter('node') if n.get('text')=='Réessayer']
assert len(buttons)==1,'Native network failure must provide one retry, not an endless heart'
import re
x1,y1,x2,y2=map(int,re.findall(r'\d+',buttons[0].get('bounds')))
open('qa/retry-coordinate.txt','w').write(str((x1+x2)//2)+' '+str((y1+y2)//2)+'\n')
print('LOVEROLL_NATIVE_NETWORK_ERROR_OK retry is visible')
PY_NATIVE
adb shell cmd connectivity airplane-mode disable
sleep 3
read -r retry_x retry_y < qa/retry-coordinate.txt
adb shell input tap "$retry_x" "$retry_y"
sleep 10
adb shell uiautomator dump /sdcard/loveroll-after-retry.xml
adb pull /sdcard/loveroll-after-retry.xml qa/loveroll-after-retry.xml
python3 - <<'PY_NATIVE'
import xml.etree.ElementTree as ET
root=ET.parse('qa/loveroll-after-retry.xml').getroot()
assert not any(n.get('text')=='Réessayer' for n in root.iter('node')),'Retry returned to native error'
print('LOVEROLL_NATIVE_RETRY_OK no native error overlay remains')
PY_NATIVE
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
