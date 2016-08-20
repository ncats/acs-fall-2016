#!/bin/sh
for f in `cat bao.txt`; do
    model="$f.model"
    if test ! -f $model; then
        echo "building model $f..."
        sbt -Dbayeslib.path=data/bao "runMain ncats.bayeslib.NaiveBayesTrainer mol.db $f"       
    fi
done
