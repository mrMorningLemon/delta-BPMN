package it.unibz.deltabpmn.processschema.blocks;

import it.unibz.deltabpmn.datalogic.ConjunctiveSelectQuery;

public interface ErrorBlock extends Block {
    //void addBlock(Block b);

    void addCondition(ConjunctiveSelectQuery cond);

//    void startPropagation(Block handler, InsertTransition transition) throws InvalidInputException, UnmatchingSortException;

//    void propagateError(Block current, InsertTransition transition) throws InvalidInputException, UnmatchingSortException;
}
