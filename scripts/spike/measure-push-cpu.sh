#!/usr/bin/env bash
# Measure what one push costs the app in CPU, and derive how many fit in the
# background CPU budget.
#
# Usage: scripts/spike/measure-push-cpu.sh <device_token> [count] [settle_s]
#   count:    pushes to measure (default 10)
#   settle_s: seconds to let each push's work finish before sampling (default 120)
#
# Why /proc rather than the log-line regression in the results document: the
# killer in ActivityManager reads utime+stime out of /proc via ProcessCpuTracker
# and compares the delta against 2% of a 300 s window. Sampling the same two
# fields measures the quantity that decides the kill, instead of inferring it
# from how many lines the app logged inside a window that happened to end in
# one. The regression put a push at ~6.7 s; this is the direct reading.
#
# The measurement needs three conditions, none of which is deep Doze:
#   - The framework must see battery. The check is gated on it, and a charging
#     device is never killed however much CPU it burns. `dumpsys battery unplug`
#     is enough; the cable can stay in.
#   - The screen must be off and the process cached (oom_score_adj >= 900). The
#     observed kills were at adj 905. A process that is top-sleeping or in the
#     previous slot is not a candidate and is also not frozen, so it keeps
#     paying timer work the cached case does not.
#   - Each push needs to finish. The captured bursts ran ~90 s from wake to the
#     last line, so a settle window shorter than that measures part of a push.
# Deep Doze is deliberately not required. It adds the Wi-Fi adb drops that took
# down two earlier cells and it does not change what the killer reads.
#
# The run starts with one quiet window of the same length and no push, which
# gives the idle term. Budget arithmetic needs both: the 2% allowance is
# 6000 ms per 300 s window, and idle CPU spends part of it before any push
# arrives.
#
# A pid change between two samples means the process died inside that window —
# usually the killer, which is the outcome being sized. The sample is dropped
# from the mean and reported separately, because a killed process stops
# accruing partway through work it had not finished.
#
# Set DEVICE to an adb serial when more than one device is attached. PAYLOAD is
# the same base64 flipcash.push.v1.Payload the bucket runner sends, and the
# default `IAU=` is category=CONTACT_JOIN, which plans RefreshFeed +
# SyncContacts.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"

TOKEN="${1:?usage: measure-push-cpu.sh <device_token> [count] [settle_s]}"
COUNT="${2:-10}"
SETTLE="${3:-120}"
PAYLOAD="${PAYLOAD:-IAU=}"
PACKAGE="com.flipcash.app.android"

if [ -n "${DEVICE:-}" ]; then
    export ANDROID_SERIAL="$DEVICE"
fi

adb shell true >/dev/null 2>&1 \
    || { echo "FATAL: no device (${ANDROID_SERIAL:-default}) — nothing was measured" >&2; exit 1; }

STAMP="$(date +%Y%m%dT%H%M%S)"
OUT_DIR="$REPO/docs/spikes/raw"
mkdir -p "$OUT_DIR"
OUT="$OUT_DIR/push-cpu-${STAMP}.cpu"

MODEL="$(adb shell getprop ro.product.model | tr -d '\r')"
SDK="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
HZ="$(adb shell getconf CLK_TCK | tr -d '\r')"

restore() {
    adb shell "dumpsys battery reset" >/dev/null 2>&1 || true
}
trap restore EXIT

adb shell "dumpsys battery unplug" >/dev/null

# HOME before SLEEP, and then wait for the process to actually fall out of the
# top slot. Turning the screen off on its own is not enough: the app stays the
# top of its task as `top-sleeping` at adj 0, which is neither cached nor frozen
# and keeps paying the timer work the cached case does not. The first run of this
# script measured that state for eleven windows without noticing, because it
# recorded the adj columns instead of gating on them.
# WAKEUP first: HOME sent to a sleeping device does nothing, which is how the
# first run stayed top-sleeping through the HOME it thought had backgrounded it.
adb shell "input keyevent KEYCODE_WAKEUP" >/dev/null || true
sleep 2
adb shell "input keyevent KEYCODE_HOME" >/dev/null || true
sleep 3
adb shell "input keyevent KEYCODE_SLEEP" >/dev/null || true

adj_now() { adb shell "cat /proc/\$(pidof $PACKAGE)/oom_score_adj" 2>/dev/null | tr -d '\r'; }

waited=0
until adj="$(adj_now)"; [ -n "$adj" ] && [ "$adj" -ge 900 ] 2>/dev/null; do
    [ "$waited" -ge 300 ] && {
        echo "FATAL: $PACKAGE never reached adj >= 900 (last: ${adj:-unknown}) — nothing was measured" >&2
        exit 1
    }
    sleep 5
    waited=$(( waited + 5 ))
done
echo "cached at adj $adj after ${waited} s" >&2

