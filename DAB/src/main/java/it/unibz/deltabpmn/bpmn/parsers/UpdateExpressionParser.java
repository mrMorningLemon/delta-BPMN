package it.unibz.deltabpmn.bpmn.parsers;

import it.unibz.deltabpmn.bpmn.utils.SQL.SelectParser;
import it.unibz.deltabpmn.datalogic.*;
import it.unibz.deltabpmn.dataschema.core.DataSchema;
import it.unibz.deltabpmn.dataschema.elements.Attribute;
import it.unibz.deltabpmn.dataschema.elements.CaseVariable;
import it.unibz.deltabpmn.dataschema.elements.Constant;
import it.unibz.deltabpmn.dataschema.elements.Term;
import it.unibz.deltabpmn.exception.InvalidInputException;
import it.unibz.deltabpmn.exception.UnmatchingSortException;
import javafx.util.Pair;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperties;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class UpdateExpressionParser {

    private static final String effKey = "eff";
    private static final String preKey = "pre";
    private static final String varKey = "var";


    public static ComplexTransition parse(String taskName, ExtensionElements extensionElements, DataSchema dataSchema) throws Exception {
        String precondition = "";
        List<String> effects = new ArrayList<>();
        List<String> newVariableDeclarations = new ArrayList<>();//list of variable declaration appearing in var clauses
        //1. extract all the elements of the update expression
        Collection<CamundaProperty> properties = extensionElements.getElementsQuery()
                .filterByType(CamundaProperties.class)
                .singleResult()
                .getCamundaProperties();
        for (CamundaProperty property : properties) {
            String name = property.getCamundaName();
            String value = property.getCamundaValue();
            if (name.equals(preKey))
                precondition += value;
            if (name.equals(effKey))
                effects.add(value);
            if (name.equals(varKey))
                newVariableDeclarations.add(value);
        }

        //2. start by adding variable declarations (if any exist)
        for (String varDeclaration : newVariableDeclarations)
            dataSchema = parseVariableDeclarations(varDeclaration, dataSchema);
        //3. parse the precondition
        ConjunctiveSelectQuery query = null;
        if (!precondition.trim().equals("TRUE")) {
            if (precondition.trim().isEmpty())
                System.out.println("empty precondition from task/event " + taskName);
//                query = new ConjunctiveSelectQuery();//
            else
                query = parsePrecondition(precondition, dataSchema);
        }
        //4. parse one of the effect types and generate the final transition object (NB: if we have only variables to set, then this is going to be an insert transition; otherwise, variable updates have to be added to a created transition)
        //ToDo always check if query is null
        ComplexTransition transition = null;
        String mainStatement = effects.stream().filter(str -> str.contains("INSERT")).findAny().orElse("") +
                effects.stream().filter(str -> str.contains("DELETE")).findAny().orElse("") +
                effects.stream().filter(str -> str.contains("UPDATE")).findAny().orElse("");

        //if (effects.stream().anyMatch(str -> str.contains("INSERT")))
        if (mainStatement.contains("INSERT")) {
            if (query == null)
                transition = new InsertTransition(taskName + "INSERT", dataSchema);
            else
                transition = new InsertTransition(taskName + "INSERT", query, dataSchema);
            Pair<Term[], String> insertComponents = parseInsertExpression(mainStatement, dataSchema);
            ((InsertTransition) transition).insert(dataSchema.getRepositoryRelationAssociations().get(insertComponents.getValue()), insertComponents.getKey());
        }
        //if (effects.stream().anyMatch(str -> str.contains("DELETE")))
        if (mainStatement.contains("DELETE")) {
            if (query == null)
                transition = new DeleteTransition(taskName + "DELETE", dataSchema);
            else
                transition = new DeleteTransition(taskName + "DELETE", query, dataSchema);
            Pair<Term[], String> deleteComponents = parseDeleteExpression(mainStatement, dataSchema);
            ((DeleteTransition) transition).delete(dataSchema.getRepositoryRelationAssociations().get(deleteComponents.getValue()), deleteComponents.getKey());
        }
        //if (effects.stream().anyMatch(str -> str.contains("UPDATE")))
        if (mainStatement.contains("UPDATE")) {
            //manage the update statement
            transition = parseUpdateExpression(query, taskName + "UPDATE", mainStatement, dataSchema);
        }

        //remove from effects the processes string and generate
        effects.remove(mainStatement);

        //deal with process variable updates (if any)
        if (transition == null) {//if transition hasn't been created, create a simple insert transition (the type of transition doesn't matter as we need to update variable values only)
            if (query == null)
                transition = new InsertTransition(taskName + "SET", dataSchema);
            else transition = new InsertTransition(taskName + "SET", query, dataSchema);
        }
        for (String update : effects) {
            transition = parseVariableUpdate(update, dataSchema, transition);
        }
        return transition;
    }


    private static ComplexTransition parseVariableUpdate(String effect, DataSchema dataSchema, ComplexTransition transition) throws Exception {
        String[] operands = effect.split("=");
        CaseVariable first = (CaseVariable) TermProcessor.processTerm(operands[0].trim(), dataSchema);//first element is always # for case variables
        Term second = TermProcessor.processTerm(operands[1].trim(), dataSchema);
        if (second instanceof Constant)
            transition.setControlCaseVariableValue(first, (Constant) second);
        else if (second instanceof Attribute)
            transition.setControlCaseVariableValue(first, (Attribute) second);
        else if (second instanceof CaseVariable)
            transition.setControlCaseVariableValue(first, (CaseVariable) second);
        else if (second == null) {
            //else, it's a constant that we don't know about ==> add it to dataSchema
            Constant c = dataSchema.newConstant(operands[1].trim(), first.getSort());
            transition.setControlCaseVariableValue(first, c);
        }
        return transition;
    }

    //returns a pair consisting of values to be deleted and the name of the relation appearing in the FROM clause
    private static Pair<Term[], String> parseDeleteExpression(String deleteExpr, DataSchema dataSchema) throws Exception {
        Pattern argumentsPattern = Pattern.compile("DELETE(.*)FROM", Pattern.DOTALL);
        Matcher argMatcher = argumentsPattern.matcher(deleteExpr);
        Term[] toDelete = null;
        if (argMatcher.find()) {
            //extract attributes from the SELECT clause
            String[] values = argMatcher.group(1).split(",");
            toDelete = new Term[values.length];
            for (int i = 0; i < values.length; i++)
                toDelete[i] = TermProcessor.processTerm(values[i].trim(), dataSchema);
        } else throw new Exception("Empty DELETE clause!");
        String relationName;
        Pattern fromPattern = Pattern.compile("FROM(.*)", Pattern.DOTALL);
        Matcher fromMatcher = fromPattern.matcher(deleteExpr);
        if (fromMatcher.find()) {
            relationName = fromMatcher.group(1).trim();
        } else throw new Exception("Empty FROM clause in the DELETE statement!");
        return new Pair<>(toDelete, relationName);
    }

    //returns a pair consisting of values to be inserted and the name of the relation appearing in the INTO clause
    private static Pair<Term[], String> parseInsertExpression(String insertExpr, DataSchema dataSchema) throws Exception {
        Pattern argumentsPattern = Pattern.compile("INSERT(.*)INTO", Pattern.DOTALL);
        Matcher argMatcher = argumentsPattern.matcher(insertExpr);
        Term[] toInsert = null;
        if (argMatcher.find()) {
            //extract attributes from the SELECT clause
            String[] values = argMatcher.group(1).split(",");
            toInsert = new Term[values.length];
            for (int i = 0; i < values.length; i++)
                toInsert[i] = TermProcessor.processTerm(values[i].trim(), dataSchema);
        } else throw new Exception("Empty INSERT clause!");
        String relationName;
        Pattern intoPattern = Pattern.compile("INTO(.*)", Pattern.DOTALL);
        Matcher intoMatcher = intoPattern.matcher(insertExpr);
        if (intoMatcher.find()) {
            relationName = intoMatcher.group(1).trim();
        } else throw new Exception("Empty FROM clause in the DELETE statement!");
        return new Pair<>(toInsert, relationName);
    }

    //returns a pair consisting of values to be inserted and the name of the relation appearing in the INTO clause
    private static BulkUpdate parseUpdateExpression(ConjunctiveSelectQuery query, String taskName, String updateExpr, DataSchema dataSchema) throws Exception {
        //extract the name of the relation to update
        Pattern relationPattern = Pattern.compile("UPDATE(.*)SET", Pattern.DOTALL);
        Matcher relMatcher = relationPattern.matcher(updateExpr);
        String relation = null;
        if (relMatcher.find()) {
            String[] values = relMatcher.group(1).split(",");
            relation = values[0].trim();
        } else throw new Exception("Empty UPDATE clause!");
        //create the update object
        BulkUpdate bulkUpdate = null;
        if (query == null)
            bulkUpdate = new BulkUpdate(taskName, dataSchema.getRepositoryRelationAssociations().get(relation), dataSchema);
        else
            bulkUpdate = new BulkUpdate(taskName, query, dataSchema.getRepositoryRelationAssociations().get(relation), dataSchema);

        //ToDo: check if extracting only attributes without relations will be helpful
        //extract elements that have to be updated
        //List<String> referenceVariables = new ArrayList<>();
        Map<String, Attribute> refVaribaleAttributeAssociations = new HashMap<>();
        Pattern updVarsPattern = Pattern.compile("SET(.*)WHERE", Pattern.DOTALL);
        Matcher updVarsMatcher = updVarsPattern.matcher(updateExpr);
        if (updVarsMatcher.find()) {
            //extract attributes from the SET clause
            String[] values = updVarsMatcher.group(1).split(",");
            Arrays.stream(values)
                    .forEach(c ->
                            refVaribaleAttributeAssociations.put(
                                    c.substring(c.indexOf("@")).trim(),
                                    dataSchema.getAllAttributes().get(c.substring(c.indexOf(".") + 1, c.indexOf("=")).trim()))
                    );
        } else throw new Exception("Empty SET clause!");

        //process WHEN-THEN clauses
        Pattern casePattern = Pattern.compile("(WHEN.*?THEN.*?(?=(WHEN|ELSE)))|(?<=ELSE).*", Pattern.DOTALL);
        Matcher caseMatcher = casePattern.matcher(updateExpr);
        //create a list that stores partially processed conditional update statements from WHEN-THEN clauses

        BulkCondition lastFalseNode = null;
        boolean init = true;
        while (caseMatcher.find()) {//ToDo: check this part!
            String clause = caseMatcher.group(0).replaceFirst("WHEN ", "").trim();
            String[] values = clause.split("THEN");
            if (init) {
                bulkUpdate.root = BulkConditionParser.parseAndUpdate(bulkUpdate.root, values[0].trim(), dataSchema);
                BulkCondition trueNode = bulkUpdate.root.addTrueChild();
                //get all @v=u expressions from the THEN part of the clause
                parseUpdateList(trueNode, values[1], refVaribaleAttributeAssociations);
                lastFalseNode = bulkUpdate.root.addFalseChild();//create a false child object that can be then iteratively populated with further conditions
                init = false;
            } else {
                if (clause.contains("THEN")) {
                    lastFalseNode = BulkConditionParser.parseAndUpdate(lastFalseNode, values[0].trim(), dataSchema);
                    BulkCondition trueNode = lastFalseNode.addTrueChild();
                    //get all @v=u expressions from the THEN part of the clause
                    parseUpdateList(trueNode, values[1], refVaribaleAttributeAssociations);
                    lastFalseNode = lastFalseNode.getFalseNode();
                } else
                    //manage the ELSE case
                    parseUpdateList(lastFalseNode, values[0], refVaribaleAttributeAssociations);
            }
        }
        return bulkUpdate;
    }

    private static void parseUpdateList(BulkCondition node, String expression, Map<String, Attribute> refVaribaleAttributeAssociations) throws InvalidInputException, UnmatchingSortException {
        String[] attributeUpdates = expression.split(",");
        for (String upd : attributeUpdates) {
            String[] operands = upd.split("=");
            node.updateAttributeValue(refVaribaleAttributeAssociations.get(operands[0].trim()), operands[1].trim());
        }
        //return node;
    }

    private static ConjunctiveSelectQuery parsePrecondition(String precondition, DataSchema dataSchema) throws Exception {
        ConjunctiveSelectQuery query = null;
        if (precondition.contains("SELECT"))
            //deal with a query that contains a SELECT part
            query = SelectParser.parse(precondition, dataSchema);
        else {
            //deal with a query that doesn't have a SELECT part
            query = new ConjunctiveSelectQuery();
            for (String expr : precondition.split("AND"))
                query.addBinaryCondition(BinaryExpressionParser.parse(expr, dataSchema));
        }
        return query;
    }

    private static DataSchema parseVariableDeclarations(String declaration, DataSchema dataSchema) {
        String[] declarationElements = declaration.split(":");
        System.out.println("new variable name: " + declarationElements[0].trim());
        System.out.println("new variable sort: " + declarationElements[1].trim());
        System.out.println();
        //we would actually don't need to create here a new case variable
        dataSchema.newCaseVariable(declarationElements[0].trim(), dataSchema.newSort(declarationElements[1].trim()), true);
        dataSchema.addEevar(declarationElements[0].trim());
        return dataSchema;
    }


}
