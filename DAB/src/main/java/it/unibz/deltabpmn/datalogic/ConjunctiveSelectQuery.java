package it.unibz.deltabpmn.datalogic;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbTable;
import it.unibz.deltabpmn.dataschema.core.SystemConstants;
import it.unibz.deltabpmn.dataschema.elements.Attribute;
import it.unibz.deltabpmn.dataschema.elements.CatalogRelation;
import it.unibz.deltabpmn.dataschema.elements.Relation;
import it.unibz.deltabpmn.dataschema.elements.RepositoryRelation;
import it.unibz.deltabpmn.exception.EevarOverflowException;

import java.util.*;


/**
 * A class for creating conjunctive queries using the SQL builder library. It also takes care of
 * the MCMT translation of the conjunctive query to build the guard of a transition, condition of blocks or safety property
 */
public class ConjunctiveSelectQuery {

    private SelectQuery selectQuery;
    private Map<String, DbTable> tables;
    private String MCMTRepresentation = "";
    private String negatedMCMTRepresentation = "";
    private Map<String, String> refManager; // key is an internal name, value is a global eevar
    private boolean indexPresent = false;
    private Attribute[] selectedAttributes; //attributes that follow the SELECT statement
    private List<Relation> fromRelations;


    //ToDo: make one single constructor + add methods for adding attributes into the SELECT clause
    /**
     * @param attributes Attributes that represent the select part of the query (that is, answer variables).
     *                   The attributes are analyzed to adjust the eevar management
     *                   (an eevar are added if an attribute is a primary key or an attribute of some catalog relation).
     */
    public ConjunctiveSelectQuery(Attribute... attributes) throws EevarOverflowException {
        this.selectedAttributes = attributes;
        this.fromRelations = new ArrayList<Relation>();
        this.tables = new HashMap<String, DbTable>();
        this.selectQuery = new SelectQuery();
        this.refManager = new HashMap<String, String>();

        for (Attribute att : attributes) {
            this.selectQuery.addColumns(att.getDbColumn());

            // check if the table that contains the attribute is already written
            if (!tables.containsKey(att.getDbColumn().getTable().getAlias())) {
                addFrom(att.getRelation());
                this.tables.put(att.getDbColumn().getTable().getAlias(), att.getDbColumn().getTable());
            }

            // add the e-variable if a column in select clause is not primary key
            if (!att.getFunctionalView().equals("") && (att.getRelation() instanceof CatalogRelation)) {
                addReferenceVariable(att);
                this.MCMTRepresentation += "(= (" + att.getFunctionalView() + " "
                        + this.refManager.get(((CatalogRelation) att.getRelation()).getPrimaryKey().getName()) + ") " + this.refManager.get(att.getName())
                        + ") ";
            }
        }
    }

    /**
     * Constructs a conjunctive query that contains only case variables (equivalent to a sequence of binary expressions connected with AND)
     */
    public ConjunctiveSelectQuery() {
        this.selectedAttributes = new Attribute[0];
        this.fromRelations = new ArrayList<Relation>();
        this.tables = new HashMap<String, DbTable>();
        this.selectQuery = new SelectQuery();
        this.refManager = new HashMap<String, String>();
    }

    /**
     * A method that adds a binary condition to the conjunctive query.
     *
     * @param condition The binary condition that uses
     */
    public void addBinaryCondition(it.unibz.deltabpmn.datalogic.BinaryCondition condition) {

        String leftMCMT = condition.getLeft().getName();// + "";
        String rightMCMT = condition.getRight().getName();// + "";

        Object left;
        Object right;

        // Check if operands of the binary condition are attributes.
        // If an operand is an attribute, then extract a DB column corresponding to it.
        if (condition.getLeft() instanceof Attribute) {
            Attribute leftAttr = (Attribute) condition.getLeft();
            left = leftAttr.getDbColumn();
            //ToDo: what is this part doing?
            if (this.refManager.containsKey(leftAttr.getName()))
                leftMCMT = "" + this.refManager.get(leftAttr.getName());
            else
                leftMCMT = "" + leftAttr.getMCMTDeclarationWithEEVAR(this.refManager.get(((CatalogRelation) leftAttr.getRelation()).getPrimaryKey().getName()));
        } else
            left = condition.getLeft();

        if (condition.getRight() instanceof Attribute) {
            Attribute rightAttr = (Attribute) condition.getRight();
            right = rightAttr.getDbColumn();
            if (this.refManager.containsKey(rightAttr.getName()))
                rightMCMT = "" + this.refManager.get(rightAttr.getName());
            else
                rightMCMT = "" + rightAttr.getMCMTDeclarationWithEEVAR(this.refManager.get(((CatalogRelation) rightAttr.getRelation()).getPrimaryKey().getName()));
        } else
            right = condition.getRight();

        //String conditionToMCMT = "(= " + leftMCMT + " " + rightMCMT + ")";
        String conditionToMCMT = null;
        switch (condition.getOperator()) {
            case EQUALITY:
                conditionToMCMT = "(= " + leftMCMT + " " + rightMCMT + ")";
                this.selectQuery.addCondition(BinaryCondition.equalTo(left, right));
                this.MCMTRepresentation += conditionToMCMT + " ";
                this.negatedMCMTRepresentation += "(not " + conditionToMCMT + ") ";
                break;
            case INEQUALITY:
                conditionToMCMT = "(= " + leftMCMT + " " + rightMCMT + ")";
                this.selectQuery.addCondition(BinaryCondition.notEqualTo(left, right));
                this.MCMTRepresentation += "(not " + conditionToMCMT + ") ";
                this.negatedMCMTRepresentation += conditionToMCMT + " ";
                break;
            case LESS_THAN:
                conditionToMCMT = "(< " + leftMCMT + " " + rightMCMT + ")";
                this.selectQuery.addCondition(BinaryCondition.lessThan(left, right));
                this.MCMTRepresentation += conditionToMCMT + " ";
                this.negatedMCMTRepresentation += "(not " + conditionToMCMT + ") ";
                break;
            case GREATER_THAN:
                conditionToMCMT = "(> " + leftMCMT + " " + rightMCMT + ")";
                this.selectQuery.addCondition(BinaryCondition.lessThan(left, right));
                this.MCMTRepresentation += conditionToMCMT + " ";
                this.negatedMCMTRepresentation += "(not " + conditionToMCMT + ") ";
                break;
        }
    }


