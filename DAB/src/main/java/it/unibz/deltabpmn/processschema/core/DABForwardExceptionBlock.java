package it.unibz.deltabpmn.processschema.core;

import it.unibz.deltabpmn.datalogic.BinaryConditionProvider;
import it.unibz.deltabpmn.datalogic.ConjunctiveSelectQuery;
import it.unibz.deltabpmn.datalogic.InsertTransition;
import it.unibz.deltabpmn.dataschema.core.DataSchema;
import it.unibz.deltabpmn.dataschema.core.SystemSorts;
import it.unibz.deltabpmn.dataschema.elements.CaseVariable;
import it.unibz.deltabpmn.dataschema.elements.Constant;
import it.unibz.deltabpmn.exception.EevarOverflowException;
import it.unibz.deltabpmn.exception.InvalidInputException;
import it.unibz.deltabpmn.exception.UnmatchingSortException;
import it.unibz.deltabpmn.processschema.blocks.Block;
import it.unibz.deltabpmn.processschema.blocks.ForwardExceptionBlock;
import it.unibz.deltabpmn.processschema.blocks.Task;
import it.unibz.deltabpmn.verification.mcmt.NameManager;

class DABForwardExceptionBlock implements ForwardExceptionBlock {

    private String name;
    private CaseVariable lifeCycle;
    private Block[] subBlocks;
    private DataSchema dataSchema;
    private Constant error;

    public DABForwardExceptionBlock(String name, DataSchema schema) {
        this.name = NameManager.normaliseName(name);
        this.subBlocks = new Block[3];
        this.dataSchema = schema;
        this.lifeCycle = this.dataSchema.newCaseVariable("lifecycle" + this.name, SystemSorts.STRING, true);
        this.lifeCycle.setLifeCycle(1);
    }

    @Override
    public void addFirstBlock(Block b) {
        this.subBlocks[0] = b;
        // add constant error
        this.error = this.dataSchema.newConstant("Error" + b.getName(), SystemSorts.STRING);
    }

    @Override
    public void addSecondBlock(Block b) {
        this.subBlocks[1] = b;
    }

    @Override
    public void addThirdBlock(Block b) {
        this.subBlocks[2] = b;
    }


