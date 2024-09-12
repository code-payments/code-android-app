#!/bin/bash

root=$(pwd)
REPO_URL="https://github.com/code-payments/code-protobuf-api"
COMMIT_SHA=$1
TEMP_DIR=$(mktemp -d)
DEST_DIR="service/protos/src/main/proto"

# Clone the repository
git clone "$REPO_URL" "$TEMP_DIR"

# Change to the cloned repository directory
cd "$TEMP_DIR" || exit

# If a commit SHA is provided, checkout that commit
if [ -n "$COMMIT_SHA" ]; then
    git checkout "$COMMIT_SHA"
else
    git checkout main
fi

# Create the destination directory if it doesn't exist
mkdir -p "../../$DEST_DIR"

# Copy proto files
if [ -d "proto" ]; then
    rsync -av --exclude='buf*' proto/ "${root}/$DEST_DIR/"
    echo "Proto files copied successfully."
else
    echo "Error: 'proto' directory not found in the repository."
    exit 1
fi

# Clean up: remove the temporary directory
cd ../..
rm -rf "$TEMP_DIR"

sh "${root}"/scripts/strip-proto-validation.sh
