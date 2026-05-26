#!/bin/bash
# Sends multiple buy-token notifications to verify on-device grouping.
# Usage: ./scripts/fcm-buy-token-group-test.sh <device_token> [mint]
#
# Default mint is 54ggcQ23uen5b9QXMAns99MQNTKn7iyzq4wvCW6e8r25 (Jeffy).
# Sends 3 notifications in quick succession — check the notification shade
# to confirm they appear bundled under a single group.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

DEVICE_TOKEN=$1
MINT=${2:-"54ggcQ23uen5b9QXMAns99MQNTKn7iyzq4wvCW6e8r25"}

if [ -z "$DEVICE_TOKEN" ]; then
  echo "Usage: $0 <device_token> [mint]"
  exit 1
fi

encode_payload() {
    local mint="$1"
    local group_key="$2"
    python3 -c "
import base64, base58
def varint(v):
    r = b''
    while v > 0x7F:
        r += bytes([(v & 0x7F) | 0x80]); v >>= 7
    return r + bytes([v & 0x7F])
def field_bytes(num, data):
    return varint((num << 3) | 2) + varint(len(data)) + data
def field_varint(num, val):
    return varint((num << 3) | 0) + varint(val)
mint = base58.b58decode('$mint')
assert len(mint) == 32, f'Expected 32-byte key, got {len(mint)}'
# field 1: navigation (Navigation message with currency_info)
nav = field_bytes(1, field_bytes(1, field_bytes(1, mint)))
# field 4: category = BUY_SELL (enum value 2)
cat = field_varint(4, 2)
# field 5: group_key
gk = field_bytes(5, b'$group_key')
print(base64.b64encode(nav + cat + gk).decode())
"
}

GROUP_KEY="buy_sell_${MINT}"
PAYLOAD=$(encode_payload "$MINT" "$GROUP_KEY")

notifications=(
  "Purchase Successful|You received 100.0 JEFFY"
  "Purchase Successful|You received 250.0 JEFFY"
  "Purchase Successful|You received 50.0 JEFFY"
)

echo "Sending ${#notifications[@]} buy-token notifications to test grouping..."
echo ""

for entry in "${notifications[@]}"; do
  IFS='|' read -r title body <<< "$entry"

  JSON_PAYLOAD=$(jq -n \
    --arg payload "$PAYLOAD" \
    --arg title "$title" \
    --arg body "$body" \
    '{
      flipcash_payload: $payload,
      push_notification_title: $title,
      push_notification_body: $body
    }')

  echo "→ Sending: $title — $body"
  "$SCRIPT_DIR/fcm.sh" "$DEVICE_TOKEN" "$JSON_PAYLOAD"
  echo ""
  sleep 1
done

echo "Done. Check the notification shade — all 3 should be grouped together."
