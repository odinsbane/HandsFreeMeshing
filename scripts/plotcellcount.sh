#!/bin/bash
LIB="$SCRIPTS_HOME/lib"
  java -cp $LIB/dm3d.jar:$LIB/ij.jar:$LIB/jama.jar:$LIB/lg.jar $SCRIPTS_HOME/PlotCellCountOverTime.java $@ 
