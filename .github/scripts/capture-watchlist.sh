#!/usr/bin/env bash
set +e
./gradlew :app:connectedDebugAndroidTest --no-daemon
test_status=$?
adb pull /sdcard/Pictures/MonWallet/watchlist-real.png watchlist-real.png
pull_status=$?
adb logcat -d -s WatchlistNetworkTest:* MonWalletQuote:* > watchlist-provider-log.txt
if [ "$test_status" -ne 0 ]; then exit "$test_status"; fi
if [ "$pull_status" -ne 0 ] || [ ! -s watchlist-real.png ]; then exit 1; fi
