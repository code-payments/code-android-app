#!/usr/bin/env bash
#
# screenshot_test.sh — Maestro screenshot capture and comparison
#
# Usage:
#   ./screenshot_test.sh baseline                          Capture baselines on current emulator
#   ./screenshot_test.sh capture --device pixel_9           Capture screenshots for a device
#   ./screenshot_test.sh compare --device pixel_9           Compare against baselines
#   ./screenshot_test.sh full --device pixel_9              Capture + compare
#
# Options:
#   --device <name>       Device profile (pixel_9_pro_fold|pixel_9|pixel_9_pro_xl|pixel_4a)
#   --threshold <0-1>     SSIM pass/fail threshold (default: 0.85)
#   --group <name>        Run only a specific group file (e.g., 01_pre_login)
#
# Environment:
#   LOGIN_DEEPLINK        Required for post-login flows
#   SEED_PHRASE           Required if using manual login flows
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCREENSHOTS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
MAESTRO_DIR="$(cd "$SCREENSHOTS_DIR/.." && pwd)"
BASELINES_DIR="$SCREENSHOTS_DIR/baselines"
RESULTS_DIR="$SCREENSHOTS_DIR/results"
DIFFS_DIR="$SCREENSHOTS_DIR/diffs"

DEFAULT_THRESHOLD=0.60
# Threshold bounds for auto-ratcheting based on device divergence
THRESHOLD_MAX=0.90   # same device class
THRESHOLD_MIN=0.50   # very different screen (e.g. Fold → phone)

# Per-screen threshold overrides (screen name → threshold).
# Screens with device-specific content (keyboards, dynamic graphics)
# need a lower bar so cross-device runs aren't perpetually red.
declare -A SCREEN_THRESHOLDS=(
  ["cash_bill.png"]=0.30                    # animated bill graphic varies by aspect ratio
  ["withdraw_destination_filled.png"]=0.30  # device-specific keyboard visible
)

# ──────────────────────────────────────────────
# Helpers
# ──────────────────────────────────────────────

usage() {
  sed -n '3,15p' "$0" | sed 's/^# \?//'
  exit 1
}

info()  { printf "\033[1;34m→\033[0m %s\n" "$*"; }
ok()    { printf "\033[1;32m✓\033[0m %s\n" "$*"; }
fail()  { printf "\033[1;31m✗\033[0m %s\n" "$*"; }
warn()  { printf "\033[1;33m!\033[0m %s\n" "$*"; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    fail "Required command not found: $1"
    exit 1
  }
}

# ──────────────────────────────────────────────
# Device info & threshold ratcheting
# ──────────────────────────────────────────────

# Query connected device for resolution and DPI
get_device_info() {
  local size dpi width height
  size=$(adb shell wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+' | tail -1)
  dpi=$(adb shell wm density 2>/dev/null | grep -oE '[0-9]+' | tail -1)
  width="${size%x*}"
  height="${size#*x}"
  echo "${width:-0} ${height:-0} ${dpi:-0}"
}

# Save device info to a file: width height dpi
save_device_info() {
  local dest="$1"
  local device_info
  device_info=$(get_device_info)
  echo "$device_info" > "$dest"
  info "Device info: $(echo "$device_info" | awk '{printf "%sx%s @ %sdpi", $1, $2, $3}')"
}

# Compute an auto-adjusted threshold based on how much the test device
# diverges from the baseline device (resolution + DPI).
#   divergence=0  → THRESHOLD_MAX (same screen)
#   divergence=1+ → THRESHOLD_MIN (very different)
compute_threshold() {
  local baseline_info="$BASELINES_DIR/device_info.txt"
  local device_info="$1"

  # Fall back to default if device info is missing
  if [ ! -f "$baseline_info" ] || [ ! -f "$device_info" ]; then
    echo "$DEFAULT_THRESHOLD"
    return
  fi

  local bw bh bdpi dw dh ddpi
  read -r bw bh bdpi < "$baseline_info"
  read -r dw dh ddpi < "$device_info"

  # Guard against zeros
  if [ "${bw:-0}" -eq 0 ] || [ "${bdpi:-0}" -eq 0 ]; then
    echo "$DEFAULT_THRESHOLD"
    return
  fi

  # Resolution divergence: ratio of total pixel counts
  # DPI divergence: ratio of density values
  # Combined divergence drives the threshold down from MAX toward MIN
  echo "$bw $bh $bdpi $dw $dh $ddpi" | awk -v tmax="$THRESHOLD_MAX" -v tmin="$THRESHOLD_MIN" '{
    bpx = $1 * $2; dpx = $4 * $5
    if (bpx == 0) { print tmax; exit }
    res_div = (dpx > bpx) ? (dpx/bpx - 1) : (bpx/dpx - 1)
    dpi_div = ($6 > $3) ? ($6/$3 - 1) : ($3/$6 - 1)
    div = (res_div + dpi_div) / 2
    if (div > 1) div = 1
    t = tmax - div * (tmax - tmin)
    printf "%.2f\n", t
  }'
}

