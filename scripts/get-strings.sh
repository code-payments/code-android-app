#!/bin/sh
#Prerequisites: 
#brew install jq
#chmod +x get-strings.sh
#Set format argument: Android - "xml", iOS - "strings"
#Set destination path

bundle_url=$(curl --request POST \
     --url https://api.lokalise.com/api2/projects/2386753963226d4509b3c1.95845692:branch/files/download \
     --header 'Accept: application/json' \
     --header 'Content-Type: application/json' \
     --header "X-Api-Token: ${1}" \
     --data '{"format":"xml"}' | jq -r '.bundle_url')

rm -r /tmp/strings_temp
mkdir /tmp/strings_temp
cd /tmp/strings_temp
curl -sS $bundle_url > file.zip
unzip file.zip
rm file.zip
node /Users/nickcode/Desktop/Code/code-android/scripts/clean-strings.js
cp -r /tmp/strings_temp/* /Users/nickcode/Desktop/Code/code-android/app/src/main/res/
rm -r /tmp/strings_temp