package it.unibz.deltabpmn.datalogic;


import it.unibz.deltabpmn.dataschema.core.DataSchema;
import it.unibz.deltabpmn.dataschema.core.SystemConstants;
import it.unibz.deltabpmn.dataschema.elements.Attribute;
import it.unibz.deltabpmn.dataschema.elements.CaseVariable;
import it.unibz.deltabpmn.dataschema.elements.Constant;
import it.unibz.deltabpmn.dataschema.elements.RepositoryRelation;
import it.unibz.deltabpmn.exception.InvalidInputException;
import it.unibz.deltabpmn.exception.UnmatchingSortException;

import java.util.Map;


/**
 * A class representing the DAB conditional update.
 * Conceptually, a conditional update object is represented as a tree consisting of inner nodes (conditions)
 * and leaves (each leaf is a set operations on a considered repository relation that has to be updated).
 */
public class BulkUpdate implements ComplexTransition {

    private ConjunctiveSelectQuery precondition;
    private Map<String, String> eevarAssociation;
    private String name;
    //ToDo: what's this toSet for?
    private CaseVariable toSet; // only used in case when there is a case variable to be changed, the lifecycle of a current task
    private String guard;
    private String finalMCMT = "";
    private int numCases = 0;
    private String caseUpdate = "";
    private RepositoryRelation toUpdate;
    public BulkCondition root;
    private DataSchema dataSchema;

    /***
     * @param name         The name of the transition.
     * @param precondition The precondition guard of the transition that defines the used eevars and the MCMT guard translation.
     * @param toUpdate     The repository relation that has to be updated.
     */
    public BulkUpdate(String name, ConjunctiveSelectQuery precondition, RepositoryRelation toUpdate, DataSchema dataSchema) throws InvalidInputException {
        this.precondition = precondition;
        this.eevarAssociation = precondition.getRefManager();
        this.guard = precondition.getMCMTTranslation();
        this.name = name;
        this.toUpdate = toUpdate;
        this.dataSchema = dataSchema;
        this.root = new BulkCondition(toUpdate, dataSchema);
        this.root.setEevarAssociation(this.eevarAssociation);
    }

    /***
     * @param name         The name of the transition.
     * @param toUpdate     The repository relation that has to be updated.
     */
    public BulkUpdate(String name, RepositoryRelation toUpdate, DataSchema dataSchema) throws InvalidInputException {
        this.precondition = null;
        this.eevarAssociation = precondition.getRefManager();
        this.guard = SystemConstants.TRUE.getName();
        this.name = name;
        this.toUpdate = toUpdate;
        this.dataSchema = dataSchema;
        this.root = new BulkCondition(toUpdate, dataSchema);
        this.root.setEevarAssociation(this.eevarAssociation);
    }

    //ToDo: this method never does anything with the value (WTF?)


    /**
     * This method is only used in cases when there is a case variable to be changed that represents the lifecycle variable of a given task.
     */
    public void defineLifecycleVariable(CaseVariable variable) {
        this.toSet = variable;
    }


    //ToDo: rename this method

    /**
     * A method for getting the string representing the list of immutable case variables. Since the update
     * involves only the repository relation, all case variables remain the same except from the control case variable.
     *
     * @return The string with case variables.
     */
    private String globalStatic() {
        String result = "";
        for (CaseVariable caseVar : dataSchema.getCaseVariables()) {
            if (caseVar == this.toSet)
                result += ":val Completed\n";
            else
                result += ":val " + caseVar.getName() + "\n";
        }
        return result;
    }

    //ToDo: come up with an interface that would have a getMCMTTranslation method that doesn't throw exceptions

    @Override
    /**
     * A method generating a translation of the bulk update into MCMT code.
     * In MCMT the bulk update is represented as a transition.
     *
     * @return String representing the MCMT translation of the bulk update.
     */
    public String getMCMTTranslation() throws InvalidInputException {
        this.finalMCMT += ":comment " + this.name + "\n:transition\n:var j\n";
        // control of the indexes
        if (this.precondition.isIndexPresent())
            this.finalMCMT += ":var x\n";
        this.finalMCMT += ":guard " + this.guard + "\n";

        //here to iterate through the three and print corresponding declaration
        processTree();
        this.finalMCMT += ":numcases " + this.numCases + "\n";
        this.finalMCMT += this.caseUpdate;

        return this.finalMCMT;
    }

    /**
     * A method for recursively traversing the tree and generating some MCMT code for the bulk update.
     */
    private void processTree() throws InvalidInputException {
        processTree(this.root, "");
    }

    /**
     * A method that recursively performs the DFS algorithm to process the tree and
     * generate some MCMT code corresponding to the bullk update.
     *
     * @param node      A current node of the tree.
     * @param condition An accumulator variable that keeps trace of conditions along the visited path.
     */
    private void processTree(BulkCondition node, String condition) throws InvalidInputException {
        //base case
        if (node.isLeaf()) {
            this.caseUpdate += ":case" + condition + "\n";
            this.caseUpdate += node.getLocalUpdateMCMTTranslation() + "\n";
            this.caseUpdate += this.globalStatic() + "\n";
            this.numCases++;
            return;
        }
        processTree(node.getTrueNode(), condition + node.getCondition());
        for (String s : node.getFalseList()) {
            processTree(node.getFalseNode(), condition + s);
        }
    }

    /**
     * @return The name of the conditional update.
     */
    public String getName() {
        return this.name;
    }

    @Override
    /**
     * A method for modifying the task guard adding some conditions. Useful in the process schema translation, for adding
     * information about control case variables.
     *
     * @param toAdd A string representing the condition to add.
     */
    public void addTaskGuard(String toAdd) {
        this.guard += toAdd;
    }

    @Override
    public void setControlCaseVariableValue(CaseVariable variable, Constant newValue) throws InvalidInputException, UnmatchingSortException {
        try {
            throw new UnsupportedOperationException();
        } catch (UnsupportedOperationException ex) {
            System.out.println("Method not supported for bulk update [" + this.name + "]: bulk updates can't set case variable values!");
        }
    }

    @Override
    public void setControlCaseVariableValue(CaseVariable variable, Attribute attr) throws InvalidInputException, UnmatchingSortException {
        try {
            throw new UnsupportedOperationException();
        } catch (UnsupportedOperationException ex) {
            System.out.println("Method not supported for bulk update [" + this.name + "]: bulk updates can't set case variable values!");
        }
    }
}