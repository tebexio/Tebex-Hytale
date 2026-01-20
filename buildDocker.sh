#!/bin/bash
# This builds the plugin using the docker container and outputs the jars to libs/

set -e # exit on any failure

# The dockerfile in this repo contains the build environment
docker build -t tebex-builder .

# Create a stopped container from the artifact stage
docker create --name tebex-build tebex-builder

# Copy jars from container to host, then clean it up
rm -f ./build/libs/*.jar
docker cp tebex-build:/app/build/libs ./build/
docker rm tebex-build

# macos, assumes a mounted volume like so where the Hytale server is installed
#rm -f /Volumes/Hytale/install/release/package/game/latest/Server/mods/Tebex-Hytale*.jar
#cp build/libs/*.jar /Volumes/Hytale/install/release/package/game/latest/Server/mods/

echo -e "\033[0;32mSuccessfully built and installed Hytale plugin!"