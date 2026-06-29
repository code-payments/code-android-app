#!/bin/bash
# Sends a chat push notification for a phone number contact.
# Usage: ./scripts/fcm-chat-phone.sh <device_token> <phone_number>
#
# Phone number must be E.164 format, e.g. +15551234567

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

DEVICE_TOKEN=$1
PHONE=$2

if [ -z "$DEVICE_TOKEN" ] || [ -z "$PHONE" ]; then
  echo "Usage: $0 <device_token> <phone_number>"
  echo "  phone_number: E.164 format, e.g. +15551234567"
  exit 1
fi

encode_payload() {
    local phone="$1"
    python3 -c "
import base64
def varint(v):
    r = b''
    while v > 0x7F:
        r += bytes([(v & 0x7F) | 0x80]); v >>= 7
    return r + bytes([v & 0x7F])
def field_bytes(num, data):
    return varint((num << 3) | 2) + varint(len(data)) + data
def field_varint(num, val):
    return varint((num << 3) | 0) + varint(val)
phone = b'$phone'
# Payload.navigation (field 1) > Navigation.chat_contact_phone_number (field 3) > PhoneNumber.value (field 1)
nav = field_bytes(1, field_bytes(3, field_bytes(1, phone)))
# Payload.category = CHAT (enum value 4)
cat = field_varint(4, 4)
# Payload.group_key
gk = field_bytes(5, b'chat_$phone')
print(base64.b64encode(nav + cat + gk).decode())
"
}

PAYLOAD=$(encode_payload "$PHONE")

JSON_PAYLOAD=$(jq -n \
  --arg payload "$PAYLOAD" \
  --arg title "Contact Joined!" \
  --arg body "Tap to send them some cash" \
  '{
    flipcash_payload: $payload,
    push_notification_title: $title,
    push_notification_body: $body
  }')

"$SCRIPT_DIR/fcm.sh" "$DEVICE_TOKEN" "$JSON_PAYLOAD"
