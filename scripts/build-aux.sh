#!/bin/bash

javac -cp lib/ij.jar:lib/dm3d.jar -d target Label2DTrainingData.java $(find crick/ -name '*.java')
cd target
jar cf aux.jar $(find -name '*.class')
mv aux.jar ../lib/
cd ..

