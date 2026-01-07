#!/bin/bash
set -e # exit immediately if any failure
./gradlew clean build

# macos, assumes a mounted volume like so where the Hytale server is installed
rm -f /Volumes/Hytale/install/release/package/game/latest/Server/mods/Tebex-Hytale*.jar
cp build/libs/*.jar /Volumes/Hytale/install/release/package/game/latest/Server/mods/

echo -e "\033[0;32mSuccessfully built and installed Hytale plugin!"