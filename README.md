Release MRCM Validator
======================
Proof of concept.
* Loads MRCM from example Refset files into domain model.
* Loads a whole SNOMED CT Snapshot release into a memory based Snomed-Query-Service Lucene index.
* For each Domain and Attribute uses ECL against the Stated Form to select and report concepts with attributes outside of the permitted range.
