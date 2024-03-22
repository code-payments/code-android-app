#!/bin/bash

key="$1"
token="$2"

# Access the JSON object passed as the first argument
contents="$3"

echo $contents

data=$(cat <<-END
{
  "registration_ids":["${token}"],
  "data": $contents
}
END
)

  echo $data

curl -i -H 'Content-type: application/json' \
  -H "Authorization: key=$key" \
  -XPOST https://fcm.googleapis.com/fcm/send \
  -d "$data"