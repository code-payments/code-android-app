#!/usr/bin/env bash
# Send data-only pushes into one standby bucket and record what arrives.
#
# Usage: scripts/spike/run-bucket-matrix.sh <device_token> <bucket> [count] [interval_s] [mode]
#   bucket:     active | working_set | frequent | rare | restricted
#   count:      pushes to send (default 20)
#   interval_s: seconds between sends (default 180, so 20 sends span an hour)
#   mode:       silent (default, data-only, no title) | visible (control, with title)
#
# Each send carries a sequence id in the spike_seq data key, which the
# onMessageReceived trace logs as seq=. That is what makes a missing push
# distinguishable from a late one: correlation is per-push, not per-window.
# It is NOT carried in push_notification_body: the trace never logged the body
# (MetadataBuilder.to takes a non-null Any, so a String? resolves to kotlin.to
# and is discarded), and putting message text in a Bugsnag breadcrumb is a leak.
#
# Set DEVICE to an adb serial when more than one device is attached.
#
# DOZE=1 runs the cell under deep Doze on battery instead of the default
# charging, never-idle state. Doze is the one condition under which buckets are
# documented to withhold work, so a cell measured while charging cannot speak to
# it. The mode holds the device there for the whole run:
#   - `dumpsys battery unplug` makes the framework see battery. The cable stays
#     in, so the run does not depend on a charge level, but every power decision
#     above the driver is taken as if unplugged. Reachability over Wi-Fi adb is
#     therefore not required, though it is the more faithful setup.
#   - `deviceidle force-idle` needs the screen off and the framework unplugged,
#     which is why both precede it.
#   - Idle is re-asserted before each send: a delivery can pull the device into
#     a maintenance window, and a cell that silently left Doze after push 3
#     measures the same thing the charging cells already did.
# Both overrides are released on exit, including on failure.
#
# DOZE=natural waits for deep idle instead of forcing it. `force-idle` applies
# Doze's restriction set but skips the gating that normally precedes it — the
# screen-off timer, and the significant-motion detector that resets the whole
# countdown when the phone is picked up. A cell that reached idle on its own is
# the stronger claim, and it is the one the forced cell explicitly does not make.
# What the mode does differently:
#   - Nothing is overridden. The device must be PHYSICALLY unplugged, which is
#     why the mode requires adb over Wi-Fi and refuses to run on USB.
#   - It polls `deviceidle get deep` until it reads IDLE, up to IDLE_TIMEOUT
#     (default 2h) at IDLE_POLL intervals (default 60s), then starts sending.
#   - Idle is NOT re-asserted between sends. A delivery that pulls the device
#     into a maintenance window is the behaviour under test, not a defect in the
#     cell, so the per-send `deep=` in the .power sidecar is the result rather
#     than something the runner corrects.
# The phone must be left still for the whole run. Motion restarts Doze's
# countdown, and nothing in this script can see that happen — only the deep=
# samples will show it, after the fact.
# The mode tolerates the Wi-Fi link dropping, which it will: adb calls retry for
# ADB_GRACE seconds and logcat reattaches from where it stopped, so a reset
# connection costs an annotated gap in the capture rather than the cell. Three
# consecutive sends with the device unreachable still fail the run — at that
# point the pushes are going somewhere nothing is recording.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"

TOKEN="${1:?usage: run-bucket-matrix.sh <device_token> <bucket> [count] [interval_s] [mode]}"
BUCKET="${2:?bucket required}"
COUNT="${3:-20}"
INTERVAL="${4:-180}"
MODE="${5:-silent}"
# Seconds to keep the log open after the last send, to catch stragglers.
# Lower it only for a smoke test; a real bucket run needs the full window.
HOLD="${HOLD:-300}"
# 0 (charging, never idle) | 1 (forced deep idle) | natural (idle reached)
DOZE="${DOZE:-0}"
# DOZE=natural only: how long to wait for deep idle, and how often to look.
# Each poll is an adb round trip, which nudges the device awake, so keep the
# cadence coarse — the wait is measured in tens of minutes either way.
IDLE_TIMEOUT="${IDLE_TIMEOUT:-7200}"
IDLE_POLL="${IDLE_POLL:-60}"

