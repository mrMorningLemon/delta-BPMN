package it.unibz.deltabpmn.bpmn.parsers;

import it.unibz.deltabpmn.bpmn.utils.SQL.SelectParser;
import it.unibz.deltabpmn.datalogic.*;
import it.unibz.deltabpmn.dataschema.core.DataSchema;
import it.unibz.deltabpmn.dataschema.elements.Attribute;
import it.unibz.deltabpmn.dataschema.elements.Term;
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


    public static ComplexTransition parseTask(String taskName, ExtensionElements extensionElements, DataSchema dataSchema) throws Exception {
        String precondition = "";
        List<String> effects = new ArrayList<>();
        List<String> newVariableDeclarations = new ArrayList<>();
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
            query = parsePrecondition(precondition, dataSchema);
        }
        //4. parse one of the effect types and generate the final transition object (NB: if we have only variables to set, then this is going to be an insert transition; otherwise, variable updates have to be added to a created transition)
        //ToDo always check if query is null
        ComplexTransition transition = null;
        String mainStatement = effects.stream().filter(str -> str.contains("INSERT")).findAny().orElse("") +
                effects.stream().filter(str -> str.contains("DELETE")).findAny().orElse("") +
                effects.stream().filter(str -> str.contains("UPDATE")).findAny().orElse("");

        if (mainStatement.contains("INSERT")) {
            if (query == null)
                transition = new InsertTransition(taskName + "INSERT", dataSchema);
            else
                transition = new InsertTransition(taskName + "INSERT", query, dataSchema);
            Pair<Term[], String> insertComponents = parseInsertExpression(mainStatement, dataSchema);
            ((InsertTransition) transition).insert(dataSchema.getRepositoryRelationAssociations().get(insertComponents.getValue()), insertComponents.getKey());
        }
        if (effects.stream().anyMatch(str -> str.contains("DELETE"))) {
            if (query == null)
                transition = new DeleteTransition(taskName + "DELETE", dataSchema);
            else
                transition = new DeleteTransition(taskName + "DELETE", query, dataSchema);
            Pair<Term[], String> deleteComponents = parseDeleteExpression(mainStatement, dataSchema);
            ((DeleteTransition) transition).delete(dataSchema.getRepositoryRelationAssociations().get(deleteComponents.getValue()), deleteComponents.getKey());
        }
        if (effects.stream().anyMatch(str -> str.contains("UPDATE"))) {
            //ToDo: add the management of (bulk) update statements!
            //manage the update statement
        }
        return transition;
    }


    private static void parseVariableUpdate(String effect, DataSchema dataSchema) {
        String[] operands = effect.split("=");
        Term first = TermProcessor.processTerm(operands[0].substring(1, operands[0].length()).trim(), dataSchema);//first element is always # for case variables
        Term second = TermProcessor.processTerm(operands[1].trim(), dataSchema);
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
                toDelete[i] = TermProcessor.processTerm(values[i], dataSchema);
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
                toInsert[i] = TermProcessor.processTerm(values[i], dataSchema);
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
        BulkUpdate update = null;
        if (query == null)
            update = new BulkUpdate(taskName + "UPDATE", dataSchema.getRepositoryRelationAssociations().get(relation), dataSchema);
        else
            update = new BulkUpdate(taskName + "UPDATE", query, dataSchema.getRepositoryRelationAssociations().get(relation), dataSchema);

        //ToDo: check if extracting only attributes without relations will be helpful
        //extract elements that have to be updated
        List<String> referenceVariables = new ArrayList<>();
        Map<Attribute, String> attributeVaribaleAssociations = new HashMap<>();
        Pattern updVarsPattern = Pattern.compile("SET(.*)CASE", Pattern.DOTALL);
        Matcher updVarsMatcher = updVarsPattern.matcher(updateExpr);
        if (updVarsMatcher.find()) {
            //extract attributes from the SET clause
            String[] values = updVarsMatcher.group(1).split(",");
            System.out.println(values.length);
            Arrays.stream(values).forEach(c -> System.out.println(c));
        } else throw new Exception("Empty SET clause!");


    }

    private static ConjunctiveSelectQuery parsePrecondition(String precondition, DataSchema dataSchema) {
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
        System.out.println("name: " + declarationElements[0].trim());
        System.out.println("sort: " + declarationElements[1].trim());
        //we would actually need to create here a new case variable
        dataSchema.newCaseVariable(declarationElements[0].trim(), dataSchema.newSort(declarationElements[1].trim()), true);
        return dataSchema;
    }


}