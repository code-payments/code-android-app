#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

DEVICE_TOKEN=$1
MINT=$2

encode_payload() {
    local mint="$1"
    python3 -c "
import base64, base58
def varint(v):
    r = b''
    while v > 0x7F:
        r += bytes([(v & 0x7F) | 0x80]); v >>= 7
    return r + bytes([v & 0x7F])
def field(num, data):
    return varint((num << 3) | 2) + varint(len(data)) + data
mint = base58.b58decode('$mint')
assert len(mint) == 32, f'Expected 32-byte key, got {len(mint)}'
print(base64.b64encode(field(1, field(1, field(1, mint)))).decode())
"
}

PAYLOAD=$(encode_payload "$MINT")

JSON_PAYLOAD=$(jq -n \
  --arg payload "$PAYLOAD" \
  --arg title "Purchase Successful" \
  --arg body "You received 100.0 of some token" \
  '{
    flipcash_payload: $payload,
    push_notification_title: $title,
    push_notification_body: $body
  }')

"$SCRIPT_DIR/fcm.sh" "$DEVICE_TOKEN" "$JSON_PAYLOAD"