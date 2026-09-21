#!/usr/bin/env bash
set -euo pipefail

# Compilation can leave the emulator idle long enough to lock. Wake it after
# both APKs are ready, immediately before the UI tests need window focus.
gradle :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace
adb shell svc power stayon true
adb shell locksettings set-disabled true
adb shell settings put system screen_off_timeout 2147483647
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell input keyevent KEYCODE_HOME
sleep 2

test_result=0
gradle :app:connectedDebugAndroidTest --stacktrace || test_result=$?
if [ "$test_result" -ne 0 ]; then
  diagnostic_dir=app/build/outputs/androidTest-results/connected/ci-diagnostics
  mkdir -p "$diagnostic_dir"
  adb shell dumpsys window > "$diagnostic_dir/windows.txt" || true
  adb logcat -d > "$diagnostic_dir/logcat.txt" || true
  adb exec-out screencap -p > "$diagnostic_dir/screen.png" || true
fi
exit "$test_result"
