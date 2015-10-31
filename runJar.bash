#!/bin/bash
THISPATH=/geog/local/alpha/progs/java/convDimToNcdf/
CPSEP=$(uname -o|awk '/Linux/{printf(":")} /Cygwin/{printf(";")}')
CLASSPATH=${CLASSPATH}${CPSEP}$(find ${BEAM4_HOME} -name \*.jar -printf "%p${CPSEP}")
CLASSPATH=${CLASSPATH}$(find $THISPATH -name \*.jar -printf "%p${CPSEP}")
export CLASSPATH
#echo $CLASSPATH

java akh.convdimtoncdf.ConvDimToNcdf $*
