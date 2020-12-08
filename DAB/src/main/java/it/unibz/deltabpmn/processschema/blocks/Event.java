package it.unibz.deltabpmn.processschema.blocks;

import it.unibz.deltabpmn.datalogic.InsertTransition;

public interface Event extends Block {

    void addTransition(InsertTransition eff);

    InsertTransition getTransition();

    boolean hasEffect();
}