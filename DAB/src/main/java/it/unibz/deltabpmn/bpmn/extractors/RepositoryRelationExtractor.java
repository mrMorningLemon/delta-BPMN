package it.unibz.deltabpmn.bpmn.extractors;

import it.unibz.deltabpmn.dataschema.core.DataSchema;
import it.unibz.deltabpmn.dataschema.elements.RepositoryRelation;
import it.unibz.deltabpmn.dataschema.elements.Sort;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.Documentation;
import org.camunda.bpm.model.xml.ModelInstance;

import java.util.Optional;

public final class RepositoryRelationExtractor {

    private final static String REPOSITORY_ID = "RepositoryID";

    /**
     * Extracts repository relations from the .bpmn file
     *
     * @return The updated data schema object contains all the extracted repository relations.
     */
    public static DataSchema extract(ModelInstance modelInstance, DataSchema dataSchema) {
        Optional<DataStoreReference> repository = Optional.ofNullable(modelInstance.getModelElementById(REPOSITORY_ID));
        if (repository.isPresent()) {
            Documentation repositoryDecl = repository.get().getDocumentations().iterator().next();
            String repositoryDeclText = repositoryDecl.getTextContent();
            for (String decl : repositoryDeclText.split(";")) {
                int index = decl.indexOf('(');
                String repRelationName = decl.substring(0, index).trim();
                RepositoryRelation repRelation = dataSchema.newRepositoryRelation(repRelationName);
                String attributes = decl.substring(index + 1, decl.length() - 1);
                //start extracting attributes
                for (String attribute : attributes.split(",")) {
                    String[] declarationElements = attribute.split(":");
                    Sort attrSort = dataSchema.newSort(declarationElements[1].trim());
                    repRelation.addAttribute(declarationElements[0].trim(), attrSort);
                }
            }
        }
        return dataSchema;
    }


}


