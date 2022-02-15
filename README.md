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


To strat working with a data-aware BPMN process, one needs to create its data schema. In delta-BPMN this is done as follows: ```DataSchema dataSchema = DataSchema.getInstance()```.
When the `DataSchema` object has been acquired, it can be populated with data type (or sort), relation and case variable declarations. 


Sorts used by a process model are created as `Sort` objects. For example, to define a sort for user idenfitiers, write ```Sort id = dataSchema.newSort("userID")```. There are a few reserved "special" sorts such as string, boolean and integer defined in class `SystemSorts`.


As attributes and relations are tightly connected, any `Attribute` object has to be immediately assigned to its “hosting” relation. For example, for catalog relation *User(UID : id, name : string, age : int)*, we first create a corresponding `CatalogRelation` and then populate it with two `Attribute` objects:

```
CatalogRelation user = dataSchema.newCatalogRelation("user");
Attribute uid = user.addAttribute(“UID”, id);
Attribute name = user.addAttribute("name", SystemSorts.STRING);
Attribute age = user.addAttribute("age", SystemSorts.INT);
```

Case variables are declared similarly to sorts and relations. For example, to create a case variable storing a user ID, one needs to use the `DataSchema` object: ```CaseVariable userID = dataSchema.newCaseVariable("userID", id, true)```.


For querying the data schema, one needs to create `ConjunctiveSelectQuery` objects and, depending whether the query accesses only the case variabels or also checks on catalog/repository relations, populate them with relational attributes (appearing in the SELECT statement) and/or binary conditions. For example, a simple SQL query `SELECT id FROM User WHERE age>26` can be generated in delta-BPMN as follows:

```
ConjunctiveSelectQuery usrQuery = new ConjunctiveSelectQuery(user.getAttributeByIndex(0));
usrQuery.addBinaryCondition(BinaryConditionProvider.greaterThan(application.getAttributeByIndex(2), dataSchema.newConstant("26",SystemSorts.INT)));
```

Updates on top of the data schema are done using three types of so-called *transitions*. Specifically, one can use `DeleteTransition`, `InsertTransition` or `BulkUpdate` classes to create objects respecitvely specifying deletions, insertions and bulk updates. Notice two things. First, all such updates can be conditional. Thus, whenever a condition is required, a correspodning query object should be provided when an update obejct is being created. Second, `DeleteTransition` and `InsertTransition` objects can also update case varibales using the `setControlCaseVariableValue` method. Here is an example of an `InsertTransition` object that uses results of the `usrQuery` object to populate the `userID` case variable:

```
InsertTransition copyUserID = new InsertTransition("CopyUserID", dataSchema, usrQuery);
copyUserID.setControlCaseVariableValue(userID,uid);
```

The second major component of the delta-BPMN framework allows the creation of block-strucutred processes and enriching them with data access and manipulation capabilities. To start creating a process, one needs to create its process schema (using by the `ProcessSchema` class), that in turn depends on the previously generated `DataSchema` object:  ```ProcessSchema processSchema = new ProcessSchema(dataSchema)```.


Then, every process block related to the given process schema should created by calling dedicated methods in the `ProcessSchema` object. For example, we can create a task called *SelectUser* that uses the previously defined query to pick up data of all the users over 26 from the relational storage, selects nondeterministically one of the returned IDs and rememebers it in the dedicated case variable for further processing: ```Task selectUsr = processSchema.newTask("SelectUser", copyUserID)```.


## Verifying DABs
Verification of DABs is done in two steps. First, create a `DABProcessTranslator` object with a DAB process to verify and add to it a safety property. The safety property has to be separately created as a `ConjunctiveSelectQuery`. Then call `generateMCMTTranslation()` of the `DABProcessTranslator` object, which will produce a `.txt` file with an MCMT translation of the DAB process. Note that all the MCMT translations are generated in the parent directory of the project.

MCMT is an SMT-based model checker that supports verification of infinite-state transition systems. It can be download here: http://users.mat.unimi.it/users/ghilardi/mcmt/. Consult MCMT's user manual for more details on the tool itself. Note that MCMT has to be linked to the SMT solver Yices 1: https://yices.csl.sri.com/. To run a file with an MCMT program, use the command line and type this: `./bin/mcmt [options] <filename>`.