# ──────────────────────────────────────────────
# Capture
# ──────────────────────────────────────────────

run_capture() {
  local output_dir="$1"
  local group="${2:-}"

  mkdir -p "$output_dir"

  local flow_file
  if [ -n "$group" ]; then
    flow_file="$SCREENSHOTS_DIR/groups/${group}.yaml"
    if [ ! -f "$flow_file" ]; then
      fail "Group not found: $flow_file"
      exit 1
    fi
  else
    flow_file="$SCREENSHOTS_DIR/capture_all.yaml"
  fi

  info "Running Maestro capture: $(basename "$flow_file")"
  info "Output: $output_dir"

  # Build env args as an array to preserve quoting (values may contain spaces)
  local env_args=()
  [ -n "${LOGIN_DEEPLINK:-}" ] && env_args+=(-e "LOGIN_DEEPLINK=$LOGIN_DEEPLINK")
  [ -n "${SEED_PHRASE:-}" ] && env_args+=(-e "SEED_PHRASE=$SEED_PHRASE")

  # Run maestro from the output dir so takeScreenshot saves PNGs there
  # (takeScreenshot writes to CWD, not --debug-output)
  (cd "$output_dir" && maestro test \
    ${env_args[@]+"${env_args[@]}"} \
    "$flow_file") || true

  # Count captured screenshots
  local count
  count=$(find "$output_dir" -maxdepth 1 -name "*.png" 2>/dev/null | wc -l | tr -d ' ')
  if [ "$count" -eq 0 ]; then
    fail "No screenshots captured. Check that the app is installed and flows are correct."
    fail "Try running a single flow directly: maestro test $flow_file"
    return 1
  fi
  # Record test device screen info for threshold ratcheting
  save_device_info "$output_dir/device_info.txt"

  ok "Captured $count screenshots to $output_dir"
}

# ──────────────────────────────────────────────
# Baseline
# ──────────────────────────────────────────────

