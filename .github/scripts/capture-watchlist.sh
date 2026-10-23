#!/usr/bin/env bash
set +e
./gradlew :app:connectedDebugAndroidTest --no-daemon
test_status=$?
adb pull /sdcard/Pictures/MonWallet/watchlist-real.png watchlist-real.png
pull_status=$?
adb pull /sdcard/Pictures/MonWallet/widget-4x2-daily-fixture.png widget-4x2-daily-fixture.png || true
adb pull /sdcard/Pictures/MonWallet/widget-4x2-six-titles.png widget-4x2-six-titles.png || true
adb pull /sdcard/Pictures/MonWallet/watchlist-diagnostic.png watchlist-diagnostic.png || true
for name in summary calendar day-detail closed-trades transactions export export-month; do
  adb pull "/sdcard/Pictures/MonWallet/reports-$name.png" "reports-$name.png" || true
done
adb logcat -d -s WatchlistNetworkTest:* MonWalletQuote:* > watchlist-provider-log.txt
if [ "$test_status" -ne 0 ]; then exit "$test_status"; fi
if [ "$pull_status" -ne 0 ] || [ ! -s watchlist-real.png ]; then exit 1; fi
