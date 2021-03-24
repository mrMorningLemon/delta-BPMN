# delta-BPMN

This is a JAVA-based framework that provides functionalities for modelling and verification of data-aware, block-structured BPMN models (DABs for short). More on DABs can be found in this paper: https://link.springer.com/chapter/10.1007%2F978-3-030-26619-6_12. 

The framework currently supports the following blocks:

* #### Task block 
This is a basic block; can be equipped with a precondition and updates.

![task block pattern](https://github.com/mrMorningLemon/delta-BPMN/blob/main/supported%20blocks/task.png)

* #### Catch event block
This is a basic block; can be equipped with updates. These are the supported block variations:

![catch event block patterns](https://github.com/mrMorningLemon/delta-BPMN/blob/main/supported%20blocks/event.png)

* #### Process block
Consists of a sub-block B surrounded by start and end events. These are the supported block variations:

![process event block pattern](https://github.com/mrMorningLemon/delta-BPMN/blob/main/supported%20blocks/process.png)

* #### Sequence block
Contains two sub-blocks B1 and B2 that should be executed sequentially.

![sequence block pattern](https://github.com/mrMorningLemon/delta-BPMN/blob/main/supported%20blocks/sequence.png)

* #### Deferred parallel/choice block
Contains two sub-blocks B1 and B2, surrounded with split- and join-gateways. These are the supported block variations:

![deferred event block patterns](https://github.com/mrMorningLemon/delta-BPMN/blob/main/supported%20blocks/deferred.png)

* #### Exclusive choice block
Similar to deferred choice block, uses a condition-based XOR split gateway. Notice that B1 only occurs if F evaluates to `true`. 

![exclusive choice block pattern](https://github.com/mrMorningLemon/delta-BPMN/blob/main/supported%20blocks/exclusive-choice.png)

* #### Loop block
Consists of two sub-blocks B1 and B2 and loop condition F. If F is `false`, the loop proceeds from B1 to B2, and then repeats again until F becomes `true`.

![loop block pattern](https://github.com/mrMorningLemon/delta-BPMN/blob/main/supported%20blocks/loop.png)

* #### Possible completion block
Consists of a XOR gateway and condition F. If F is `false`, then the process finishes. These are the supported block variations:

![possible completion block pattern](https://github.com/mrMorningLemon/delta-BPMN/blob/main/supported%20blocks/possible-completion.png)



## Programming DABs
The core of delta-BPMN is based on the DAB object model. Thus, one can easily 'program' any data-aware BPMN model by using dedicated packages of the framework. 

## Verifying DABs
Verification of DABs is done in two steps. First, create a `DABProcessTranslator` object with a DAB process to verify and add to it a safety. The safety property has to be separately created as a `ConjunctiveSelectQuery`. Then call `generateMCMTTranslation()` of the `DABProcessTranslator` object, which will generate a `.txt` file with an MCMT translation of the DAB process. Note that all the MCMT translations are generated in the parent directory of the project.

MCMT is an SMT-based model checker that supports verification of infinite-state transition systems. It can be download here: http://users.mat.unimi.it/users/ghilardi/mcmt/. Consult MCMT's user manual for more details on the tool itself. Note that MCMT has to be linked to the SMT solver Yices 1. To run a file with an MCMT program, use the command line and type this: `./bin/mcmt [options] <filename>`.
