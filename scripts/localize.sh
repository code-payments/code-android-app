#Prerequisites:
#brew install jq
#Set format argument: Android - "xml", iOS - "strings"
#Set destination path

bundle_url=$(curl --request POST \
     --url https://api.lokalise.com/api2/projects/"${1}":branch/files/download \
     --header 'Accept: application/json' \
     --header 'Content-Type: application/json' \
     --header "X-Api-Token: ${2}" \
     --data '{"format":"xml"}' | jq -r '.bundle_url')

rm -r strings_temp
mkdir strings_temp
cd strings_temp || exit
curl -sS "$bundle_url" > file.zip
unzip file.zip
rm file.zip
node scripts/clean-strings.js
cp -r strings_temp/* app/src/main/res/
rm -rf strings_temp