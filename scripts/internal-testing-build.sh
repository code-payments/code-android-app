#!/usr/bin/env bash

date="$(date '+%m.%d.%y')"

export NOTIFY_ERRORS=true
export DEBUG_MINIFY=true
export DEBUG_CRASHLYTICS_UPLOAD=true

./gradlew :app:assembleDebug

outputDir="$(pwd)/app/build/outputs/apk/debug"
mv "${outputDir}/app-debug.apk" "${outputDir}/app-${date}-debug.apk"

unset NOTIFY_ERRORS
unset DEBUG_MINIFY
unset DEBUG_CRASHLYTICS_UPLOAD