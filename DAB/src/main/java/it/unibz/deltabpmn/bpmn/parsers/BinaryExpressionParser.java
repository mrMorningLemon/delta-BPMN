package it.unibz.deltabpmn.bpmn.parsers;

import it.unibz.deltabpmn.datalogic.BinaryCondition;
import it.unibz.deltabpmn.datalogic.BinaryConditionProvider;
import it.unibz.deltabpmn.dataschema.core.DataSchema;
import it.unibz.deltabpmn.dataschema.elements.Term;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

public class BinaryExpressionParser {

    public static BinaryCondition parse(String expression, DataSchema dataSchema) throws Exception {

        //var v - is a newly declared variable that is an eevar //ToDo check if it's even currently supported
        //R.a - is an attribute from some new

        BinaryCondition condition = null;
        if (expression.contains("=")) {
            String[] operands = expression.split("=");
            //if it's an attribute, then don't do anything, just fetch it from list of attributes
            //if it's a case variable, we assume that it has been already generated by looking into the vars declaration
            Pair<Term, Term> terms = parseOperands(operands[0].trim(), operands[1].trim(), dataSchema);
            condition = BinaryConditionProvider.equality(terms.getKey(), terms.getValue());
        }
        if (expression.contains("!=")) {
            String[] operands = expression.split("!=");
            Pair<Term, Term> terms = parseOperands(operands[0].trim(), operands[1].trim(), dataSchema);
            condition = BinaryConditionProvider.inequality(terms.getKey(), terms.getValue());
        }
        if (expression.contains(">")) {
            String[] operands = expression.split(">");
            Pair<Term, Term> terms = parseOperands(operands[0].trim(), operands[1].trim(), dataSchema);

            condition = BinaryConditionProvider.greaterThan(terms.getKey(), terms.getValue());
        }
        if (expression.contains("<")) {
            String[] operands = expression.split("<");
            Pair<Term, Term> terms = parseOperands(operands[0].trim(), operands[1].trim(), dataSchema);
            condition = BinaryConditionProvider.inequality(terms.getKey(), terms.getValue());
        }

        if (expression.contains("IN")) {
            //currently not supported for update preconditions
            //ToDo: add support!:)
        }
        return condition;
    }


    private static Pair<Term, Term> parseOperands(String first, String second, DataSchema dataSchema) throws Exception {
        Term t1 = TermProcessor.processTerm(first.trim(), dataSchema);
        Term t2 = TermProcessor.processTerm(second.trim(), dataSchema);

        if (t1 == null && t2 == null)
            throw new Exception("Unmatching operands " + t1 + " and " + t2);
        else if (t1 == null)
            //it's a constant that we don't know about ==> add it to dataSchema
            t1 = dataSchema.newConstant(first.trim(), t2.getSort());
        else if (t2 == null)
            //it's a constant that we don't know about ==> add it to dataSchema
            t2 = dataSchema.newConstant(second.trim(), t1.getSort());
        return new ImmutablePair<>(t1, t2);
    }

}


