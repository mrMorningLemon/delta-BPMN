# delta-BPMN

This is a JAVA-based framework that provides functionalities for modelling and verification of data-aware BPMN models (DABs for short). More on DABs can be found in this paper: https://link.springer.com/chapter/10.1007%2F978-3-030-26619-6_12. 

## Programming DABs
The core of delta-BPMN is based on the DAB object model. Thus, one can easily 'program' any data-aware BPMN model by using dedicated packages of the framework. 

## Verifying DABs
Verification of DABs is done in two steps. First, create a `DABProcessTranslator` object with a DAB process to verify and add to it a safety. The safety property has to be separately created as a `ConjunctiveSelectQuery`. Then call `generateMCMTTranslation()` of the `DABProcessTranslator` object, which will generate a `.txt` file with an MCMT translation of the DAB process. Note that all the MCMT translations are generated in the parent directory of the project.

MCMT is an SMT-based model checker that supports verification of infinite-state transition systems. It can be download here: http://users.mat.unimi.it/users/ghilardi/mcmt/. Consult MCMT's user manual for more details on the tool itself. Note that MCMT has to be linked to the SMT solver Yices 1 or (starting from MCMT version 3.0) to the SMT solver Z3. To run a file with an MCMT program, use the command line and type this: `./bin/mcmt [options] <filename>`.
