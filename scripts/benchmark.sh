#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "Usage: scripts/benchmark.sh [-t TestClass] [-d SERIAL] [seed phrase words...]"
    echo ""
    echo "  -t CLASS   Run a specific test class (e.g. StartupBenchmark, BaselineProfileGenerator)"
    echo "  -d SERIAL  Target a specific device (adb serial or emulator name)"
    echo ""
    echo "  BaselineProfileGenerator drives the app as a real user: it sends a chat message"
    echo "  and pulls a bill on the signed-in account. Run it on a throwaway account only,"
    echo "  and detach every other device first -- the connected task installs on each one."
    echo "  seed ...   Seed phrase for authenticated user journeys (overrides .env)"
    echo ""
    echo "  SEED_PHRASE can also be set in .env or .env.local"
    echo ""
    echo "Examples:"
    echo "  scripts/benchmark.sh                              # all benchmarks, seed from .env"
    echo "  scripts/benchmark.sh -t StartupBenchmark          # startup only, no auth"
    echo "  scripts/benchmark.sh -d emulator-5554 -t BaselineProfileGenerator"
    exit 1
}

# Source .env files for SEED_PHRASE and other config
for envfile in .env .env.local; do
    [[ -f "$envfile" ]] && set -a && source "$envfile" && set +a
done

TEST_CLASS=""
DEVICE_SERIAL=""

while getopts "t:d:h" opt; do
    case $opt in
        t) TEST_CLASS="$OPTARG" ;;
        d) DEVICE_SERIAL="$OPTARG" ;;
        h) usage ;;
        *) usage ;;
    esac
done
shift $((OPTIND - 1))

# CLI args override .env
if [[ $# -gt 0 ]]; then
    SEED_PHRASE="$*"
fi

# benchmarkRelease is the variant to measure: nonMinifiedRelease merges only the *library* baseline
# profiles, so it carries none of the app's own rules and reads as a no-profile build.
GRADLE_ARGS=(
    :apps:flipcash:benchmark:connectedBenchmarkReleaseAndroidTest
    --no-configuration-cache
    -x lint
)

if [[ -n "$TEST_CLASS" ]]; then
    GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.class=com.flipcash.benchmark.$TEST_CLASS")
fi

if [[ -n "${SEED_PHRASE:-}" ]]; then
    GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.SEED_PHRASE=$SEED_PHRASE")
fi

# Target a specific device so benchmarks don't run on multiple connected devices
if [[ -n "$DEVICE_SERIAL" ]]; then
    export ANDROID_SERIAL="$DEVICE_SERIAL"
fi

# A benchmark against two devices at once is two sets of numbers from different hardware, and the
# generator's journeys would run twice against the same account. Refuse rather than guess which one
# was meant -- ANDROID_SERIAL is not a reliable filter for the connected task.
ATTACHED=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [[ $(wc -l <<< "$ATTACHED") -gt 1 ]]; then
    echo "More than one device is attached:" >&2
    sed 's/^/  /' <<< "$ATTACHED" >&2
    echo "Detach all but the one you are measuring." >&2
    exit 1
fi
if [[ -n "$DEVICE_SERIAL" && "$ATTACHED" != "$DEVICE_SERIAL" ]]; then
    echo "-d $DEVICE_SERIAL was given but the attached device is ${ATTACHED:-none}." >&2
    exit 1
fi

./gradlew "${GRADLE_ARGS[@]}"

# Print results if available
RESULTS_DIR="apps/flipcash/benchmark/build/outputs/connected_android_test_additional_output"
BENCHMARK_JSON=$(find "$RESULTS_DIR" -name "*benchmarkData.json" 2>/dev/null | head -1)

if [[ -n "$BENCHMARK_JSON" ]]; then
    echo ""
    echo "=== Benchmark Results ==="
    # Two pairs, from two metrics that disagree by a couple of hundred ms because they are timed
    # from different origins: StartupTimingMetric reads the trace, the legacy one scrapes the
    # ActivityManager logcat lines. Print both rather than pick.
    python3 -c "
import json
data = json.load(open('$BENCHMARK_JSON'))
rows = [
    ('TTID (trace) ', 'timeToInitialDisplayMs'),
    ('TTFD (trace) ', 'timeToFullDisplayMs'),
    ('TTID (logcat)', 'startupMs'),
    ('TTFD (logcat)', 'fullyDrawnMs'),
]
for b in data.get('benchmarks', []):
    m = b['metrics']
    print(f\"{b['name']}:\")
    for label, key in rows:
        v = m.get(key)
        if not v: continue
        print(f\"  {label}  min={v['minimum']:.0f}ms  median={v['median']:.0f}ms  max={v['maximum']:.0f}ms\")
" 2>/dev/null || cat "$BENCHMARK_JSON"
fi
