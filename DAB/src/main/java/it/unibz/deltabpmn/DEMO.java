package it.unibz.deltabpmn;


import it.unibz.deltabpmn.bpmn.CamundaModelReader;
import it.unibz.deltabpmn.datalogic.*;
import it.unibz.deltabpmn.dataschema.core.DataSchema;
import it.unibz.deltabpmn.dataschema.core.SystemConstants;
import it.unibz.deltabpmn.dataschema.core.SystemSorts;
import it.unibz.deltabpmn.dataschema.elements.*;
import it.unibz.deltabpmn.exception.EevarOverflowException;
import it.unibz.deltabpmn.exception.InvalidInputException;
import it.unibz.deltabpmn.exception.UnmatchingSortException;
import it.unibz.deltabpmn.processschema.blocks.ProcessBlock;
import it.unibz.deltabpmn.processschema.blocks.SequenceBlock;
import it.unibz.deltabpmn.processschema.blocks.Task;
import it.unibz.deltabpmn.processschema.core.ProcessSchema;
import it.unibz.deltabpmn.processschema.core.State;
import it.unibz.deltabpmn.verification.mcmt.translation.DABProcessTranslator;

public class DEMO {

    public static void main(String[] args) throws UnmatchingSortException, InvalidInputException, EevarOverflowException {


        //DataSchema dataSchema = DataSchema.getInstance();
        String path = System.getProperty("user.home") + "/Dropbox/DABs/DAB-Camunda/models/ex1.bpmn";

        CamundaModelReader modelReader = new CamundaModelReader(path);
        DataSchema dataSchema = modelReader.getDataSchema();

        // check the sorts
        dataSchema.getSorts().forEach(s -> System.out.print(s + " "));
        System.out.println();

        // check the catalog
        dataSchema.getCatalogRelations().forEach(c -> System.out.print(c + " "));
        System.out.println();

        // check the repository
        dataSchema.getRepositoryRelations().forEach(c -> System.out.print(c + " "));
        System.out.println();

        ProcessSchema processSchema = new ProcessSchema(dataSchema);
        Task decideEligible = processSchema.newTask("taskA");

        System.out.println(decideEligible.getMCMTTranslation());

        example(dataSchema);
    }


