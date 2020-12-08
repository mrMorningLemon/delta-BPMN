package it.unibz.deltabpmn.bpmn.parsers;

import it.unibz.deltabpmn.dataschema.core.DataSchema;
import it.unibz.deltabpmn.dataschema.elements.Term;

public class TermProcessor {

    /**
     * Process a string representation of a DAB term. A term can be an attribute,
     * a case variable, a constant or an integer (we separate integers from other constants
     * that have to be explicitly defined in MCMT as integers are natively supported with
     * related arithmetic predicates in MCMT).
     *
     * @param el         The string representation of the term to be processed.
     * @param dataSchema The data schema of the current data-aware process model.
     * @return
     */
    public static Term processTerm(String el, DataSchema dataSchema) {
        Term t = null;
        if (isAttribute(el, dataSchema))
            t = dataSchema.getAllAttributes().get(el);
        if (isConstant(el, dataSchema))
            t = dataSchema.getConstants().get(el);
        if (isCaseVariable(el, dataSchema))
            t = dataSchema.getCaseVariableAssociations().get(el);
        if (isInteger(el))
            t = new MCMTIntConstant(Integer.valueOf(el));
        return t;
    }

    private static boolean isAttribute(String el, DataSchema dataSchema) {
        return dataSchema.getAllAttributes().keySet().contains(el);
    }

    private static boolean isConstant(String el, DataSchema dataSchema) {
        return dataSchema.getConstants().keySet().contains(el);
    }

    private static boolean isCaseVariable(String el, DataSchema dataSchema) {
        return dataSchema.getCaseVariableAssociations().keySet().contains(el);
    }

    private static boolean isInteger(String el) {
        if (el == null) {
            return false;
        }
        try {
            double d = Integer.parseInt(el);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }
}