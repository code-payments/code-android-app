#Prerequisites:
#brew install jq
#brew install xmlstarlet
#Set format argument: Android - "xml", iOS - "strings"
#Set destination path

root=$(pwd)

bundle_url_strings=$(curl --request POST \
     --url https://api.lokalise.com/api2/projects/"${1}":branch/files/download \
     --header 'Accept: application/json' \
     --header 'Content-Type: application/json' \
     --header "X-Api-Token: ${2}" \
     --data '{"format":"strings"}' | jq -r '.bundle_url')

 bundle_url_xml=$(curl --request POST \
      --url https://api.lokalise.com/api2/projects/"${1}":branch/files/download \
      --header 'Accept: application/json' \
      --header 'Content-Type: application/json' \
      --header "X-Api-Token: ${2}" \
      --data '{"format":"xml"}' | jq -r '.bundle_url')

function traverse_and_clean {
  for file in "$1"/*
  do
    dirName=${file%/*}
    if [ ! -d "${file}" ]; then
      fileName=$(basename "$file")
      if [[ "${fileName}" == "Localizable.strings" ]]; then
        echo "converting $file"
        cd "$dirName" || exit
        ruby "${root}"/scripts/convert-strings.rb $fileName
        rm "$fileName"
        # grab access key android value and replace iOS
        grab_android_string "subtitle.accessKeySnapshotDescriptionAndroid" "subtitle_accessKeySnapshotDescription"
        rm strings.xml
        cd - || exit
      fi
    else
      localeDir="$(basename "$file")"
      locale=${localeDir%%.*}
      clean=0
      if [[ "${locale}" == "en" ]]; then
        dir="values"
        clean=1
      elif [[ "${locale}"  == "zh-Hans" ]]; then
        dir="values-zh-rCN"
        clean=1
      elif [[ "${locale}"  == "zh-Hant" ]]; then
        dir="values-zh-rTW"
        clean=1
      elif [[ ${locale} == values* ]]; then
        clean=0
      elif [[ ${locale} == *-* && "${locale}" ]]; then
        IFS='-' read -ra parts <<< "$locale"
        reconnected_string="${parts[0]}-r${parts[1]}"

        dir="values-$reconnected_string"
        clean=1
      else
        dir="values-$locale"
        clean=1
      fi

      if [ $clean -eq 1 ]; then
         mkdir -p "$dir"
         mv -n "$file"/* "$dir"/
         rmdir "$file"
         traverse_and_clean "$dir"
       fi
    fi
  done
}

function grab_android_string {
    androidStringName=$1
    if [ -z "$2" ]; then
        replaceWith=${androidStringName//./_}
    else
        replaceWith=$2
    fi

    # Fetch the value for the provided androidStringName from strings.xml
    androidValue=$(xmlstarlet sel -t -v "//string[@name=\"$androidStringName\"]" strings.xml)

    # Check if the string exists in strings-localized.xml
    stringExists=$(xmlstarlet sel -t -v "count(//string[@name=\"$replaceWith\"])" strings-localized.xml)

    if [ "$stringExists" -eq 0 ]; then
        # If the string does not exist, add it
        xmlstarlet ed -L -s "/resources" -t elem -n "string" -v "$androidValue" \
            -i "//string[not(@name)]" -t attr -n "name" -v "$replaceWith" \
            strings-localized.xml
    else
        # If the string exists, update its value
        xmlstarlet ed -L -u "//string[@name=\"$replaceWith\"]" -v "$androidValue" strings-localized.xml
    fi
}

function copy_strings {
    for dir in "$1"/*
    do
      if [ -f "$dir/strings-localized.xml" ]; then
        cp -r "$dir" app/src/main/res/
      fi

    done
}


rm -rf strings_temp
mkdir strings_temp
cd strings_temp || exit
curl -sS "$bundle_url_strings" > file_strings.zip
curl -sS "$bundle_url_xml" > file_xml.zip
unzip file_strings.zip
rm file_strings.zip

unzip file_xml.zip
rm file_xml.zip

traverse_and_clean .

cd .. || exit

copy_strings strings_temp
rm -rf strings_temp
