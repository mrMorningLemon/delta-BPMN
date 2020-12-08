package it.unibz.deltabpmn.verification.mcmt;

import it.unibz.deltabpmn.exception.EevarOverflowException;
import it.unibz.deltabpmn.exception.InvalidInputException;
import it.unibz.deltabpmn.exception.UnmatchingSortException;

/**
 * Interface specifying basic methods used for translating DAB components into MCMT code.
 */
public interface MCMTTranslatable {
    
    String getMCMTTranslation() throws InvalidInputException, UnmatchingSortException, EevarOverflowException;

}