    @Override
    public String getMCMTTranslation() throws InvalidInputException, UnmatchingSortException, EevarOverflowException {
        String result = "";

        // first part: itself ENABLED --> B1 ENABLED and itself ACTIVE
        ConjunctiveSelectQuery firstGuard = new ConjunctiveSelectQuery();
        firstGuard.addBinaryCondition(BinaryConditionProvider.equality(this.lifeCycle, State.ENABLED));
        InsertTransition firstUpdate = new InsertTransition(this.name + " first translation", firstGuard, this.dataSchema);
        firstUpdate.setControlCaseVariableValue(this.lifeCycle, State.ACTIVE);
        firstUpdate.setControlCaseVariableValue(this.subBlocks[0].getLifeCycleVariable(), State.ENABLED);

        // second part: B1 completed --> B1 IDLE and B2 ENABLED
        ConjunctiveSelectQuery secondGuard = new ConjunctiveSelectQuery();
        secondGuard.addBinaryCondition(BinaryConditionProvider.equality(this.subBlocks[0].getLifeCycleVariable(), State.COMPLETED));
        InsertTransition secondUpdate = new InsertTransition(this.name + " second translation", secondGuard, this.dataSchema);
        secondUpdate.setControlCaseVariableValue(this.subBlocks[0].getLifeCycleVariable(), State.IDLE);
        secondUpdate.setControlCaseVariableValue(this.subBlocks[1].getLifeCycleVariable(), State.ENABLED);

        // third part: B2 COMPLETED --> B2 IDLE and itself COMPLETED
        ConjunctiveSelectQuery thirdGuard = new ConjunctiveSelectQuery();
        thirdGuard.addBinaryCondition(BinaryConditionProvider.equality(this.subBlocks[1].getLifeCycleVariable(), State.COMPLETED));
        InsertTransition thirdUpdate = new InsertTransition(this.name + " third translation", thirdGuard, this.dataSchema);
        thirdUpdate.setControlCaseVariableValue(this.lifeCycle, State.COMPLETED);
        thirdUpdate.setControlCaseVariableValue(this.subBlocks[1].getLifeCycleVariable(), State.IDLE);

        // fourth part: B1 error --> B1 IDLE and B3 ENABLED
        ConjunctiveSelectQuery fourthGuard = new ConjunctiveSelectQuery();
        fourthGuard.addBinaryCondition(BinaryConditionProvider.equality(this.subBlocks[0].getLifeCycleVariable(), this.error));
        InsertTransition fourthUpdate = new InsertTransition(this.name + " fourth translation", fourthGuard, this.dataSchema);
        fourthUpdate.setControlCaseVariableValue(this.subBlocks[0].getLifeCycleVariable(), State.IDLE);
        fourthUpdate.setControlCaseVariableValue(this.subBlocks[2].getLifeCycleVariable(), State.ENABLED);

        // fifth part:  B3 completed --> B3 IDLE and itself COMPLETED
        ConjunctiveSelectQuery fifthGuard = new ConjunctiveSelectQuery();
        fifthGuard.addBinaryCondition(BinaryConditionProvider.equality(this.subBlocks[2].getLifeCycleVariable(), State.COMPLETED));
        InsertTransition fifthUpdate = new InsertTransition(this.name + " fifth translation", fifthGuard, this.dataSchema);
        fifthUpdate.setControlCaseVariableValue(this.subBlocks[2].getLifeCycleVariable(), State.IDLE);
        fifthUpdate.setControlCaseVariableValue(this.lifeCycle, State.COMPLETED);

        // sixth part, non deterministic error
        //ToDo: add T_{err_3} from translation, as "active" and "waiting" in this code are the same
        ConjunctiveSelectQuery sixthGuard = new ConjunctiveSelectQuery();
        sixthGuard.addBinaryCondition(BinaryConditionProvider.equality(this.subBlocks[0].getLifeCycleVariable(), State.ENABLED));
        InsertTransition nondeterministicUpdate = new InsertTransition(this.name + " sixth translation", sixthGuard, this.dataSchema);
        nondeterministicUpdate.setControlCaseVariableValue(this.subBlocks[0].getLifeCycleVariable(), this.error);
        startPropagation(this.subBlocks[0], nondeterministicUpdate);

        // generate MCMT translation
        result += firstUpdate.getMCMTTranslation() + "\n" + secondUpdate.getMCMTTranslation() + "\n" + thirdUpdate.getMCMTTranslation() + "\n" + fourthUpdate.getMCMTTranslation() + "\n" + fifthUpdate.getMCMTTranslation() + "\n" + nondeterministicUpdate.getMCMTTranslation() + "\n";
        nondeterministicUpdate.setGuard("(= " + this.subBlocks[0].getLifeCycleVariable().getName() + " Active)");
        nondeterministicUpdate.setName(this.name + " seventh translation");
        result += nondeterministicUpdate.getMCMTTranslation() + "\n";

        return result;
    }


    private void startPropagation(Block handler, InsertTransition transition) throws InvalidInputException, UnmatchingSortException {
        for (Block sub : handler.getSubBlocks())
            propagateError(sub, transition);
    }


    private void propagateError(Block current, InsertTransition transition) throws InvalidInputException, UnmatchingSortException {
        if (current instanceof Task) {
            transition.setControlCaseVariableValue(current.getLifeCycleVariable(), State.IDLE);
            return;
        }
        transition.setControlCaseVariableValue(current.getLifeCycleVariable(), State.IDLE);
        for (Block sub : current.getSubBlocks())
            propagateError(sub, transition);

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

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof DABForwardExceptionBlock))
            return false;
        DABForwardExceptionBlock obj = (DABForwardExceptionBlock) o;
        return name.equals(obj.getName()) && lifeCycle.equals(obj.getLifeCycleVariable());
    }
}
