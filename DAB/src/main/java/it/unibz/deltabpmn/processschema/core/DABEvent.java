package it.unibz.deltabpmn.processschema.core;

import it.unibz.deltabpmn.datalogic.InsertTransition;
import it.unibz.deltabpmn.dataschema.core.SystemSorts;
import it.unibz.deltabpmn.dataschema.elements.CaseVariable;
import it.unibz.deltabpmn.dataschema.core.DataSchema;
import it.unibz.deltabpmn.exception.InvalidInputException;
import it.unibz.deltabpmn.exception.UnmatchingSortException;
import it.unibz.deltabpmn.processschema.blocks.Block;
import it.unibz.deltabpmn.processschema.blocks.Event;

class DABEvent implements Event {
    private String name;
    private CaseVariable lifeCycle;
    private Block[] subBlocks;
    private InsertTransition effect = null;
    private DataSchema dataSchema;

    public DABEvent(String name, DataSchema schema) {
        this.name = name;
        this.subBlocks = new Block[0];
        this.dataSchema = schema;
        this.lifeCycle = this.dataSchema.newCaseVariable("lifecycle_" + name, SystemSorts.STRING, true);
        this.lifeCycle.setLifeCycle(1);
    }

    public DABEvent(String name, InsertTransition eff, DataSchema schema) {
        this.name = name;
        this.effect = eff;
        this.subBlocks = new Block[0];
        this.lifeCycle = this.dataSchema.newCaseVariable("lifecycle_" + name, SystemSorts.STRING, true);
        this.lifeCycle.setLifeCycle(1);
    }

    @Override
    public void addTransition(InsertTransition eff) {
        this.effect = eff;
    }

    @Override
    public InsertTransition getTransition() {
        return this.effect;
    }

    @Override
    public boolean hasEffect() {
        if (this.effect != null)
            return true;
        return false;
    }

    //ToDo: add a guard that checks whether it's enabled and then add an effect that says that the event is "completed"
    @Override
    public String getMCMTTranslation() throws InvalidInputException, UnmatchingSortException {
        String result = "";
        // control if this event has an effect
        if (this.effect != null)
            result += this.effect.getMCMTTranslation();//event block has no states, thus just add the update (if exists)
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
