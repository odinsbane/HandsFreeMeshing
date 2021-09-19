
LIB="$SCRIPTS_HOME/lib"
image=$1
mesh=$2

java -cp $LIB/dm3d.jar:$LIB/ij.jar:$LIB/jama.jar $SCRIPTS_HOME/BatchDeformMeshes.java $image $mesh