# PAYLOAD is a base64 flipcash.push.v1.Payload, sent as the flipcash_payload
# data key. Without it every cell measures delivery and nothing else, because
# planPushHandling derives all sync work from that payload and returns an empty
# action list when it is absent — which is what the first seven cells did, all
# of them recording actions=0 in the trace they were read from. The two-byte
# encoding `IAU=` is category=CONTACT_JOIN and nothing else, which plans
# RefreshFeed + SyncContacts without naming a chat or a contact.
#
# Note the flag interaction: planPushHandling consults PushSilentSync only when
# the title is null, so MODE=silent measures the flag and the sync path
# together, while MODE=visible exercises the sync path whatever the flag says.
PAYLOAD="${PAYLOAD:-}"

PACKAGE="com.flipcash.app.android"
# Target one device via ANDROID_SERIAL rather than an adb wrapper function:
# wrapping adb makes $! the wrapper subshell's pid, so the later kill misses the
# real logcat, which then keeps the script's stdout pipe open forever.
if [ -n "${DEVICE:-}" ]; then
    export ANDROID_SERIAL="$DEVICE"
fi
STAMP="$(date +%Y%m%dT%H%M%S)"
OUT_DIR="$REPO/docs/spikes/raw"
# A natural-idle cell is marked in the filename. The forced and charging cells
# keep the original naming so the captures already committed still match it.
SUFFIX=""
[ "$DOZE" = "natural" ] && SUFFIX="-natdoze"
LOG="$OUT_DIR/${BUCKET}-${MODE}${SUFFIX}-${STAMP}.log"
SENDS="$OUT_DIR/${BUCKET}-${MODE}${SUFFIX}-${STAMP}.sends"
POWER="$OUT_DIR/${BUCKET}-${MODE}${SUFFIX}-${STAMP}.power"
mkdir -p "$OUT_DIR"

# Fail before the run rather than after it. A disconnected device makes every
# adb call print "device not found" to stderr and carry on, which once produced
# three cells that reported success against no device at all.
require_device() {
    adb shell true >/dev/null 2>&1 \
        || { echo "FATAL: no device (${ANDROID_SERIAL:-default}) — nothing was measured" >&2; exit 1; }
}
require_device

MODEL="$(adb shell getprop ro.product.model | tr -d '\r')"
RELEASE="$(adb shell getprop ro.build.version.release | tr -d '\r')"
SDK="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"

# Wi-Fi adb drops, and a drop is not a result. Over screen-off Doze the phone
# resets its adbd connection: TCP 5555 stays open and the device re-authorizes
# within seconds, but every adb call issued in that window returns "device
# offline", and under set -e that took the first natural-Doze cell down at send
# 5 of 20 with the device still sitting in unforced deep idle. So one-shot adb
# calls retry, and only a device that stays unreachable for ADB_GRACE seconds is
# treated as gone.
ADB_GRACE="${ADB_GRACE:-180}"
adb_try() {
    local deadline=$((SECONDS + ADB_GRACE)) out
    while :; do
        if out="$(adb "$@" 2>/dev/null)"; then printf '%s' "$out"; return 0; fi
        [ "$SECONDS" -ge "$deadline" ] && return 1
        adb reconnect >/dev/null 2>&1 || true
        sleep 5
    done
}

# logcat dies with the connection too, and it is the capture — a cell whose log
# stops at send 5 reads as fifteen undelivered pushes. Reattach from the last
# stamped line rather than re-dumping the buffer, so the outage window is
# recovered; the parse step keeps the first trace per seq, so the overlap that
# creates is discarded rather than counted twice. Each gap is marked in the log.
logcat_forever() {
    local last=""
    while :; do
        if [ -n "$last" ]; then
            adb logcat -v time -T "$last" >> "$LOG" 2>/dev/null &
        else
            adb logcat -v time >> "$LOG" 2>/dev/null &
        fi
        echo $! > "$LOGCAT_CHILD"
        wait $! 2>/dev/null || true
        echo "# logcat detached $(date +%s000)" >> "$LOG"
        last="$(grep -oE '^[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}' "$LOG" | tail -1)"
        sleep 5
        adb_try shell true >/dev/null || continue
        echo "# logcat reattached $(date +%s000)" >> "$LOG"
    done
}

