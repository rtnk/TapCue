#!/bin/sh

./gradlew :maestro-cli:installDist

rm -rf ~/.tapcue/bin
rm -rf ~/.tapcue/lib

cp -r ./maestro-cli/build/install/tapcue/bin ~/.tapcue/bin
cp -r ./maestro-cli/build/install/tapcue/lib ~/.tapcue/lib