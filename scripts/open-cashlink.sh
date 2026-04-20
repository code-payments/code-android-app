#!/bin/bash

CASHLINK=$1

adb shell am start -a android.intent.action.VIEW -d "${CASHLINK}"

