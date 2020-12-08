package it.unibz.deltabpmn.datalogic;

import it.unibz.deltabpmn.dataschema.elements.Attribute;
import it.unibz.deltabpmn.dataschema.elements.CaseVariable;
import it.unibz.deltabpmn.dataschema.elements.Constant;
import it.unibz.deltabpmn.exception.InvalidInputException;
import it.unibz.deltabpmn.exception.UnmatchingSortException;

/**
 * An interface defining a transition that can update case variables using dedicated setters
 * (that is, expressions in the {@code SET x_1=v_1,...,x_n=v_n} parts of {@code INSERT} and {@code DELETE} transitions).
 */
public interface ComplexTransition extends Transition{

    void setControlCaseVariableValue(CaseVariable variable, Constant newValue) throws InvalidInputException, UnmatchingSortException;

    void setControlCaseVariableValue(CaseVariable variable, Attribute attr) throws InvalidInputException, UnmatchingSortException;
}
