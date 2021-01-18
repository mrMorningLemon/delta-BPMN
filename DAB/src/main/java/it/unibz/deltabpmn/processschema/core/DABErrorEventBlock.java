package it.unibz.deltabpmn.processschema.core;

import it.unibz.deltabpmn.datalogic.BinaryConditionProvider;
import it.unibz.deltabpmn.datalogic.ComplexTransition;
import it.unibz.deltabpmn.datalogic.ConjunctiveSelectQuery;
import it.unibz.deltabpmn.datalogic.InsertTransition;
import it.unibz.deltabpmn.dataschema.core.DataSchema;
import it.unibz.deltabpmn.dataschema.core.SystemSorts;
import it.unibz.deltabpmn.dataschema.elements.CaseVariable;
import it.unibz.deltabpmn.exception.EevarOverflowException;
import it.unibz.deltabpmn.exception.InvalidInputException;
import it.unibz.deltabpmn.exception.UnmatchingSortException;
import it.unibz.deltabpmn.processschema.blocks.Block;
import it.unibz.deltabpmn.processschema.blocks.ErrorEventBlock;
import it.unibz.deltabpmn.processschema.blocks.Event;
import it.unibz.deltabpmn.processschema.blocks.Task;
import it.unibz.deltabpmn.verification.mcmt.NameManager;


//ToDo: this is an optimization block that we shouldn't consider in the first version of the translator!
public class DABErrorEventBlock implements ErrorEventBlock {

    private String name;
    private CaseVariable lifeCycle;
    private Block[] subBlocks;
    private DataSchema dataSchema;
    private Block handler;

    public DABErrorEventBlock(String name, Block handler, DataSchema schema) {
        this.name = NameManager.normaliseName(name);
        this.handler = handler;
        this.subBlocks = new Block[2];
        this.dataSchema = schema;
        this.lifeCycle = this.dataSchema.newCaseVariable("lifecycle" + this.name, SystemSorts.STRING, true);
        this.lifeCycle.setLifeCycle(1);
    }

    @Override
    public void addFirstBlock(Event b) {
        this.subBlocks[0] = b;
    }

    @Override
    public void addSecondBlock(Event b) {
        this.subBlocks[1] = b;
    }


    @Override
    public String getMCMTTranslation() throws InvalidInputException, UnmatchingSortException, EevarOverflowException {
        String result = "";

        // first part: event B1 enabled --> B1 COMPLETED and UPDATE
        ComplexTransition firstU = null;
        if (((Event) this.subBlocks[0]).hasEffect()) {
            firstU = ((Event) this.subBlocks[0]).getTransition();
            firstU.addTaskGuard("(= " + this.subBlocks[0].getLifeCycleVariable().getName() + " Enabled)");
            firstU.setControlCaseVariableValue(this.subBlocks[0].getLifeCycleVariable(), State.COMPLETED);
        } else {
            ConjunctiveSelectQuery firstG = new ConjunctiveSelectQuery();
            firstG.addBinaryCondition(BinaryConditionProvider.equality(this.subBlocks[0].getLifeCycleVariable(), State.ENABLED));
            firstU = new InsertTransition(this.name + " first translation", firstG, this.dataSchema);
            firstU.setControlCaseVariableValue(this.subBlocks[0].getLifeCycleVariable(), State.COMPLETED);
        }

        // second part: event B1 enabled --> Error propagation and possible update
        ComplexTransition secondU = null;
        if (((Event) this.subBlocks[1]).hasEffect()) {
            secondU = ((Event) this.subBlocks[1]).getTransition();
            startPropagation(this.handler, secondU);
        } else {
            ConjunctiveSelectQuery secondG = new ConjunctiveSelectQuery();
            secondG.addBinaryCondition(BinaryConditionProvider.equality(this.subBlocks[1].getLifeCycleVariable(), State.ENABLED));
            secondU = new InsertTransition(this.name + " second translation", secondG, this.dataSchema);
            startPropagation(this.handler, secondU);
        }


        // generate MCMT translation
        result += firstU.getMCMTTranslation() + "\n" + secondU.getMCMTTranslation() + "\n";

        return result;

    }

    private void startPropagation(Block handler, ComplexTransition transition) throws InvalidInputException, UnmatchingSortException {
        transition.setControlCaseVariableValue(handler.getLifeCycleVariable(), this.dataSchema.newConstant("Error" + handler.getSubBlocks()[0].getName(), SystemSorts.STRING));
        for (Block block : handler.getSubBlocks())
            propagateError(block, transition);
    }

    private void propagateError(Block current, ComplexTransition transition) throws InvalidInputException, UnmatchingSortException {
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
