package it.unibz.deltabpmn.bpmn.parsers;

import it.unibz.deltabpmn.bpmn.utils.SQL.SelectParser;
import it.unibz.deltabpmn.datalogic.ConjunctiveSelectQuery;
import it.unibz.deltabpmn.dataschema.core.DataSchema;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperties;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SafetyPropertyParser {

    private static final String verifyKey = "verify";

    public static List<ConjunctiveSelectQuery> parse(ExtensionElements extensionElements, DataSchema dataSchema) throws Exception {
        Collection<CamundaProperty> properties = extensionElements.getElementsQuery()
                .filterByType(CamundaProperties.class)
                .singleResult()
                .getCamundaProperties();
        String name = null;
        String value = null;
        List<ConjunctiveSelectQuery> toVerify = new ArrayList<>();
        for (CamundaProperty property : properties) {
            name = property.getCamundaName();
            if (name.equals(verifyKey)) {
                value = property.getCamundaValue();
                toVerify.add(parseProperty(value, dataSchema));
            }
        }
        return toVerify;
    }

    private static ConjunctiveSelectQuery parseProperty(String property, DataSchema dataSchema) throws Exception {
        ConjunctiveSelectQuery query = null;
        if (property.contains("SELECT"))
            //deal with a query that contains a SELECT part
            query = SelectParser.parse(property, dataSchema);
        else {
            //deal with a query that doesn't have a SELECT part
            query = new ConjunctiveSelectQuery(dataSchema);
            for (String expr : property.split("AND"))
                query.addBinaryCondition(BinaryExpressionParser.parse(expr, dataSchema));
        }
        return query;
    }
}
