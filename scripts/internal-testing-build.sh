#!/usr/bin/env bash

date="$(date '+%m.%d.%y')"

export NOTIFY_ERRORS=true
export DEBUG_MINIFY=true
export DEBUG_CRASHLYTICS_UPLOAD=true

./gradlew :flipchatApp:assembleDebug

outputDir="$(pwd)/flipchatApp/build/outputs/apk/debug"
mv "${outputDir}/flipchatApp-debug.apk" "${outputDir}/flipchatApp-${date}-debug.apk"

unset NOTIFY_ERRORS
unset DEBUG_MINIFY
unset DEBUG_CRASHLYTICS_UPLOAD