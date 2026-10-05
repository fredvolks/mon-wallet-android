#!/usr/bin/env bash
set -euo pipefail
mkdir -p signing
keytool -genkeypair \
  -v \
  -keystore signing/monwallet-release.jks \
  -alias monwallet \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000

echo "IMPORTANT: conserve signing/monwallet-release.jks en lieu sûr et ne le commit jamais."
