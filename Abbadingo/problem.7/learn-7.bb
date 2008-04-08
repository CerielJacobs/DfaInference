ARGS=
# ARGS="$ARGS -DIncrementalCounts"
# ARGS="$ARGS -DCompensateRedundancy"
# ARGS="$ARGS -DMissingEdges"
# ARGS="$ARGS -DUseProductive"

prun -4  CLASSPATH=../../DfaInference.jar:. -v -o learn-7.bb.out \
    -rsh ssh \
    $IBIS_HOME/bin/ibis-prun $1 \
    -server -Xms512M -Xmx512M \
    -Dibis.name=tcp \
    -Dsatin.closed \
    -Dsatin.closeConnections=false \
    $ARGS \
    DfaInference.BestBlue \
    -strategy DfaInference.ChoiceCountStrategy \
    -folder DfaInference.EdFold \
    -input ../train.7 -mindepth 10 -maxdepth 18
