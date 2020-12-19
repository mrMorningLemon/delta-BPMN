package it.unibz.deltabpmn.bpmn.extractors;

import it.unibz.deltabpmn.dataschema.core.DataSchema;
import it.unibz.deltabpmn.dataschema.elements.Sort;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.Documentation;
import org.camunda.bpm.model.xml.ModelInstance;

import java.util.Optional;

public final class CaseVariableExtractor {

    private final static String PROCESS_VARIABLES_ID = "ProcessVariablesID";

    /**
     * Extracts process (or case) variables from the .bpmn file.
     *
     * @return The updated data schema object contains all the extracted case variables.
     */
    public static DataSchema extract(ModelInstance modelInstance, DataSchema dataSchema) {
        Optional<DataObjectReference> bpmnProcessVariables = Optional.ofNullable(modelInstance.getModelElementById(PROCESS_VARIABLES_ID));
        if (bpmnProcessVariables.isPresent()) {
            Documentation documentation = bpmnProcessVariables.get().getDocumentations().iterator().next();
            String documentationText = documentation.getTextContent();
            for (String varDeclaration : documentationText.split(";")) {
                String[] declarationElements = varDeclaration.split(":");
                //ToDo: manage system sorts correctly!
                Sort varSort = dataSchema.newSort(declarationElements[1].trim());
                //ToDo: add a way to account for multiple-case variables
                dataSchema.newCaseVariable(declarationElements[0].trim(), varSort, true);
            }
        }
        return dataSchema;
    }
}