# One round trip returns pid, cached-ness and the two counters together. Read
# apart they can straddle a process death and produce a negative delta.
sample() {
    # utime and stime are fields 14 and 15 of /proc/pid/stat. The comm field is
    # parenthesised and can hold spaces, so everything up to the closing paren
    # is cut before counting: state becomes field 1, which puts utime at 12.
    adb shell "pid=\$(pidof $PACKAGE); \
        if [ -z \"\$pid\" ]; then echo 'dead 0 0 0'; else \
          echo \"\$pid \$(cat /proc/\$pid/oom_score_adj) \
            \$(sed 's/.*) //' /proc/\$pid/stat | awk '{print \$12, \$13}')\"; fi" | tr -d '\r'
}

{
    echo "# model=$MODEL sdk=$SDK clk_tck=$HZ settle_s=$SETTLE payload=$PAYLOAD"
    echo "# window pid_before adj_before pid_after adj_after ticks ms"
} > "$OUT"

# Ticks are per-core sums across the process's threads, so the value is CPU
# time and not wall time; that is what the killer compares too.
ticks_to_ms() { echo "$(( $1 * 1000 / HZ ))"; }

measure() {
    local label="$1" send="$2"
    local before after p0 a0 u0 s0 p1 a1 u1 s1 ticks
    before="$(sample)"; read -r p0 a0 u0 s0 <<< "$before"
    [ "$p0" = "dead" ] && { echo "$label - - - - - dead_before" >> "$OUT"; return; }
    if [ "$send" = "yes" ]; then
        "$REPO/scripts/fcm.sh" "$TOKEN" \
            "{\"spike_seq\":\"cpu-$label\",\"flipcash_payload\":\"$PAYLOAD\"}" >/dev/null
    fi
    sleep "$SETTLE"
    after="$(sample)"; read -r p1 a1 u1 s1 <<< "$after"
    if [ "$p1" = "dead" ] || [ "$p1" != "$p0" ]; then
        echo "$label $p0 $a0 ${p1} ${a1} - died" >> "$OUT"
        return
    fi
    ticks=$(( (u1 + s1) - (u0 + s0) ))
    echo "$label $p0 $a0 $p1 $a1 $ticks $(ticks_to_ms "$ticks")" >> "$OUT"
}

measure "idle" no
for i in $(seq -f '%03g' 1 "$COUNT"); do
    # A kill ends the cell. Without this the loop keeps calling measure(), which
    # returns immediately on a dead process, so the remaining windows land in the
    # file as `dead_before` in a couple of seconds and the run looks like it
    # completed its full count. Stop and say which push was the last one.
    if [ -z "$(adb shell "pidof $PACKAGE" 2>/dev/null | tr -d '\r')" ]; then
        echo "process gone before window $i - cell ends here" >&2
        break
    fi
    measure "$i" yes
done

# 2% of a 300 s window is 6000 ms. Subtract what idle spends over the same
# window before dividing the remainder by the cost of one push: a budget that
# ignores the idle term overstates how many pushes fit.
python3 - "$OUT" "$SETTLE" <<'PY'
import statistics, sys
path, settle = sys.argv[1], float(sys.argv[2])
rows = [l.split() for l in open(path) if not l.startswith('#')]

# The window has to *start* cached. That is the state the killer's budget applies
# to, and the state a push has to thaw the process out of, so it is what decides
# whether the reading is the cold cost or a warm one. It cannot also *end* cached:
# handling a push promotes the process into the previous-app slot at adj 700,
# which it holds for about two minutes after the last activity. Requiring both
# ends discards every push window by construction.
def cached(r):
    return r[2].isdigit() and int(r[2]) >= 900

complete = [r for r in rows if r[-1].isdigit()]
uncached = [r[0] for r in complete if not cached(r)]
idle = [r for r in complete if r[0] == 'idle' and cached(r)]
push = [int(r[-1]) for r in complete if r[0] != 'idle' and cached(r)]
died = [r[0] for r in rows if r[-1] == 'died']
if uncached:
    print(f"dropped:  {len(uncached)} window(s) that did not start cached: {uncached}")
if not push:
    print(f"no complete push samples; {len(died)} died: {died}"); sys.exit(0)
idle_ms = int(idle[0][-1]) if idle else 0
idle_300 = idle_ms * 300 / settle
mean, med = statistics.mean(push), statistics.median(push)
print(f"samples: n={len(push)} died={len(died)} {died if died else ''}")
print(f"idle:    {idle_ms} ms over {settle:.0f} s -> {idle_300:.0f} ms per 300 s window")
print(f"push:    mean {mean:.0f} ms  median {med:.0f} ms  min {min(push)}  max {max(push)}")
budget = 6000 - idle_300
print(f"budget:  (6000 - {idle_300:.0f}) / {mean:.0f} = {budget/mean:.2f} pushes per 5 min")
PY

echo "raw: $OUT"
