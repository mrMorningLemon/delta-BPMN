package it.unibz.deltabpmn.bpmn;

import it.unibz.deltabpmn.dataschema.elements.CaseVariable;
import it.unibz.deltabpmn.exception.EevarOverflowException;
import it.unibz.deltabpmn.exception.InvalidInputException;
import it.unibz.deltabpmn.exception.UnmatchingSortException;
import it.unibz.deltabpmn.processschema.blocks.Block;

public class DeferredANDSplitGate implements Block {

    private final String name = "(+)_F";
    private String id;


    public DeferredANDSplitGate(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    @Override
    public CaseVariable getLifeCycleVariable() {
        try {
            throw new UnsupportedOperationException();
        } catch (UnsupportedOperationException ex) {
            System.out.println("Method not supported for AND gates [" + this.id + "]: they can't set case variable values!");
        }
        return null;
    }

    @Override
    public String getName() {
        return this.name + ":" + this.id;
    }

    @Override
    public Block[] getSubBlocks() {
        try {
            throw new UnsupportedOperationException();
        } catch (UnsupportedOperationException ex) {
            System.out.println("Method not supported for AND gates [" + this.id + "]: they can't set case variable values!");
        }
        return null;
    }

    @Override
    public String getMCMTTranslation() throws InvalidInputException, UnmatchingSortException, EevarOverflowException {
        try {
            throw new UnsupportedOperationException();
        } catch (UnsupportedOperationException ex) {
            System.out.println("Method not supported for AND gates [" + this.id + "]: they can't set case variable values!");
        }
        return null;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public int hashCode() {
        return name.hashCode() + 31 * id.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof DeferredANDSplitGate))
            return false;
        DeferredANDSplitGate obj = (DeferredANDSplitGate) o;
        return id.equals(obj.getId());
    }
}


