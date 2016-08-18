This repository contains code, data, and slides for the following
presentation:

What can your library do for you?
=================================

Rajarshi Guha, guhar@mail.NIH.gov, Dac-Trung Nguyen, Ajit Jadhav

National Institutes of Health, Bethesda, Maryland, United States

Abstract: The design of chemical libraries is usually informed by
pre-existing characteristics and desired features. On the other hand,
assesing the prospective performance of a new library is more
difficult. Importantly, a given screening library is often screened in
a variety of systems which can differ in cell lines, readouts, formats
and so on. In this study we explore to what extent pre-existing
libraries can shed light on the relation between library activity and
assay features. Using an ontology such as the BAO, it is possible to
construct a hierarchy of annotations associated with an assay. Based
on this annotation hierarchy we can then ask how likely are molecules
associated with a specific annotation, to be identified as active. To
allow generalization we consider substrucural features, as represented
by a structural key fingerprint, rather than whole molecules. We
employ a Bayesian framework to quantify the the association between a
substructural feature and a given assay annotation, using a set of
NCGC assays that have been annotated with BAO terms. We discuss our
approach to training the Bayesian model and describe benchmarks that
characterize model performance relative to the position of the
annotation in the BAO hierarchy. Finally we discuss the role of this
approach in a library design workflow that includes traditional design
features such as chemical space coverage and physicochemical
properties but also takes in to account screening platform features.
