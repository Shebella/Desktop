#!/bin/sh

ID=vdcm
STREAM=CSS-SAFEBOX-stream

echo ""
echo "User ID=$ID"
echo "Stream Name=$STREAM"

# make test or build.sh
cov-build --config /usr/local/cov/config/java.xml --dir ./cov-out sh build.sh
cov-analyze-java --dir ./cov-out/
cov-commit-defects --dir ./cov-out --stream $STREAM --host 140.96.27.72 --dataport 9090 --user $ID