    private static void example(DataSchema dataSchema) throws UnmatchingSortException, InvalidInputException, EevarOverflowException {
        //////////////////////////////// JOB HIRING EXAMPLE ////////////////////////////////

        // sorts
        Sort job_id = dataSchema.newSort("jobcatID");
        Sort user_id = dataSchema.newSort("userID");
        Sort num_age = dataSchema.newSort("Num_age");
        Sort int_sort = dataSchema.newSort("Num_score");

        // check the sorts
//        dataSchema.getSorts().forEach(s -> System.out.print(s + " "));
//        System.out.println();

        //catalog relations
        CatalogRelation jobCategory = dataSchema.newCatalogRelation("Job_Category");
        jobCategory.addAttribute("jcid", job_id);

        CatalogRelation user = dataSchema.newCatalogRelation("user");
        Attribute u_id = user.addAttribute("uid", user_id);
        Attribute name = user.addAttribute("name", SystemSorts.STRING);
        Attribute age = user.addAttribute("age", num_age);

        // check the catalogs
//        dataSchema.getCatalogRelations().forEach(c -> System.out.print(c + " "));
//        System.out.println();

        //repository relations
        RepositoryRelation application = dataSchema.newRepositoryRelation("Application");
        Attribute jcid_app = application.addAttribute("jcid_app", job_id);
        Attribute uid_app = application.addAttribute("uid_app", user_id);
        Attribute score = application.addAttribute("score", int_sort);
        Attribute eligible = application.addAttribute("eligible", SystemSorts.BOOL);

//        dataSchema.getRepositoryRelations().forEach(c -> System.out.print(c + " "));
//        System.out.println();


        //case variables
        CaseVariable jcid = dataSchema.newCaseVariable("jcid", job_id, true);
        CaseVariable uid = dataSchema.newCaseVariable("uid", user_id, true);
        CaseVariable qualif = dataSchema.newCaseVariable("qualif", SystemSorts.BOOL, true);
        CaseVariable result = dataSchema.newCaseVariable("result", SystemSorts.BOOL, true);
        CaseVariable winner = dataSchema.newCaseVariable("winner", user_id, true);

//        dataSchema.getCaseVariables().forEach(c -> System.out.print(c + " "));
//        System.out.println("\n\n");

        // some transitions
        ConjunctiveSelectQuery ins_job_cat_guard = new ConjunctiveSelectQuery(jobCategory.getAttributeByIndex(0));
        InsertTransition ins_job_cat = new InsertTransition("Insert_job_category", ins_job_cat_guard, dataSchema);
        //ToDo: the following shouldn't work!!!
        ins_job_cat.setControlCaseVariableValue(jcid, dataSchema.newConstant("jcid", job_id));


        ///System.out.println(ins_job_cat.getMCMTTranslation());

        ConjunctiveSelectQuery ins_user_guard = new ConjunctiveSelectQuery(user.getAttributeByIndex(0));
        InsertTransition ins_user = new InsertTransition("Insert_User", ins_user_guard, dataSchema);
        ins_user.setControlCaseVariableValue(uid, dataSchema.newConstant("uid", user_id));

        //System.out.println(ins_user.getMCMTTranslation());

        InsertTransition check_qual = new InsertTransition("Check_Qual", dataSchema);
        check_qual.setControlCaseVariableValue(qualif, SystemConstants.TRUE);

        //System.out.println(check_qual.getMCMTTranslation());

        BulkUpdate markE = new BulkUpdate("MarkE", new ConjunctiveSelectQuery(), application, dataSchema);
        markE.root.addGreaterThanCondition(application.getAttributeByIndex(2), 80)
                .addLessThanCondition(application.getAttributeByIndex(2),100);
        markE.root.addTrueChild().updateAttributeValue(application.getAttributeByIndex(3), "True");
        markE.root.addFalseChild().updateAttributeValue(application.getAttributeByIndex(3), "False");

        // System.out.println(markE.getMCMTTranslation());


        // some transitions
        ConjunctiveSelectQuery sel_winner_guard = new ConjunctiveSelectQuery(application.getAttributeByIndex(0),
                application.getAttributeByIndex(1), application.getAttributeByIndex(2), application.getAttributeByIndex(3));
        sel_winner_guard.addBinaryCondition(BinaryConditionProvider.equality(application.getAttributeByIndex(3), SystemConstants.TRUE));
        DeleteTransition sel_winner = new DeleteTransition("Sel_Winner", sel_winner_guard, dataSchema);
        sel_winner.delete(application, jcid_app, uid_app, score, eligible);
        sel_winner.setControlCaseVariableValue(jcid, dataSchema.newConstant("jcid_app", job_id));
        sel_winner.setControlCaseVariableValue(uid, dataSchema.newConstant("uid_app", user_id));
        sel_winner.setControlCaseVariableValue(winner, dataSchema.newConstant("uid_app", user_id));
        sel_winner.setControlCaseVariableValue(result, dataSchema.newConstant("eligible", SystemSorts.BOOL));
        sel_winner.setControlCaseVariableValue(qualif, SystemConstants.FALSE);


        ConjunctiveSelectQuery prova = new ConjunctiveSelectQuery(jobCategory.getAttributeByIndex(0));
        prova.addBinaryCondition(BinaryConditionProvider.equality(jobCategory.getAttributeByIndex(0), dataSchema.newConstant("HR", job_id)));


        //example
        ProcessSchema processSchema = new ProcessSchema(dataSchema);
        Task decideEligible = processSchema.newTask("decide_eligible", markE);
        Task selectWinner = processSchema.newTask("select_winner", sel_winner);


        SequenceBlock seq = processSchema.newSequenceBlock("sequence_block");
        seq.addFirstBlock(decideEligible);
        seq.addSecondBlock(selectWinner);

        ProcessBlock root = processSchema.newProcessBlock("root_process");
        root.addBlock(seq);

        DABProcessTranslator assign_job_process = new DABProcessTranslator("job_hiring_process", root, dataSchema);

        //this property can be assigned to a list of properties to check in the BPMN model! the properties get parsed and (they're just conjunctive queries! we have a parser for conjunctive queries!)
        //run MCMT on the generated files
        ConjunctiveSelectQuery safety_property = new ConjunctiveSelectQuery();
        safety_property.addBinaryCondition(BinaryConditionProvider.equality(root.getLifeCycleVariable(), State.ENABLED));
        safety_property.addBinaryCondition(BinaryConditionProvider.equality(winner, SystemConstants.NULL));
        assign_job_process.setSafetyFormula(safety_property);

        assign_job_process.generateMCMTTranslation();

    }

}


