#!/usr/bin/env bash
# Force the app into a standby bucket, then watch how the next pushes land.
#
# Usage: scripts/spike/standby-bucket-probe.sh <bucket>
#   bucket: active | working_set | frequent | rare | restricted
#
# Send the pushes themselves from the server or the FCM console while this
# runs. It does not send them; it only controls the variable and records
# the result.
set -euo pipefail

PACKAGE="com.flipcash.app.android"
BUCKET="${1:?usage: standby-bucket-probe.sh <active|working_set|frequent|rare|restricted>}"
OUT="docs/spikes/raw/${BUCKET}-$(date +%Y%m%dT%H%M%S).log"

mkdir -p "$(dirname "$OUT")"

adb shell am set-standby-bucket "$PACKAGE" "$BUCKET"
echo "bucket now: $(adb shell am get-standby-bucket "$PACKAGE")"

# Background the app and let the system settle before measuring.
adb shell input keyevent KEYCODE_HOME
sleep 5

adb logcat -c
echo "recording to $OUT — send test pushes now, Ctrl-C when done"
adb logcat -v time | grep --line-buffered "onMessageReceived" | tee "$OUT"