# Power state is sampled, not assumed. The first six cells were all written up
# as "charging, therefore Doze-exempt" on the strength of spot checks taken
# outside the capture; a reader had to take that on trust. These fields make the
# claim checkable from the artefacts alone, and catch a run that drifted out of
# the state it was supposed to be measuring.
power_sample() {
    local label="$1"
    local plugged deep light bucket
    if ! plugged="$(adb_try shell dumpsys battery | awk -F': ' '
        /AC powered/       {ac=$2}
        /USB powered/      {usb=$2}
        /Wireless powered/ {wl=$2}
        END {printf "ac=%s,usb=%s,wireless=%s", ac, usb, wl}' | tr -d '\r')"; then
        echo "$(date +%s000) ${label} unreachable"
        return 1
    fi
    deep="$(adb_try shell dumpsys deviceidle get deep | tr -d '\r')" || deep="?"
    light="$(adb_try shell dumpsys deviceidle get light | tr -d '\r')" || light="?"
    bucket="$(adb_try shell am get-standby-bucket "$PACKAGE" | tr -d '\r')" || bucket="?"
    echo "$(date +%s000) ${label} plugged=[${plugged}] deep=${deep} light=${light} bucket=${bucket}"
}

release_overrides() {
    if [ "$DOZE" = "1" ]; then
        adb shell dumpsys deviceidle unforce >/dev/null 2>&1 || true
        adb shell dumpsys battery reset >/dev/null 2>&1 || true
    fi
}

adb shell am set-standby-bucket "$PACKAGE" "$BUCKET"
echo "bucket code now: $(adb shell am get-standby-bucket "$PACKAGE" | tr -d '\r')"
echo "device: $MODEL, Android $RELEASE (API $SDK)"

# Background the app and let the system settle before measuring. Skipped under
# DOZE=natural: HOME is a wake key, so on a screen-off device it turns the
# display on and restarts the idle countdown the mode is waiting on — and a
# device that is already asleep has already backgrounded the app.
if [ "$DOZE" != "natural" ]; then
    adb shell input keyevent KEYCODE_HOME
    sleep 5
fi

if [ "$DOZE" = "1" ]; then
    trap 'release_overrides' EXIT
    adb shell input keyevent KEYCODE_SLEEP
    sleep 3
    adb shell dumpsys battery unplug >/dev/null
    adb shell dumpsys deviceidle force-idle >/dev/null
    DEEP="$(adb shell dumpsys deviceidle get deep | tr -d '\r')"
    [ "$DEEP" = "IDLE" ] \
        || { echo "FATAL: deep idle is $DEEP, not IDLE — the cell would measure the charging case again" >&2; exit 1; }
    echo "deep idle: $DEEP (framework sees battery; cable may stay in)"
elif [ "$DOZE" = "natural" ]; then
    # A USB link is a charger. Refuse rather than quietly measure the plugged
    # case, which is exactly how the first six cells came to be Doze-exempt.
    case "${ANDROID_SERIAL:-}" in
        *:*) : ;;
        *) echo "FATAL: DOZE=natural needs adb over Wi-Fi — set DEVICE to host:port" >&2; exit 1 ;;
    esac
    PLUGGED="$(adb shell dumpsys battery | awk -F': ' '
        /AC powered/{ac=$2} /USB powered/{usb=$2} /Wireless powered/{wl=$2}
        END {print ac usb wl}' | tr -d '\r')"
    case "$PLUGGED" in
        *true*) echo "FATAL: device is still on a charger ($PLUGGED) — unplug it; Doze will not start" >&2; exit 1 ;;
    esac
    adb shell input keyevent KEYCODE_SLEEP
    echo "waiting for deep idle (up to ${IDLE_TIMEOUT}s, polling every ${IDLE_POLL}s)"
    echo "leave the phone still — motion restarts the countdown"
    WAITED=0
    while :; do
        DEEP="$(adb shell dumpsys deviceidle get deep 2>/dev/null | tr -d '\r')"
        [ "$DEEP" = "IDLE" ] && break
        [ "$WAITED" -ge "$IDLE_TIMEOUT" ] \
            && { echo "FATAL: still $DEEP after ${WAITED}s — nothing was measured" >&2; exit 1; }
        echo "  ${WAITED}s: deep=$DEEP"
        sleep "$IDLE_POLL"
        WAITED=$((WAITED + IDLE_POLL))
    done
    echo "deep idle reached after ${WAITED}s, unforced, on battery"
