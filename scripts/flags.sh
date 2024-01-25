#!/bin/bash
pwd=$(pwd)
iosRepoDir=$1


cd $iosRepoDir
cd CodeUI/Sources/CodeUI/Assets/UI.xcassets/flags\ \(region\)/

echo " "
echo "Converting asset pdf's to webp"
echo " "

for d in */; do
  cd $d
  dirname=$(echo "${PWD##*/}")
  flag=$(echo "${dirname%%.*}")
  convert           \
   -verbose       \
   -density 1000 \
    ${flag}.pdf      \
    ic_flag_$flag.webp
  cd -
done

# update in source
mv **/*.webp ${pwd}/app/src/main/res/drawable-nodpi/

cd $pwd
