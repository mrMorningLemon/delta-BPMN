package it.unibz.deltabpmn.bpmn.extractors;

import it.unibz.deltabpmn.dataschema.core.DataSchema;
import it.unibz.deltabpmn.dataschema.elements.CatalogRelation;
import it.unibz.deltabpmn.dataschema.elements.Sort;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.Documentation;
import org.camunda.bpm.model.xml.ModelInstance;

import java.util.Optional;

public final class CatalogRelationExtractor {

    private final static String CATALOG_ID = "CatalogID";

    /**
     * Extracts catalog relations from the .bpmn file.
     *
     * @param modelInstance
     * @param dataSchema
     * @return The updated data schema object contains all the extracted catalog relations.
     */
    public static DataSchema extract(ModelInstance modelInstance, DataSchema dataSchema) {
        Optional<DataStoreReference> catalog = Optional.ofNullable(modelInstance.getModelElementById(CATALOG_ID));
        if (catalog.isPresent()) {
            Documentation catalogDecl = catalog.get().getDocumentations().iterator().next();
            String catalogDeclText = catalogDecl.getTextContent();
            for (String decl : catalogDeclText.split(";")) {
                int index = decl.indexOf('(');
                String catalogRelationName = decl.substring(0, index);
                CatalogRelation catalogRelation = dataSchema.newCatalogRelation(catalogRelationName);
                String attributes = decl.substring(index + 1, decl.length() - 1);
                //start extracting attributes
                for (String attribute : attributes.split(",")) {
                    String[] declarationElements = attribute.split(":");
                    String attrName = declarationElements[0].trim();
                    Sort attrSort = dataSchema.newSort(declarationElements[1].trim());
                    catalogRelation.addAttribute(attrName, attrSort);//remember that the first attribute will always be set as Primary Key
                }
            }
        }
        return dataSchema;
    }
}
