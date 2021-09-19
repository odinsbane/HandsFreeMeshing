#!/bin/bash
LIB="$SCRIPTS_HOME/lib"

echo "# $LIB running mesh predictions"
echo "# image_files: $@"

for image_file in $*;
  do
    java -cp $LIB/dm3d.jar:$LIB/ij.jar:$LIB/jama.jar $SCRIPTS_HOME/MeshPrediction.java $image_file
  done
