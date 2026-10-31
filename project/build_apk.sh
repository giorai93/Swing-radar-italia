#!/usr/bin/env bash
set -euo pipefail
APP_ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILDTOOLS="${ANDROID_HOME:?ANDROID_HOME missing}/build-tools/35.0.0"
PLATFORM="$ANDROID_HOME/platforms/android-35/android.jar"
OUT="$APP_ROOT/build"
mkdir -p "$OUT/classes" "$OUT/generated" "$OUT/dex"
"$BUILDTOOLS/aapt2" compile --dir "$APP_ROOT/res" -o "$OUT/resources.zip"
"$BUILDTOOLS/aapt2" link -o "$OUT/unsigned.apk" -I "$PLATFORM" \
  --manifest "$APP_ROOT/AndroidManifest.xml" -A "$APP_ROOT/assets" \
  --min-sdk-version 26 --target-sdk-version 34 --version-code 30 \
  --version-name 3.0 --java "$OUT/generated" -R "$OUT/resources.zip"
java com.sun.tools.javac.Main -source 8 -target 8 -Xlint:-options -encoding UTF-8 -bootclasspath "$PLATFORM" \
  -d "$OUT/classes" "$OUT/generated/it/swingradar/italia/v11/R.java" \
  "$APP_ROOT/java/it/swingradar/italia/v11/MainActivity.java" \
  "$APP_ROOT/java/it/swingradar/italia/v11/RadarScanner.java" \
  "$APP_ROOT/java/it/swingradar/italia/v11/RecoveryPhases.java" \
  "$APP_ROOT/java/it/swingradar/italia/v11/MarketSessions.java" \
  "$APP_ROOT/java/it/swingradar/italia/v11/ItalianUniverse.java" \
  "$APP_ROOT/java/it/swingradar/italia/v11/EarningsCalendar.java" \
  "$APP_ROOT/java/it/swingradar/italia/v11/Fundamentals.java" \
  "$APP_ROOT/java/it/swingradar/italia/v11/RadarStore.java" \
  "$APP_ROOT/java/it/swingradar/italia/v11/ScanJobService.java"
(cd "$OUT/classes" && zip -q -r "$OUT/classes.jar" .)
"$BUILDTOOLS/d8" --lib "$PLATFORM" --min-api 26 --output "$OUT/dex" "$OUT/classes.jar"
(cd "$OUT/dex" && zip -q -u "$OUT/unsigned.apk" classes.dex)
if [ ! -f "$OUT/local-signing-key.jks" ]; then
  keytool -genkeypair -keystore "$OUT/local-signing-key.jks" -storepass android \
    -keypass android -alias swingradar -keyalg RSA -keysize 3072 -validity 3650 \
    -dname "CN=Swing Radar Italia 1.1, OU=Private research, O=Personal, C=IT"
fi
"$BUILDTOOLS/apksigner" sign --ks "$OUT/local-signing-key.jks" \
  --ks-key-alias swingradar --ks-pass pass:android --key-pass pass:android \
  --out "$APP_ROOT/Swing_Radar_Italia_3.0.apk" "$OUT/unsigned.apk"
"$BUILDTOOLS/apksigner" verify --verbose "$APP_ROOT/Swing_Radar_Italia_3.0.apk"
"$BUILDTOOLS/aapt2" dump badging "$APP_ROOT/Swing_Radar_Italia_3.0.apk" | head -9
ls -lh "$APP_ROOT/Swing_Radar_Italia_3.0.apk"
