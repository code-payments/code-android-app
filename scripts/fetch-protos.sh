#!/bin/bash

root=$(pwd)
REPO_URL="git@github.com:code-payments/code-protobuf-api.git"  # Default repo URL
COMMIT_SHA=""
RUN_STRIP_PROTO_VALIDATION=false  # Default to not running the script
TEMP_DIR=$(mktemp -d)
TARGET="code"

# Parse options
while getopts ":r:t:x" opt; do
  case ${opt} in
    r )
      REPO_URL=$OPTARG
      ;;
    x )
      RUN_STRIP_PROTO_VALIDATION=true
      ;;
    t )
      TARGET=$OPTARG
      if [ "$TARGET" == "flipchat" ]; then
        REPO_URL="git@github.com:code-payments/flipchat-protobuf-api.git"
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

# Conditionally run the strip proto validation script
if [ "$RUN_STRIP_PROTO_VALIDATION" = true ]; then
    SCRIPT_PATH="${root}/scripts/strip-proto-validation.sh"

    # Ensure the script exists and is executable
    if [ -f "$SCRIPT_PATH" ]; then
        if [ -x "$SCRIPT_PATH" ]; then
            echo "Running strip-proto-validation.sh"
            "$SCRIPT_PATH"
        else
            echo "Error: strip-proto-validation.sh is not executable. Run 'chmod +x $SCRIPT_PATH' to fix this."
            exit 1
        fi
    else
        echo "Error: strip-proto-validation.sh not found at $SCRIPT_PATH"
        exit 1
    fi
fi
