Sample Programs
===============

Building index:

```
sbt 'runMain ncats.bayeslib.MolIndex$Build bayes.db cids.smi.gz'
```

Fetching from index:

```
sbt 'runMain ncats.bayeslib.MolIndex$Fetch bayes.db 49858731 24791769 5771900 16013837 2522096 5711619 22549997 6622044 796928 60186109'
```

Searching molecular weight range:

```
sbt 'runMain ncats.bayeslib.MolIndex$Mwt bayes.db 104 250'
```

Searching popcnt (fingerprint population count) range:

```
sbt 'runMain ncats.bayeslib.MolIndex$Popcnt bayes.db 100 130'
```
