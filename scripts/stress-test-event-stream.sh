#!/bin/bash
# Event Stream Stress Test
#
# Prerequisites: adb connected to device, app installed

set -euo pipefail

LABEL="${1:-test}"
PKG="com.flipcash.app.android"
LOGFILE="event-stream-${LABEL}-$(date +%Y%m%d-%H%M%S).log"
TAGS="gRPC:V BIDI:V ChatCoordinator:V EventStream:V event-streaming:V EventStreamingController:V *:S"

echo "=== Event Stream Stress Test ($LABEL) ==="
echo "Logging to: $LOGFILE"
echo ""

# Clear logcat
adb logcat -c

# Start logcat capture in background
adb logcat $TAGS | tee "$LOGFILE" &
LOGCAT_PID=$!
trap "kill $LOGCAT_PID 2>/dev/null; echo; echo 'Logs saved to $LOGFILE'" EXIT

pause() {
    echo ""
    echo ">>> $1"
    echo "    Press ENTER when ready..."
    read -r
}

wait_and_log() {
    local duration=$1
    local label=$2
    echo "    [$label] Waiting ${duration}s..."
    sleep "$duration"
    echo "    [$label] Done."
}

echo "--- Test 1: Cold Start ---"
echo "    Force stopping app..."
adb shell am force-stop "$PKG"
sleep 2
echo "    Launching app..."
adb shell am start -n "$PKG/.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
wait_and_log 15 "cold-start"
echo "    CHECK: Look for 'flipcash-stream => READY' and 'Stream activated on first response'"
echo ""

echo "--- Test 2: Airplane Mode Toggle (network loss/recovery) ---"
echo "    Enabling airplane mode..."
adb shell cmd connectivity airplane-mode enable
wait_and_log 5 "airplane-on"
echo "    Disabling airplane mode..."
adb shell cmd connectivity airplane-mode disable
wait_and_log 20 "airplane-off"
echo "    CHECK: Stream should reconnect. Look for 'Opening bidirectional stream' then 'Stream activated'"
echo ""

echo "--- Test 3: Rapid Network Flapping (3 cycles) ---"
for i in 1 2 3; do
    echo "    Cycle $i: airplane ON..."
    adb shell cmd connectivity airplane-mode enable
    sleep 3
    echo "    Cycle $i: airplane OFF..."
    adb shell cmd connectivity airplane-mode disable
    sleep 8
done
wait_and_log 15 "flap-settle"
echo "    CHECK: Should settle to one active stream, no zombie states"
echo ""

echo "--- Test 4: Background/Foreground Cycling ---"
echo "    Sending to background..."
adb shell input keyevent KEYCODE_HOME
wait_and_log 5 "background"
echo "    Bringing to foreground..."
adb shell am start -n "$PKG/.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
wait_and_log 10 "foreground"
echo "    Repeat..."
adb shell input keyevent KEYCODE_HOME
sleep 3
adb shell am start -n "$PKG/.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
wait_and_log 10 "foreground-2"
echo "    CHECK: Stream should close on background, reopen on foreground"
echo ""

echo "--- Test 5: Long Idle (simulates carrier NAT timeout) ---"
echo "    Waiting 90s with app in foreground (covers typical 30-90s NAT timeout)..."
wait_and_log 90 "long-idle"
echo "    CHECK: Heartbeat should detect dead stream and reconnect if needed"
echo ""

echo "--- Test 6: Wi-Fi Toggle (different network path) ---"
echo "    Disabling Wi-Fi..."
adb shell svc wifi disable
wait_and_log 10 "wifi-off"
echo "    Enabling Wi-Fi..."
adb shell svc wifi enable
wait_and_log 15 "wifi-on"
echo "    CHECK: Stream reconnects on new network"
echo ""

echo ""
echo "=== Stress test complete ==="
echo ""
echo "Review logs: less $LOGFILE"
echo ""
echo "Key things to grep for:"
echo "  grep 'flipcash-stream =>'  $LOGFILE   # channel state changes"
echo "  grep 'activateStream'      $LOGFILE   # when stream becomes 'connected'"
echo "  grep 'Stream active:'      $LOGFILE   # isActive state changes"
echo "  grep 'zombie\\|Giving up'   $LOGFILE   # failure modes (should be absent)"
echo "  grep 'Event stream error'  $LOGFILE   # retry triggers"
echo "  grep 'Pong'                $LOGFILE   # successful ping/pong = healthy stream"
