package it.unibz.deltabpmn.processschema.blocks.providers;

import it.unibz.deltabpmn.datalogic.ConjunctiveSelectQuery;
import it.unibz.deltabpmn.processschema.blocks.ErrorBlock;

/**
 * An interface for a factory used to generate {@code ErrorBlock} objects.
 */
public interface ErrorBlockProvider {

    //    ErrorBlock newErrorBlock(String name, Block handler);
    //
    //    ErrorBlock newErrorBlock(String name, Block handler, ConjunctiveSelectQuery cond);

    ErrorBlock newErrorBlock(String name);

    ErrorBlock newErrorBlock(String name, ConjunctiveSelectQuery cond);

}
