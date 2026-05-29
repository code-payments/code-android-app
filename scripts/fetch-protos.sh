#!/bin/bash

root=$(pwd)
REPO_URL="git@github.com:code-payments/ocp-protobuf-api.git"  # Default repo URL
COMMIT_SHA=""
TEMP_DIR=$(mktemp -d)
TARGET="code"

# Parse options
while getopts ":r:t:" opt; do
  case ${opt} in
    r )
      REPO_URL=$OPTARG
      ;;
    t )
      TARGET=$OPTARG
      if [ "$TARGET" == "flipchat" ]; then
        REPO_URL="git@github.com:code-payments/flipchat-protobuf-api.git"
      elif [ "$TARGET" == "flipcash" ]; then
        REPO_URL="git@github.com:code-payments/flipcash2-protobuf-api.git"
      fi
      ;;
    \? )
      echo "Invalid option: -$OPTARG" >&2
      exit 1
      ;;
  esac
done

shift $((OPTIND -1))

DEST_DIR="definitions/$TARGET/protos/src/main/proto"

# Get the commit SHA if provided
COMMIT_SHA=$1

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
mkdir -p "${root}/$DEST_DIR"

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

# Preserve custom opencode namespacing
if [ "$TARGET" = "opencode" ]; then
  find "${root}/definitions/$TARGET/protos/src/main/proto" -name "*.proto" -type f -exec sh -c "awk '{gsub(/];/, \"];\n\n\"); gsub(/option java_package = \"com\.codeinc\.gen\./, \"option java_package = \\\"com.codeinc.opencode.gen.\"); print}' {} > tmp && mv tmp {}" \;
fi