save_baseline() {
  local group="${1:-}"
  local tmp_dir
  tmp_dir=$(mktemp -d)

  info "Capturing baselines..."
  run_capture "$tmp_dir" "$group"

  mkdir -p "$BASELINES_DIR"

  local count=0
  for png in "$tmp_dir"/*.png; do
    [ -f "$png" ] || continue
    cp "$png" "$BASELINES_DIR/"
    count=$((count + 1))
  done

  rm -rf "$tmp_dir"

  # Record baseline device screen info for threshold ratcheting
  save_device_info "$BASELINES_DIR/device_info.txt"

  ok "Saved $count baselines to $BASELINES_DIR"
}

# ──────────────────────────────────────────────
# Comparison
# ──────────────────────────────────────────────

normalize_resolution() {
  local source="$1"
  local target="$2"
  local baseline_ref="$3"

  local base_dims
  base_dims=$(magick identify -format "%wx%h" "$baseline_ref")
  local base_w="${base_dims%x*}"
  local base_h="${base_dims#*x}"

  magick "$source" \
    -resize "${base_w}x${base_h}" \
    -gravity center \
    -background black \
    -extent "${base_w}x${base_h}" \
    "$target"
}

mask_keyboard_region() {
  local input="$1"
  local output="$2"

  local dims
  dims=$(magick identify -format "%wx%h" "$input")
  local w="${dims%x*}"
  local h="${dims#*x}"

  # Mask bottom 40% of screen (keyboard area)
  local mask_y=$((h * 60 / 100))

  magick "$input" \
    -fill black \
    -draw "rectangle 0,$mask_y $w,$h" \
    "$output"
}

mask_camera_region() {
  local input="$1"
  local output="$2"

  local dims
  dims=$(magick identify -format "%wx%h" "$input")
  local w="${dims%x*}"
  local h="${dims#*x}"

  # Mask central 60% width x 50% height (camera preview area)
  local mask_w=$((w * 60 / 100))
  local mask_h=$((h * 50 / 100))
  local mask_x=$(( (w - mask_w) / 2 ))
  local mask_y=$(( (h - mask_h) / 4 ))

  magick "$input" \
    -fill black \
    -draw "rectangle $mask_x,$mask_y $((mask_x + mask_w)),$((mask_y + mask_h))" \
    "$output"
}

compare_ssim() {
  local baseline="$1"
  local candidate="$2"
  local diff_output="$3"

  # magick compare outputs SSIM to stderr
  magick compare -metric SSIM "$baseline" "$candidate" "$diff_output" 2>&1 || true
}

run_compare() {
  local device="$1"
  local threshold="${2:-}"

  require_cmd magick

  local device_results="$RESULTS_DIR/$device"
  local device_diffs="$DIFFS_DIR/$device"

  if [ ! -d "$BASELINES_DIR" ] || [ -z "$(ls "$BASELINES_DIR"/*.png 2>/dev/null)" ]; then
    fail "No baselines found. Run './screenshot_test.sh baseline' first."
    exit 1
  fi

  if [ ! -d "$device_results" ] || [ -z "$(ls "$device_results"/*.png 2>/dev/null)" ]; then
    fail "No results for device '$device'. Run capture first."
    exit 1
  fi

  # Auto-ratchet threshold from device info if not explicitly set
  if [ -z "$threshold" ]; then
    local device_info_file="$device_results/device_info.txt"
    threshold=$(compute_threshold "$device_info_file")
    info "Auto-threshold: $threshold (based on screen divergence from baseline)"
  fi

  mkdir -p "$device_diffs"

  local pass_count=0
  local fail_count=0
  local skip_count=0
  local total_ssim=0

  info "Comparing $device against baselines (threshold: $threshold)"
  echo ""
  printf "  %-35s %-8s %s\n" "SCREEN" "SSIM" "RESULT"
  printf "  %-35s %-8s %s\n" "──────────────────────────────────" "──────" "──────"

  for baseline_file in "$BASELINES_DIR"/*.png; do
    local name
    name=$(basename "$baseline_file")
    local candidate="$device_results/$name"

    if [ ! -f "$candidate" ]; then
      printf "  %-35s %-8s %s\n" "$name" "-" "SKIP"
      skip_count=$((skip_count + 1))
      continue
    fi

    local normalized="$device_diffs/${name%.png}_normalized.png"
    local diff_img="$device_diffs/${name%.png}_diff.png"

    # Normalize resolution
    normalize_resolution "$candidate" "$normalized" "$baseline_file"

    local compare_base="$baseline_file"
    local compare_cand="$normalized"

    # Special handling: mask volatile regions before comparison
    if [ "$name" = "scanner_screen.png" ]; then
      # Mask camera preview area
      compare_base="$device_diffs/scanner_baseline_masked.png"
      compare_cand="$device_diffs/scanner_candidate_masked.png"
      mask_camera_region "$baseline_file" "$compare_base"
      mask_camera_region "$normalized" "$compare_cand"
    elif [[ "$name" == withdraw_destination* ]]; then
      # Mask keyboard region (bottom 40% of screen)
      compare_base="$device_diffs/${name%.png}_baseline_masked.png"
      compare_cand="$device_diffs/${name%.png}_candidate_masked.png"
      mask_keyboard_region "$baseline_file" "$compare_base"
      mask_keyboard_region "$normalized" "$compare_cand"
    fi

    local ssim
    ssim=$(compare_ssim "$compare_base" "$compare_cand" "$diff_img")

    # Parse SSIM value from ImageMagick output.
    # Q16-HDRI builds emit: "52055.8 (0.794321)" — the real SSIM is
    # inside the parentheses.  Non-HDRI builds emit just "0.794321".
    local ssim_val
    if echo "$ssim" | grep -qE '\('; then
      # Extract the number inside parentheses: handles (0.794321) and (1)
      ssim_val=$(echo "$ssim" | grep -oE '\([0-9.]+\)' | tr -d '()')
    else
      ssim_val=$(echo "$ssim" | grep -oE '[0-9]+\.?[0-9]*' | head -1)
    fi
    ssim_val="${ssim_val:-0}"

    # Use per-screen override if defined, otherwise the global threshold
    local screen_threshold="${SCREEN_THRESHOLDS[$name]:-$threshold}"

    local result
    if (( $(echo "$ssim_val >= $screen_threshold" | bc -l 2>/dev/null || echo 0) )); then
      result="PASS"
      pass_count=$((pass_count + 1))
    else
      result="FAIL"
      fail_count=$((fail_count + 1))
    fi

    total_ssim=$(echo "$total_ssim + $ssim_val" | bc -l)

    local color
    [ "$result" = "PASS" ] && color="\033[32m" || color="\033[31m"
    printf "  %-35s %-8s ${color}%s\033[0m\n" "$name" "$ssim_val" "$result"
  done

  local total=$((pass_count + fail_count))
  local avg_ssim=0
  [ "$total" -gt 0 ] && avg_ssim=$(echo "scale=4; $total_ssim / $total" | bc -l)

  echo ""
  echo "  ──────────────────────────────────────────────────"
  printf "  PASS: %d | FAIL: %d | SKIP: %d | Avg SSIM: %s\n" \
    "$pass_count" "$fail_count" "$skip_count" "$avg_ssim"
  echo ""

  if [ "$fail_count" -gt 0 ]; then
    fail "$fail_count screenshot(s) below threshold ($threshold)"
    info "Diff images saved to: $device_diffs"
    return 1
  else
    ok "All screenshots passed (threshold: $threshold)"
    return 0
  fi
}

# ──────────────────────────────────────────────
# Main
# ──────────────────────────────────────────────

main() {
  require_cmd maestro

  local command="${1:-}"
  [ -z "$command" ] && usage
  shift

  local device=""
  local threshold=""
  local group=""

  while [ $# -gt 0 ]; do
    case "$1" in
      --device)   device="$2"; shift 2 ;;
      --threshold) threshold="$2"; shift 2 ;;
      --group)    group="$2"; shift 2 ;;
      *)          fail "Unknown option: $1"; usage ;;
    esac
  done

  case "$command" in
    baseline)
      save_baseline "$group"
      ;;

    capture)
      [ -z "$device" ] && { fail "--device required for capture"; usage; }
      run_capture "$RESULTS_DIR/$device" "$group"
      ;;

    compare)
      [ -z "$device" ] && { fail "--device required for compare"; usage; }
      run_compare "$device" "$threshold"
      ;;

    full)
      [ -z "$device" ] && { fail "--device required for full"; usage; }
      run_capture "$RESULTS_DIR/$device" "$group"
      run_compare "$device" "$threshold"
      ;;

    *)
      fail "Unknown command: $command"
      usage
      ;;
  esac
}

main "$@"
