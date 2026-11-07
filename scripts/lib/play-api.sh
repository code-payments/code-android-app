#!/usr/bin/env bash
# scripts/lib/play-api.sh — sourced helpers for the Google Play Developer API.
#
# play_access_token <service-account.json>
#   Prints an OAuth access token with the androidpublisher scope. Needs jq, openssl, curl.

play_access_token() {
  local sa_path="$1"
  local now exp client_email private_key header payload sig access

  now=$(date +%s); exp=$((now + 3600))
  client_email=$(jq -r .client_email "$sa_path")
  private_key=$(jq -r .private_key "$sa_path")

  header=$(echo -n '{"alg":"RS256","typ":"JWT"}' | base64 | tr '+/' '-_' | tr -d '=\n')
  payload=$(jq -nc \
    --arg iss "$client_email" \
    --arg scope "https://www.googleapis.com/auth/androidpublisher" \
    --argjson iat "$now" --argjson exp "$exp" \
    '{iss:$iss, scope:$scope, aud:"https://oauth2.googleapis.com/token", iat:$iat, exp:$exp}' \
    | base64 | tr '+/' '-_' | tr -d '=\n')
  sig=$(printf '%s.%s' "$header" "$payload" \
    | openssl dgst -sha256 -sign <(echo "$private_key") \
    | base64 | tr '+/' '-_' | tr -d '=\n')
  access=$(curl -s -X POST https://oauth2.googleapis.com/token \
    -d "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" \
    -d "assertion=$header.$payload.$sig" | jq -r .access_token)

  if [ -z "$access" ] || [ "$access" = "null" ]; then
    echo "Token request failed" >&2
    return 1
  fi
  echo "$access"
}
