LIB="$SCRIPTS_HOME"/lib
prediction="$1"

java -cp $LIB/dm3d.jar:$LIB/ij.jar:$LIB/jama.jar "$SCRIPTS_HOME"/GenerateMosaicFromNNPrediction.java $prediction

