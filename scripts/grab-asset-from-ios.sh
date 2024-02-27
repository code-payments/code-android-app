#!/bin/bash
pwd=$(pwd)
iosRepoDir=$1
relativeAssetDir=$2
assetPrefix=$3

cd $iosRepoDir
cd CodeUI/Sources/CodeUI/Assets/UI.xcassets/${relativeAssetDir}/

echo " "
echo "Converting asset pdf's to webp"
echo " "

for d in */; do
  cd $d
  dirname=$(echo "${PWD##*/}")
  asset=$(echo "${dirname%%.*}")
  convert           \
   -verbose       \
   -density 1000 \
    ${asset}.pdf      \
    ${assetPrefix}${asset}.webp
  cd -
done

# update in source
find . -name "*.webp" -exec mv {} ${pwd}/app/src/main/res/drawable-nodpi/ \;

cd $pwd
