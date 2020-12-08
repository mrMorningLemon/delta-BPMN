package it.unibz.deltabpmn.bpmn.parsers;

import it.unibz.deltabpmn.datalogic.BinaryCondition;
import it.unibz.deltabpmn.datalogic.BinaryConditionProvider;
import it.unibz.deltabpmn.dataschema.core.DataSchema;

public class BinaryExpressionParser {

    public static BinaryCondition parse(String expression, DataSchema dataSchema) {

        //var v - is a newly declared variable that is an eevar //ToDo check if it's even currently supported
        //R.a - is an attribute from some new

        BinaryCondition condition = null;
        if (expression.contains("=")) {
            String[] operands = expression.split("=");
            //if it's an attribute, then don't do anything, just fetch it from list of attributes
            //if it's a case variable, we assume that it has been already generated by looking into the vars declaration
            condition = BinaryConditionProvider.equality(TermProcessor.processTerm(operands[0], dataSchema), TermProcessor.processTerm(operands[1], dataSchema));
        }
        if (expression.contains("!=")) {
            String[] operands = expression.split("!=");
            condition = BinaryConditionProvider.inequality(TermProcessor.processTerm(operands[0], dataSchema), TermProcessor.processTerm(operands[1], dataSchema));
        }
        if (expression.contains(">")) {
            String[] operands = expression.split(">");
            condition = BinaryConditionProvider.greaterThan(TermProcessor.processTerm(operands[0], dataSchema), TermProcessor.processTerm(operands[1], dataSchema));
        }
        if (expression.contains("<")) {
            String[] operands = expression.split("<");
            condition = BinaryConditionProvider.inequality(TermProcessor.processTerm(operands[0], dataSchema), TermProcessor.processTerm(operands[1], dataSchema));
        }

        if (expression.contains("IN")) {
            //currently not supported for update preconditions
            //ToDo: add support!:)
        }
        return condition;
    }


}

