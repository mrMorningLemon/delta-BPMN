package it.unibz.deltabpmn.processschema.core;

import it.unibz.deltabpmn.datalogic.BinaryConditionProvider;
import it.unibz.deltabpmn.datalogic.ConjunctiveSelectQuery;
import it.unibz.deltabpmn.datalogic.InsertTransition;
import it.unibz.deltabpmn.dataschema.core.DataSchema;
import it.unibz.deltabpmn.dataschema.core.SystemSorts;
import it.unibz.deltabpmn.dataschema.elements.CaseVariable;
import it.unibz.deltabpmn.exception.EevarOverflowException;
import it.unibz.deltabpmn.exception.InvalidInputException;
import it.unibz.deltabpmn.exception.UnmatchingSortException;
import it.unibz.deltabpmn.processschema.blocks.Block;
import it.unibz.deltabpmn.processschema.blocks.ErrorBlock;
import it.unibz.deltabpmn.processschema.blocks.Task;


//ToDo: add support for possible completion block (maybe add an empty block class)
public class DABErrorBlock implements ErrorBlock {

    private String name;
    private CaseVariable lifeCycle;
    private Block[] subBlocks;
    private DataSchema dataSchema;
    private ConjunctiveSelectQuery cond;
    //private Block handler;

    //public DABErrorBlock(String name, Block handler, DataSchema schema) {
    public DABErrorBlock(String name, DataSchema schema) {
        this.name = name;
        //this.handler = handler;
        this.subBlocks = new Block[1];
        this.dataSchema = schema;
        this.lifeCycle = this.dataSchema.newCaseVariable("lifecycle_" + name, SystemSorts.STRING, true);
        this.lifeCycle.setLifeCycle(1);
    }

    //public DABErrorBlock(String name, Block handler, ConjunctiveSelectQuery cond, DataSchema schema) {
    public DABErrorBlock(String name, ConjunctiveSelectQuery cond, DataSchema schema) {
        this.name = name;
        //this.handler = handler;
        this.cond = cond;
        this.subBlocks = new Block[1];
        this.dataSchema = schema;
        this.lifeCycle = this.dataSchema.newCaseVariable("lifecycle_" + name, SystemSorts.STRING, true);
    }

//    @Override
//    public void addBlock(Block b) {
//        this.subBlocks[0] = b;
//    }

    @Override
    public void addCondition(ConjunctiveSelectQuery cond) {
        this.cond = cond;
    }

    @Override
    public String getMCMTTranslation() throws InvalidInputException, UnmatchingSortException, EevarOverflowException {
        String result = "";

        // first execution step via an MCMT transition: itself ENABLED --> B1 ENABLED and itself ACTIVE
        ConjunctiveSelectQuery firstGuard = new ConjunctiveSelectQuery();
        firstGuard.addBinaryCondition(BinaryConditionProvider.equality(this.lifeCycle, State.ENABLED));
        InsertTransition firstUpdate = new InsertTransition(this.name + " first translation", firstGuard, this.dataSchema);
        firstUpdate.setControlCaseVariableValue(this.lifeCycle, State.ACTIVE); //the block itself becomes "active" (or "waiting" as in the tech. report)
        firstUpdate.setControlCaseVariableValue(this.subBlocks[0].getLifeCycleVariable(), State.ENABLED); //the sub-block becomes "enabled"

        // second execution step via an MCMT transition: B1 completed and cond TRUE --> B1 IDLE and itself COMPLETED
        ConjunctiveSelectQuery secondGuard = new ConjunctiveSelectQuery();
        secondGuard.addBinaryCondition(BinaryConditionProvider.equality(this.subBlocks[0].getLifeCycleVariable(), State.COMPLETED));//check if the sub-block's state is set to "completed"
        InsertTransition secondUpdate = new InsertTransition(this.name + " second translation", secondGuard, this.dataSchema);
        secondUpdate.addTaskGuard(this.cond.getMCMTTranslation());//add condition to the guard that will be checked directly in MCMT
        secondUpdate.setControlCaseVariableValue(this.lifeCycle, State.COMPLETED); //the block's state is changed to "completed"
        secondUpdate.setControlCaseVariableValue(this.subBlocks[0].getLifeCycleVariable(), State.IDLE);//the sub-block's state is changed


        // third execution step via an MCMT transition: B1 completed and cond FALSE --> B1 idle, Handler error, subBlocks of handler idle
        ConjunctiveSelectQuery thirdGuard = new ConjunctiveSelectQuery();
        thirdGuard.addBinaryCondition(BinaryConditionProvider.equality(this.subBlocks[0].getLifeCycleVariable(), State.COMPLETED));
        InsertTransition thirdUpdate = new InsertTransition(this.name + " third translation", thirdGuard, this.dataSchema);
        thirdUpdate.addTaskGuard(this.cond.getNegatedMCMT());// adds a guard that guarantees that the process will progress along a path associated to a condition that is FALSE
        //ToDo: fix this part with the block handler
        //startPropagation(handler, thirdUpdate);//start propagation if the path with a FALSE condition has been followed

        // generate MCMT translation
        result += firstUpdate.getMCMTTranslation() + "\n" + secondUpdate.getMCMTTranslation() + "\n" + thirdUpdate.getMCMTTranslation() + "\n";

        return result;
    }


    private void startPropagation(Block handler, InsertTransition transition) throws InvalidInputException, UnmatchingSortException {
        //the main block gets its lifecycle variable set to Error
        transition.setControlCaseVariableValue(handler.getLifeCycleVariable(), this.dataSchema.newConstant("Error" + handler.getSubBlocks()[0].getName(), SystemSorts.STRING));
        for (Block block : handler.getSubBlocks())
            propagateError(block, transition);
    }

    private void propagateError(Block current, InsertTransition transition) throws InvalidInputException, UnmatchingSortException {
        //all other sub-blocks of the main block (i.e., the main block with the handler) are set to Idle
        if (current instanceof Task) {
            transition.setControlCaseVariableValue(current.getLifeCycleVariable(), State.IDLE);
            return;
        }
        transition.setControlCaseVariableValue(current.getLifeCycleVariable(), State.IDLE);
        for (Block block : current.getSubBlocks())
            propagateError(block, transition);
    }

    @Override
    public CaseVariable getLifeCycleVariable() {
        return this.lifeCycle;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Block[] getSubBlocks() {
        return this.subBlocks;
    }
}
