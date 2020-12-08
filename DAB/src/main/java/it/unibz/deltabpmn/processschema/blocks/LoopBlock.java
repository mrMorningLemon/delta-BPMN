package it.unibz.deltabpmn.processschema.blocks;


import it.unibz.deltabpmn.datalogic.ConjunctiveSelectQuery;

public interface LoopBlock extends Block{

    void addFirstBlock(Block b);

    void addSecondBlock(Block b);

    void addCondition(ConjunctiveSelectQuery cond);
}