fi

adb logcat -c
{
    echo "# device: $MODEL, Android $RELEASE (API $SDK)"
    echo "# bucket: $BUCKET  mode: $MODE  count: $COUNT  interval: ${INTERVAL}s  hold: ${HOLD}s  doze: $DOZE"
    echo "# power: $(power_sample start)"
} > "$LOG"
LOGCAT_CHILD="$(mktemp -t bucketlogcat)"
logcat_forever &
LOGCAT_PID=$!
# Kill the supervisor first, or it respawns the child it is watching.
stop_logcat() {
    kill "$LOGCAT_PID" 2>/dev/null || true
    [ -s "$LOGCAT_CHILD" ] && kill "$(cat "$LOGCAT_CHILD")" 2>/dev/null || true
    rm -f "$LOGCAT_CHILD"
}
if [ "$DOZE" = "1" ]; then
    trap 'stop_logcat; release_overrides' EXIT
else
    trap 'stop_logcat' EXIT
fi

: > "$SENDS"
: > "$POWER"
power_sample start >> "$POWER"
MISSES=0
for i in $(seq 1 "$COUNT"); do
    SEQ=$(printf "%s-%s-%03d" "$BUCKET" "$MODE" "$i")
    if [ "$MODE" = "visible" ]; then
        DATA=$(jq -n --arg s "$SEQ" \
            '{spike_seq: $s, push_notification_title: "Spike", push_notification_body: $s}')
    else
        DATA=$(jq -n --arg s "$SEQ" '{spike_seq: $s}')
    fi
    if [ -n "$PAYLOAD" ]; then
        DATA=$(jq -n --argjson d "$DATA" --arg p "$PAYLOAD" '$d + {flipcash_payload: $p}')
    fi
    if [ "$DOZE" = "1" ]; then
        # Re-assert rather than assert-once: step-idle can advance to a
        # maintenance window on its own, and force-idle is idempotent.
        adb shell dumpsys deviceidle force-idle >/dev/null 2>&1 || true
    fi
    if power_sample "pre-$SEQ" >> "$POWER"; then
        MISSES=0
    else
        MISSES=$((MISSES + 1))
        echo "device unreachable at $SEQ (${MISSES} in a row)" >&2
        [ "$MISSES" -ge 3 ] && {
            echo "FATAL: device unreachable across $MISSES consecutive sends — this cell is incomplete" >&2
            exit 1
        }
    fi
    echo "$(date +%s000) $SEQ" >> "$SENDS"
    "$REPO/scripts/fcm.sh" "$TOKEN" "$DATA" > /dev/null 2>&1 \
        && echo "sent $SEQ" || echo "SEND FAILED $SEQ"
    [ "$i" -lt "$COUNT" ] && sleep "$INTERVAL"
done

echo "sends done; holding ${HOLD}s for stragglers"
sleep "$HOLD"
power_sample end >> "$POWER"
kill $LOGCAT_PID 2>/dev/null || true

# The device can vanish mid-run — a cable, a reboot, a sleeping host. Say so,
# because the log then just stops and the cell reads as a partial delivery
# failure instead of an interrupted capture.
adb_try shell true >/dev/null \
    || { echo "FATAL: device disappeared during the run — this cell is incomplete" >&2; exit 1; }
echo "log:   $LOG"
echo "sends: $SENDS"
echo "power: $POWER"
