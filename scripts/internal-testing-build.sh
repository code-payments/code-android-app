#!/usr/bin/env bash

date="$(date '+%m.%d.%y')"

export NOTIFY_ERRORS=true
./gradlew assembleDebug

outputDir="$(pwd)/app/build/outputs/apk/debug"
mv "${outputDir}/app-debug.apk" "${outputDir}/app-${date}-debug.apk"

unset NOTIFY_ERRORS