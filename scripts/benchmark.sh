#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "Usage: scripts/benchmark.sh [-t TestClass] [-d SERIAL] [seed phrase words...]"
    echo ""
    echo "  -t CLASS   Run a specific test class (e.g. StartupBenchmark, BaselineProfileGenerator)"
    echo "  -d SERIAL  Target a specific device (adb serial or emulator name)"
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

GRADLE_ARGS=(
    :apps:flipcash:benchmark:connectedBenchmarkAndroidTest
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

./gradlew "${GRADLE_ARGS[@]}"

# Print results if available
RESULTS_DIR="apps/flipcash/benchmark/build/outputs/connected_android_test_additional_output"
BENCHMARK_JSON=$(find "$RESULTS_DIR" -name "*benchmarkData.json" 2>/dev/null | head -1)

if [[ -n "$BENCHMARK_JSON" ]]; then
    echo ""
    echo "=== Benchmark Results ==="
    python3 -c "
import json, sys
data = json.load(open('$BENCHMARK_JSON'))
for b in data.get('benchmarks', []):
    m = b['metrics']
    startup = m.get('startupMs', {})
    fd = m.get('fullyDrawnMs', {})
    print(f\"{b['name']}:\")
    if startup: print(f\"  TTID:  min={startup['minimum']:.0f}ms  median={startup['median']:.0f}ms  max={startup['maximum']:.0f}ms\")
    if fd: print(f\"  TTFD:  min={fd['minimum']:.0f}ms  median={fd['median']:.0f}ms  max={fd['maximum']:.0f}ms\")
" 2>/dev/null || cat "$BENCHMARK_JSON"
fi
