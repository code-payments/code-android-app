#!/usr/bin/env bash

pushd apps/flipcash/app/dapp_publishing >/dev/null || exit
root=$(pwd)
RELATIVE_PATH=$(grealpath --relative-to="$root" "$1")
echo "$RELATIVE_PATH"
popd >/dev/null || exit