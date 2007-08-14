ARGS=
# ARGS="$ARGS -DIncrementalCounts"
# ARGS="$ARGS -DCompensateRedundancy"
# ARGS="$ARGS -DMissingEdges"
# ARGS="$ARGS -DUseProductive"

prun CLASSPATH=../../DfaInference.jar:. -v -o learn-7.bb.out \
    $IBIS_HOME/bin/ibis-prun 64 \
    -server -Xmx256M \
    -Dibis.name=tcp \
    $ARGS \
    DfaInference.BestBlue \
    -strategy DfaInference.ChoiceCountStrategy \
    -folder DfaInference.EdFold \
    -input ../train.7 -mindepth 10 -maxdepth 18
