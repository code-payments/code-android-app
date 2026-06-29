#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"                                                                                                                                                                                                              
                                                                                                                                                                                                                                                                        
source "$SCRIPT_DIR/../.env"

ACCESS_TOKEN=$(python3 << EOF
from google.oauth2 import service_account
from google.auth.transport.requests import Request

credentials = service_account.Credentials.from_service_account_file(
    "$SERVER_SERVICE_ACCOUNT_KEY_JSON",
    scopes=["https://www.googleapis.com/auth/firebase.messaging"]
)
credentials.refresh(Request())
print(credentials.token)
EOF
)


DEVICE_TOKEN=$1

# Access the JSON object passed as the first argument
contents="$2"

JSON_PAYLOAD=$(jq -n \
  --arg token "$DEVICE_TOKEN" \
  --argjson data "$contents" \
  '{
    message: {
      token: $token,
      android: {
        priority: "high",
        data: $data
      }
    }
  }')

curl -X POST \
  "https://fcm.googleapis.com/v1/projects/${FCM_PROJECT_ID}/messages:send" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "$JSON_PAYLOAD"