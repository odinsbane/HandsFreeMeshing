LIB=$SCRIPTS_HOME/lib
java -cp $LIB/dm3d.jar:$LIB/ij.jar:$LIB/jama.jar \
    $SCRIPTS_HOME/CompareMosaicImages.java $1 $2
