#!/bin/bash
LIB="$SCRIPTS_HOME/lib"

echo "# $LIB running mesh predictions"
echo "# image_files: $1"
echo "# threshold: $2 or 1"

java -cp $LIB/dm3d.jar:$LIB/ij.jar:$LIB/jama.jar $SCRIPTS_HOME/MeshPrediction.java $1 $2

