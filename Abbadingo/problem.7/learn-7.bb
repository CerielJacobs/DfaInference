ARGS=
# ARGS="$ARGS -DIncrementalCounts"
# ARGS="$ARGS -DCompensateRedundancy"
# ARGS="$ARGS -DMissingEdges"
# ARGS="$ARGS -DUseProductive"

prun COMMAND=$SATIN_HOME/bin/satin-run -4  CLASSPATH=../../lib/DfaInference.jar:. -v -o learn-7.bb.out \
    -rsh ssh \
    ibis-prun $1 \
    -server -Xms512M -Xmx512M \
    -Dibis.name=tcp \
    -Dsatin.closed \
    -Dsatin.closeConnections=false \
    $ARGS \
    DfaInference.BestBlue \
    -strategy DfaInference.ChoiceCountStrategy \
    -folder DfaInference.EdFold \
    -input ../train.7 -mindepth 10 -maxdepth 18
