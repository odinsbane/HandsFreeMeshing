#!/bin/bash

LIB="$SCRIPTS_HOME/lib"
skeleton=$1
output=$2
echo "skeleton: $skeleton being labelled and saved as $output"
java -Xmx48G -cp $LIB/dm3d.jar:$LIB/ij.jar:$LIB/jama.jar:$LIB/aux.jar Label2DTrainingData "$skeleton" "$output"