    /**
     * A method for adding a relation to the FROM part of the conjunctive query.
     *
     * @param relation The relation to be added to the FROM clause.
     */
    private void addFrom(Relation relation) throws EevarOverflowException {
        if (relation instanceof CatalogRelation) {
            addFrom((CatalogRelation) relation);
        } else {
            addFrom((RepositoryRelation) relation);
        }
    }

    /**
     * A method for adding a catalog relation to the FROM part of the conjunctive query.
     * *
     *
     * @param relation The catalog relation to be added to the FROM clause.
     */
    private void addFrom(CatalogRelation relation) throws EevarOverflowException {
        DbTable table = relation.getDbTable();

        if (!tables.containsKey(table.getAlias())) {
            this.tables.put(table.getAlias(), table);
            this.selectQuery.addFromTable(table);

            // prova
            addReferenceVariable(relation.getPrimaryKey());
            this.MCMTRepresentation += "(not (= " + this.refManager.get(relation.getPrimaryKey().getName())
                    + SystemConstants.NULL.getName() + "_"
                    + relation.getPrimaryKey().getSort().getSortName() +
                    ")) ";
        }
    }

    /**
     * Method for adding a repository relation to the FROM part of the conjunctive query.
     *
     * @param relation The repository relation to be added to the FROM clause.
     */
    private void addFrom(RepositoryRelation relation) throws EevarOverflowException {
        DbTable table = relation.getDbTable();
        if (!tables.containsKey(table.getAlias())) {
            this.tables.put(table.getAlias(), table);
            this.selectQuery.addFromTable(table);

            for (int i = 0; i < relation.getAttributes().size(); i++) {
                addReferenceVariable(relation.getAttributeByIndex(i));

                this.MCMTRepresentation += "(= " + relation.getName() + (i + 1) + "[x] " + this.refManager.get(relation.getAttributeByIndex(i).getName()) + ") ";
                setIndexPresent(true);
            }
        }
    }

//    /**
//     * @return A string representing the validated version of the conjunctive query as a SQL SELECT query.
//     */
//    public String getQueryStringRepresentation() {
//        return this.selectQuery.validate().toString();
//    }

//    /***
//     * @return A SQL version of the conjunctive query.
//     */
//    public SelectQuery getSelectQuery() {
//        return this.selectQuery;
//    }


//    /**
//     * @return A set of pairs, where the first element is a table/relation name, and the second element is the table object.
//     * The considered tables/relations are those appearing in the FORM clause of the conjunctive query.
//     */
//    public Map<String, DbTable> getTables() {
//        return this.tables;
//    }
//

    /**
     * @return A string representing an MCMT translation of the conjunctive select query, i.e., a conjunction of equalities/inequalities.
     */
    public String getMCMTTranslation() {
        return this.MCMTRepresentation;
    }

    //ToDo: the index is never manipulated in the main methods of the class

    /**
     * @return {@code True}, if there is an index present in the query; {@code False}, otherwise.
     */
    public boolean isIndexPresent() {
        return this.indexPresent;
    }

    /**
     * A method that sets an internal variable indicating whether there is an index in the query.
     *
     * @param indexPresent A boolean value that is {@code True}, if there is an index present in the query; {@code False}, otherwise.
     */
    private void setIndexPresent(boolean indexPresent) {
        this.indexPresent = indexPresent;
    }


    //ToDo: specify what are the eevar associations

    /**
     * @return A map with eevar associations.
     */
    public Map<String, String> getRefManager() {
        return refManager;
    }

    //ToDo: what does this method do?

    /**
     * @return
     */
    public String getNegatedMCMT() {
        return this.negatedMCMTRepresentation;
    }

    /**
     * A method that performs the eevar association process.
     *
     * @param att The attribute for which the association process is performed.
     * @throws EevarOverflowException
     */
    private void addReferenceVariable(Attribute att) throws EevarOverflowException {
        // 1 ) check in the reference manager
        if (this.refManager.containsKey(att.getName()))
            return;

        // 2 ) check if in the global manager there are eevar with that sort
        Collection<String> eevarAvailable = EevarManager.getEevarWithSort(att.getSort());

        if (eevarAvailable.isEmpty()) {
            // add eevar to the global eevar manager
            String global_reference = EevarManager.addEevar(att.getSort());
            // add association locally
            this.refManager.put(att.getName(), global_reference);
        }
        // 3 process the array and look if one is free (it means it is not in the local map)
        else {
            for (String glb_name : eevarAvailable) {
                // case in which current one is not already used, I can use it
                if (!this.refManager.containsValue(glb_name)) {
                    this.refManager.put(att.getName(), glb_name);
                    return;
                }
            }
            // case in which all eevar already used
            String global_reference = EevarManager.addEevar(att.getSort());
            this.refManager.put(att.getName(), global_reference);
        }
    }


}