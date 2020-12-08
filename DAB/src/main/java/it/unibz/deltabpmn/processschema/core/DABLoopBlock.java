package it.unibz.deltabpmn.processschema.core;

import it.unibz.deltabpmn.datalogic.BinaryConditionProvider;
import it.unibz.deltabpmn.datalogic.ConjunctiveSelectQuery;
import it.unibz.deltabpmn.datalogic.InsertTransition;
import it.unibz.deltabpmn.dataschema.core.SystemSorts;
import it.unibz.deltabpmn.dataschema.elements.CaseVariable;
import it.unibz.deltabpmn.dataschema.core.DataSchema;
import it.unibz.deltabpmn.exception.EevarOverflowException;
import it.unibz.deltabpmn.exception.InvalidInputException;
import it.unibz.deltabpmn.exception.UnmatchingSortException;
import it.unibz.deltabpmn.processschema.blocks.Block;
import it.unibz.deltabpmn.processschema.blocks.LoopBlock;

class DABLoopBlock implements LoopBlock {

    private String name;
    private CaseVariable lifeCycle;
    private Block[] subBlocks;
    private DataSchema dataSchema;
    private ConjunctiveSelectQuery cond;

    public DABLoopBlock(String name, DataSchema schema) {
        this.name = name;
        this.subBlocks = new Block[2];
        this.dataSchema = schema;
        this.lifeCycle = this.dataSchema.newCaseVariable("lifecycle_" + name, SystemSorts.STRING, true);
        this.lifeCycle.setLifeCycle(1);
    }

    public DABLoopBlock(String name, ConjunctiveSelectQuery cond, DataSchema schema) {
        this.name = name;
        this.cond = cond;
        this.subBlocks = new Block[2];
        this.lifeCycle = this.dataSchema.newCaseVariable("lifecycle_" + name, SystemSorts.STRING, true);
        this.lifeCycle.setLifeCycle(1);
    }

    @Override
    public void addFirstBlock(Block b) {
        this.subBlocks[0] = b;
    }

    @Override
    public void addSecondBlock(Block b) {
        this.subBlocks[1] = b;
    }

    @Override
    public void addCondition(ConjunctiveSelectQuery cond1) {
        this.cond = cond1;
    }


    @Override
    public String getMCMTTranslation() throws InvalidInputException, UnmatchingSortException, EevarOverflowException {
        String result = "";

        // first part: itself ENABLED --> B1 ENABLED and itself ACTIVE
        ConjunctiveSelectQuery firstGaurd = new ConjunctiveSelectQuery();
        firstGaurd.addBinaryCondition(BinaryConditionProvider.equality(this.lifeCycle, State.ENABLED));
        InsertTransition firstUpate = new InsertTransition(this.name + " first translation", firstGaurd,this.dataSchema);
        firstUpate.setControlCaseVariableValue(this.subBlocks[0].getLifeCycleVariable(), State.ENABLED);
        firstUpate.setControlCaseVariableValue(this.lifeCycle, State.ACTIVE);

        // second part: B1 completed and cond TRUE --> B1 IDLE and B2 ENABLED
        ConjunctiveSelectQuery secondGuard = new ConjunctiveSelectQuery();
        secondGuard.addBinaryCondition(BinaryConditionProvider.equality(this.subBlocks[0].getLifeCycleVariable(), State.COMPLETED));
        InsertTransition secondUpdate = new InsertTransition(this.name + " second translation", secondGuard,this.dataSchema);
        secondUpdate.addTaskGuard(cond.getMCMTTranslation());
        secondUpdate.setControlCaseVariableValue(this.subBlocks[0].getLifeCycleVariable(), State.IDLE);
        secondUpdate.setControlCaseVariableValue(this.subBlocks[1].getLifeCycleVariable(), State.ENABLED);


        // third part: B2 completed --> B1 ENABLED and B2 IDLE
        ConjunctiveSelectQuery thirdGuard = new ConjunctiveSelectQuery();
        thirdGuard.addBinaryCondition(BinaryConditionProvider.equality(this.subBlocks[1].getLifeCycleVariable(), State.COMPLETED));
        InsertTransition thirdUpdate = new InsertTransition(this.name + " third translation", thirdGuard,this.dataSchema);
        thirdUpdate.setControlCaseVariableValue(this.subBlocks[0].getLifeCycleVariable(), State.ENABLED);
        thirdUpdate.setControlCaseVariableValue(this.subBlocks[1].getLifeCycleVariable(), State.IDLE);


        // fourth part:  B1 completed and cond FALSE --> B1 IDLE and itself COMPLETED
        ConjunctiveSelectQuery fourthGuard = new ConjunctiveSelectQuery();
        fourthGuard.addBinaryCondition(BinaryConditionProvider.equality(this.subBlocks[0].getLifeCycleVariable(), State.COMPLETED));
        InsertTransition fourthUpdate = new InsertTransition(this.name + " fourth translation", fourthGuard,this.dataSchema);
        fourthUpdate.addTaskGuard(cond.getNegatedMCMT());
        fourthUpdate.setControlCaseVariableValue(this.subBlocks[0].getLifeCycleVariable(), State.IDLE);
        fourthUpdate.setControlCaseVariableValue(this.lifeCycle, State.COMPLETED);


        // generate MCMT translation
        result += firstUpate.getMCMTTranslation() + "\n" + secondUpdate.getMCMTTranslation() + "\n" + thirdUpdate.getMCMTTranslation() + "\n" + fourthUpdate.getMCMTTranslation() + "\n";

        return result;

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
