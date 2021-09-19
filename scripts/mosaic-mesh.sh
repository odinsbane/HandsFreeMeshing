#!/bin/bash
LIB=~/data/working/scripts/lib

java -cp $LIB/dm3d.jar:$LIB/ij.jar:$LIB/jama.jar:$LIB/lg.jar ~/data/working/scripts/MosaicMesh.java $1 $2
