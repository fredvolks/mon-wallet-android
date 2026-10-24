#!/usr/bin/env bash
set +e
./gradlew :app:connectedDebugAndroidTest --no-daemon
test_status=$?
adb pull /sdcard/Pictures/MonWallet/watchlist-real.png watchlist-real.png
pull_status=$?
adb pull /sdcard/Pictures/MonWallet/widget-4x2-daily-fixture.png widget-4x2-daily-fixture.png || true
adb pull /sdcard/Pictures/MonWallet/widget-4x2-six-titles.png widget-4x2-six-titles.png || true
adb pull /sdcard/Pictures/MonWallet/watchlist-diagnostic.png watchlist-diagnostic.png || true
for name in analyst-balance-failure analyst-ideas analyst-figures analyst-sources analyst-filters analyst-balance analyst-title analyst-scope family family-sale family-warning summary calendar day-detail closed-trades retained-trade transactions export export-month diagnostic etf-allocation etf-detail etf-underlying screener-card screener-filters vfv-detail vfv-underlying search-na search-general; do
  adb pull "/sdcard/Pictures/MonWallet/reports-$name.png" "reports-$name.png" || true
done
adb logcat -d -s WatchlistNetworkTest:* MonWalletQuote:* FundCoverage:* StockScreenerTest:* InvestmentScopeTest:* > watchlist-provider-log.txt
if [ "$test_status" -ne 0 ]; then exit "$test_status"; fi
if [ "$pull_status" -ne 0 ] || [ ! -s watchlist-real.png ]; then exit 1; fi
