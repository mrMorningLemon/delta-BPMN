package it.unibz.deltabpmn.dataschema.core;

import com.healthmarketscience.sqlbuilder.dbspec.basic.DbSchema;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbSpec;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbTable;
import it.unibz.deltabpmn.dataschema.elements.*;
import it.unibz.deltabpmn.dataschema.elements.providers.*;
import it.unibz.deltabpmn.processschema.core.State;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DataSchema implements SortProvider, ConstantProvider, RepositoryRelationProvider, CatalogRelationProvider, CaseVariableProvider {
    private static DataSchema dataSchema = new DataSchema();
    //a collection of all generated sorts (required for preserving the uniqueness of every created Sort object)
    private Map<String, Sort> sorts;
    private Map<String, Constant> constants;
    private Map<String, CatalogRelation> catalog;
    private Map<String, RepositoryRelation> repository;
    private Map<String, CaseVariable> caseVariables;
    private Map<String, Attribute> attributes;
    private DbSpec spec;
    private DbSchema schema;


    private DataSchema() {
        //1. initialize all the components needed for sorts
        this.sorts = new HashMap<String, Sort>();
        initializeSorts();
        //2. add all the constants required by the general encoding
        this.constants = new HashMap<String, Constant>();
        intializeConstants();
        //3. initialize all the components needed for managing relations
        this.catalog = new HashMap<String, CatalogRelation>();
        this.repository = new HashMap<String, RepositoryRelation>();
        this.spec = new DbSpec();
        this.schema = spec.addDefaultSchema();
        //4. initialize all the case variables
        this.caseVariables = new HashMap<String, CaseVariable>();
        //a map for collecting data about the attributes of all the relations
        this.attributes = new HashMap<>();
    }

    /**
     * @return The data schema object.
     */
    public static DataSchema getInstance() {
        return dataSchema;
    }

    public Map<String, Attribute> getAllAttributes() {
        if (this.attributes.isEmpty()) {
            for (CatalogRelation catalogRelation : getCatalogRelations())//populate a map containing all attributes
                catalogRelation.getAttributes().stream().forEach(attr -> this.attributes.put(attr.getName(), attr));
            for (RepositoryRelation repositoryRelation : getRepositoryRelations())//populate a map containing all attributes
                repositoryRelation.getAttributes().stream().forEach(attr -> this.attributes.put(attr.getName(), attr));
        }
        return this.attributes;
    }

    @Override
    /**
     * If a sort does not exist, then the method creates a new one.
     * Otherwise, it returns a link to an already created {@code Sort} object.
     * @param name A sort name.
     * @return A {@code Sort} object.
     */
    public Sort newSort(String name) {
        if (sorts.containsKey(name))
            return sorts.get(name);
        else {
            Sort sort = new DABSort(name);
            sorts.put(name, sort);
            return sort;
        }
    }

    @Override
    /**
     * If a constant does not exist, then the method creates a new one.
     * Otherwise, it returns a link to an already created {@code Constant} object.
     *
     * @param name A constant name.
     * @param sort A constant sort.
     * @return A {@code Constant} object.
     */
    public Constant newConstant(String name, Sort sort) {
        if (constants.containsKey(name))
            return constants.get(name);
        else {
            Constant c = new DABConstant(name, sort);
            constants.put(name, c);
            return c;
        }
    }

    /**
     * A method that adds basic sorts that have to be specified in every MCMT program.
     */
    private void initializeSorts() {
        sorts.put(SystemSorts.BOOL.getSortName(), SystemSorts.BOOL);
        sorts.put(SystemSorts.INT.getSortName(), SystemSorts.INT);
        sorts.put(SystemSorts.STRING.getSortName(), SystemSorts.STRING);
    }

    /**
     * A method that adds constants that have to be specified in every MCMT program working with DABs.
     */
    private void intializeConstants() {
        newConstant(State.IDLE.getName(), State.IDLE.getSort());
        newConstant(State.ENABLED.getName(), State.ENABLED.getSort());
        newConstant(State.ACTIVE.getName(), State.ACTIVE.getSort());
        newConstant(State.COMPLETED.getName(), State.COMPLETED.getSort());
        newConstant(State.ACTIVE_SINGLE.getName(), State.ACTIVE_SINGLE.getSort());
        newConstant(State.ACTIVE_ALL.getName(), State.ACTIVE_ALL.getSort());
        newConstant(SystemConstants.TRUE.getName(), SystemConstants.FALSE.getSort());
        newConstant(SystemConstants.FALSE.getName(), SystemConstants.FALSE.getSort());
        //newConstant(SystemConstants.NULL.getName(), SystemConstants.NULL.getSort());
    }

    @Override
    /**
     * Creates a Catalog relation. If a relation does not exist, then the method creates a new one.
     * Otherwise, it returns a link to an already created {@code DABCatalogRelation} object.
     *
     * @param name A {@code DABCatalogRelation} relation name.
     * @return A {@code DABCatalogRelation} relation object.
     */
    public CatalogRelation newCatalogRelation(String name) {
        if (this.catalog.containsKey(name))
            return this.catalog.get(name);
        else {
            CatalogRelation catRelation = new DABCatalogRelation(name, this);
            this.catalog.put(name, catRelation);
            return catRelation;
        }
    }

    @Override
    /**
     * Creates a Repository relation. If a relation does not exist, then the method creates a new one.
     * Otherwise, it returns a link to an already created {@code DABRepositoryRelation} object.
     *
     * @param name A {@code DABRepositoryRelation} relation name.
     * @return A {@code DABRepositoryRelation} object.
     */
    public RepositoryRelation newRepositoryRelation(String name) {
        if (this.repository.containsKey(name))
            return this.repository.get(name);
        else {
            DABRepositoryRelation repRelation = new DABRepositoryRelation(name, this);
            this.repository.put(name, repRelation);
            return repRelation;
        }
    }

    @Override
    /**
     * If a case variable does not exist, then the method creates a new one.
     * Otherwise, it returns a link to an already created {@code DABCaseVariable} object.
     *
     * @param name A case variable name.
     * @param sort A case variable sort.
     * @param type {@code true} is the case variable is used in the one-case setting. {@code false} otherwise.
     * @return A {@CaseVariable} object.
     */
    public CaseVariable newCaseVariable(String name, Sort sort, boolean type) {
        if (this.caseVariables.containsKey(name))
            return caseVariables.get(name);
        else {
            CaseVariable variable = new DABCaseVariable(name, sort, type);
            caseVariables.put(name, variable);
            return variable;
        }
    }

    /**
     * @return A a list of all sorts contained in the data schema.
     */
    public List<Sort> getSorts() {
        return new ArrayList<Sort>(this.sorts.values());
    }

    /**
     * @return A map of all constants (together with their string representations) contained in the data schema.
     */
    public Map<String, Constant> getConstants() {
        return this.constants;
    }

    /**
     * @return A  list of all Catalog relations contained in the data schema.
     */
    public List<CatalogRelation> getCatalogRelations() {
        return new ArrayList<CatalogRelation>(this.catalog.values());
    }

    /**
     *
     * @return A map with (catalog relation name, object) pairs
     */
    public Map<String, CatalogRelation> getCatalogRelationAssociations() {
        return this.catalog;
    }


    /**
     * @return A list of all Repository relations contained in the data schema.
     */
    public List<RepositoryRelation> getRepositoryRelations() {
        return new ArrayList<RepositoryRelation>(this.repository.values());
    }

    /**
     *
     * @return A map with (repository relation name, object) pairs
     */
    public Map<String, RepositoryRelation> getRepositoryRelationAssociations() {
        return this.repository;
    }

    /**
     * @return A list of all case variables contained in the data schema.
     */
    public List<CaseVariable> getCaseVariables() {
        return new ArrayList<CaseVariable>(this.caseVariables.values());
    }

    /**
     * @return A map of (CaseVariable_Name,CaseVariable_Object) pairs
     */
    public Map<String, CaseVariable> getCaseVariableAssociations() {
        return this.caseVariables;
    }

    /**
     * A method that updates the database schema component
     * {@link com.healthmarketscience.sqlbuilder.dbspec.basic.DbSchema} of the given data schema
     * by creating a table and adding it to the database schema component.
     *
     * @param tableName The table name.
     * @return The created table.
     */
    public DbTable addTableToDbSchema(String tableName) {
        return this.schema.addTable(tableName);
    }
}
