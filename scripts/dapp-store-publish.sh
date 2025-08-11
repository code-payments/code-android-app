#!/usr/bin/env bash

source .env.local

pushd apps/flipcash/app/dapp_publishing >/dev/null || exit

root=$(pwd)

KEYSTORE_PATH=$(grealpath --relative-to="$root" "$DAPP_KEYSTORE_PATH")

echo "Creating release on mainnet"
echo ""

npx dapp-store create release -k "$KEYSTORE_PATH" -b "$BUILD_TOOLS_PATH" -u "$DAPP_MAINNET_RPC"  || exit

echo "Submitting app for review"
echo ""

npx dapp-store publish submit -k "$KEYSTORE_PATH" -u "$DAPP_MAINNET_RPC" --requestor-is-authorized --complies-with-solana-dapp-store-policies || exit

popd >/dev/null || exit



