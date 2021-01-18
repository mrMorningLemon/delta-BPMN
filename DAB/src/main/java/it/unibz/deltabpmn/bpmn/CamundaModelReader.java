package it.unibz.deltabpmn.bpmn;

import it.unibz.deltabpmn.bpmn.extractors.CaseVariableExtractor;
import it.unibz.deltabpmn.bpmn.extractors.CatalogRelationExtractor;
import it.unibz.deltabpmn.bpmn.extractors.RepositoryRelationExtractor;
import it.unibz.deltabpmn.bpmn.parsers.GatewayConditionParser;
import it.unibz.deltabpmn.bpmn.parsers.SafetyPropertyParser;
import it.unibz.deltabpmn.bpmn.parsers.UpdateExpressionParser;
import it.unibz.deltabpmn.datalogic.ComplexTransition;
import it.unibz.deltabpmn.datalogic.ConjunctiveSelectQuery;
import it.unibz.deltabpmn.dataschema.core.DataSchema;
import it.unibz.deltabpmn.processschema.blocks.*;
import it.unibz.deltabpmn.processschema.core.ProcessSchema;
import it.unibz.deltabpmn.verification.mcmt.NameManager;
import it.unibz.deltabpmn.verification.mcmt.translation.DABProcessTranslator;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.impl.instance.ServiceTaskImpl;
import org.camunda.bpm.model.bpmn.impl.instance.StartEventImpl;
import org.camunda.bpm.model.bpmn.impl.instance.TaskImpl;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.xml.ModelInstance;

import java.io.File;
import java.util.*;
import java.util.stream.Collector;

/**
 * A class that parses a .bpmn model and extracts from it three main DAB components such as
 * data schema, process schema and data (manipulation) logic.
 */
public class CamundaModelReader {

    private ModelInstance modelInstance;
    private DataSchema dataSchema;
    private ProcessSchema processSchema;
    private Deque<FlowNode> bpmnNodeQueue = new ArrayDeque<>();
    private Set<FlowNode> visitedNodes = new HashSet<FlowNode>();
    private Map<FlowNode, Integer> visitedEndGates = new HashMap<>();
    private Stack<XORSplitGate> visitedXORSplitGates = new Stack<>();
    private Stack<FlowNode> visitedOpenXORLoopGates = new Stack<>();//used for opening XORs in LOOP blocks
    private Stack<XORLoopGate> visitedLoopGates = new Stack<>();//remember all loop gates
    private Stack<Block> stackBlocks = new Stack<>();
    // private int taskBlockCounter = 1;//counter for task blocks
    private int seqBlockCounter = 1; //counter for sequence blocks
    private int xorBlockCounter = 1;//counter for XOR (exclusive choice) blocks
    private int loopBlockCounter = 1;//counter for LOOP blocks
    private ProcessBlock dabProcess;
    private List<ConjunctiveSelectQuery> propertiesToVerify = new ArrayList<>();
    private String processName;


    public CamundaModelReader(String filePath) throws Exception {
        File file = new File(filePath);
        this.modelInstance = Bpmn.readModelFromFile(file);
        //1. generate the DAB data schema
        this.dataSchema = DataSchema.getInstance();
        //extract case variables
        this.dataSchema = CaseVariableExtractor.extract(this.modelInstance, this.dataSchema);
        //extract catalog relations
        this.dataSchema = CatalogRelationExtractor.extract(this.modelInstance, this.dataSchema);
        //extract repository relations
        this.dataSchema = RepositoryRelationExtractor.extract(this.modelInstance, this.dataSchema);

        this.processSchema = new ProcessSchema(dataSchema);
        //remove unsopported elements from the process name
        this.processName = NameManager.normaliseName(file.getName().replaceAll("(.bpmn)*", ""));
        this.dabProcess = processSchema.newProcessBlock(processName);
        bpmnModelExplorer();
        this.dabProcess.addBlock(this.stackBlocks.pop());

        //get property to verify
        ExtensionElements extensionElements = modelInstance.getModelElementsByType(Process.class).iterator().next().getExtensionElements();
        if (extensionElements == null)
            throw new Exception("The process model contains no properties to verify!");
        this.propertiesToVerify = SafetyPropertyParser.parse(extensionElements, dataSchema);
    }


    public DataSchema getDataSchema() {
        return this.dataSchema;
    }

    public ProcessBlock getDabProcess() {
        return this.dabProcess;
    }

    public List<ConjunctiveSelectQuery> getPropertiesToVerify() {
        return this.propertiesToVerify;
    }

    public List<DABProcessTranslator> getProcessTranslators() {
        List<DABProcessTranslator> processTranslators = new ArrayList<>();
        int cnt = 1;
        for (ConjunctiveSelectQuery property : this.propertiesToVerify) {
            DABProcessTranslator processTranslator = new DABProcessTranslator(processName + cnt, this.dabProcess, this.dataSchema);
            processTranslator.setSafetyFormula(property);
            processTranslators.add(processTranslator);
            cnt++;
        }
        return processTranslators;
    }

    private void bpmnModelExplorer() throws Exception {
        //1. Extract start events and check if there are more than two
        Collection<StartEvent> startEvents = modelInstance.getModelElementsByType(StartEvent.class);
        try {
            if (startEvents.size() > 1)
                //thiw warning might not work as there are models that have subprocesses with local start events
                throw new Exception("Warning: your model contains more than one start event!");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        StartEvent start = startEvents.iterator().next();

        //2. find all boundary events and generate a map of activities attached to them
        Map<Activity, BoundaryEvent> boundaryEventMap = new HashMap<>();
        //needed for locating sub-processes
        Collection<BoundaryEvent> boundaryEvents = modelInstance.getModelElementsByType(BoundaryEvent.class);
        for (BoundaryEvent event : boundaryEvents)
            boundaryEventMap.put(event.getAttachedTo(), event);

        //3. initialize the queue, prepare a set of visited nodes and a current node
        this.bpmnNodeQueue.add(start);
        FlowNode currentNode;

        //4. run the breadth-first traversal

        while (!bpmnNodeQueue.isEmpty()) {
            currentNode = bpmnNodeQueue.removeFirst();
            if (!visitedNodes.contains(currentNode)) {
                List<FlowNode> newFrontier = currentNode.getSucceedingNodes().list();

                // get succeeding nodes of a boundary event (if any attached to the current node)
                if (boundaryEventMap.get(currentNode) != null) {
                    bpmnNodeQueue.add(boundaryEventMap.get(currentNode));
                    bpmnNodeQueue.addAll(boundaryEventMap.get(currentNode).getSucceedingNodes().list());
                }

                // for subprocesses it's important to load into the stack the internal starting event
                if (currentNode instanceof SubProcess) {
                    bpmnNodeQueue.addAll(currentNode.getChildElementsByType(StartEvent.class));
                }

                //****************************************
                //process a START Event
                //****************************************
                if (currentNode instanceof StartEventImpl) {
                    newFrontier.stream().forEach(c -> bpmnNodeQueue.addLast(c));
                    visitedNodes.add(currentNode);

                    //System.out.println("SIMPLE: Start Event");
                    //System.out.println("BLOCK: Process");
                    //stackBlocks.push(currentNode);//this might not be even needed as start events are currently treated as labels
                }
                //****************************************

                //****************************************
                //process a CATCH Event
                //****************************************
                if (currentNode instanceof CatchEvent && !currentNode.getIncoming().isEmpty() && !currentNode.getOutgoing().isEmpty()) {
                    // --> (E) -->
                    newFrontier.stream().forEach(c -> bpmnNodeQueue.addLast(c));
                    visitedNodes.add(currentNode);
                    it.unibz.deltabpmn.processschema.blocks.Event eventBlock = null;
                    ExtensionElements extensionElements = currentNode.getExtensionElements();
                    if (extensionElements != null)
                        eventBlock = processSchema.newEvent(currentNode.getId(), UpdateExpressionParser.parse(currentNode.getId(), extensionElements, dataSchema));
                    else
                        eventBlock = processSchema.newEvent(currentNode.getId());
                    stackBlocks.push(eventBlock);
                    //System.out.println("SIMPLE: Catch Event");//ToDo: in our case, Catch Events can be only of certain types --> NARROW THE SCOPE
                }
                //****************************************


                //****************************************
                //process a Task
                //****************************************
                if (currentNode instanceof TaskImpl || currentNode instanceof ServiceTaskImpl) {
                    //--> [ T ] -->
                    newFrontier.stream().forEach(c -> bpmnNodeQueue.addFirst(c));
                    visitedNodes.add(currentNode);
                    //start parsing the task data update (if any is available)
                    it.unibz.deltabpmn.processschema.blocks.Task taskBlock = null;
                    ExtensionElements extensionElements = currentNode.getExtensionElements();
                    if (extensionElements != null) {
                        ComplexTransition transition = UpdateExpressionParser.parse(currentNode.getId(), extensionElements, dataSchema);
                        taskBlock = processSchema.newTask(currentNode.getId(), transition);
                    } else
                        taskBlock = processSchema.newTask(currentNode.getId());
                    stackBlocks.push(taskBlock);
                    //this.taskBlockCounter++;
                }
                //****************************************


                //****************************************
                //process a Deferred Choice block
                //****************************************
                //XOR split
                if (currentNode instanceof ExclusiveGateway && currentNode.getIncoming().size() == 1 && currentNode.getSucceedingNodes().filterByType(EndEvent.class).list().isEmpty() && lookAheadLoop(newFrontier, this.visitedOpenXORLoopGates) == null) {
                    //the order has to be reversed as forEach reverses the order of added elements; when we  start forking, add the frontier to the front of the deque
                    newFrontier
                            .stream()
                            .collect(
                                    Collector.of(
                                            ArrayDeque<FlowNode>::new,
                                            (deq, t) -> deq.addFirst(t),
                                            (d1, d2) -> {
                                                d2.addAll(d1);
                                                return d2;
                                            }))
                            .stream()
                            .forEach(c -> bpmnNodeQueue.addFirst(c));
                    ExtensionElements extensionElements = currentNode.getExtensionElements();
                    //ToDo: do we always force a condition in the gate for a XOR split? if there's none, we can put TRUE, but this is not very "sustainable"
                    if (extensionElements == null)
                        throw new Exception("The condition of the XOR split " + currentNode.getId() + " is empty!");
                    XORSplitGate gate = new XORSplitGate(currentNode.getId(), GatewayConditionParser.parseXORCondition(extensionElements, this.dataSchema));
                    visitedXORSplitGates.push(gate);
                    stackBlocks.push(gate);//remember where did we start forking
                    //ToDo: add visited start gates for detecting loops; if we saw that gate twice, then it's a loop
                }

                //XOR join
                if (currentNode instanceof ExclusiveGateway && currentNode.getIncoming().size() == 2 && Collections.disjoint(this.visitedNodes, currentNode.getPreviousNodes().list())) {
                    //ToDO: when we have two incoming arrows, it can mean that we're dealing with a gateway of a backward (or forward) exception or a loop block!
                    //if it has two inputs and the next element is in visited ==> can be backward exception or loop block!
                    visitedEndGates.merge(currentNode, 1, (prev, one) -> prev + one);//does upsert into the map with XOR-JOIN gates (if a gate hasn't been visited, it is added with counter 1; otherwise it's counter gets incremented by 1

                    if (visitedEndGates.get(currentNode) == 2) {
                        newFrontier.stream().forEach(c -> bpmnNodeQueue.addLast(c));//we start assembling the XOR block after we encounter the join for the second time; then we populate its frontier
                        ExclusiveChoiceBlock newXORBlock = processSchema.newExclusiveChoiceBlock("XORblock" + this.xorBlockCounter);
                        newXORBlock.addSecondBlock(stackBlocks.pop());//add second block
                        newXORBlock.addFirstBlock(stackBlocks.pop());//add first block
                        //remove (X)_F and get its condition into the XOR block
                        newXORBlock.addCondition(((XORSplitGate) stackBlocks.pop()).getCondition());
                        System.out.println("XOR completed: " + newXORBlock);
                        stackBlocks.push(newXORBlock);
                        this.xorBlockCounter++;
                    } else {
                        //we are on one of the branches of the XOR block, walk back until (X)_F on the stack so as to create a unique block on the branch
                        XORSplitGate startingXORGate = visitedXORSplitGates.pop();//get the last XOR gate from the stack of visited gates and
                        while (stackBlocks.search(startingXORGate) > 2) {
                            SequenceBlock newSequenceBlock = processSchema.newSequenceBlock("SEQblock" + this.seqBlockCounter);
                            newSequenceBlock.addSecondBlock(stackBlocks.pop());//add second block
                            newSequenceBlock.addFirstBlock(stackBlocks.pop());//add first block
                            stackBlocks.push(newSequenceBlock);
                            this.seqBlockCounter++;
                        }
                    }
                }
                //****************************************

                //****************************************
                //process a LOOP block
                //****************************************
                //XOR split for LOOP
                if (currentNode instanceof ExclusiveGateway && currentNode.getIncoming().size() == 2) {
                    //visiting the gate for the second time, close the block
                    if (this.visitedOpenXORLoopGates.contains(currentNode)) {
                        this.visitedOpenXORLoopGates.pop();//remove the gate that we've just visited for the second time
                        //go until the first XOR block on the stack of blocks, this will be an iteration XOR gate with a condition
                        //use this gate to generate a second block of the LOOP
                        XORLoopGate iterationGate = this.visitedLoopGates.pop();
                        while (stackBlocks.search(iterationGate) > 2) {
                            SequenceBlock newSequenceBlock = processSchema.newSequenceBlock("SEQBlock" + this.seqBlockCounter);
                            newSequenceBlock.addSecondBlock(stackBlocks.pop());//add second block
                            newSequenceBlock.addFirstBlock(stackBlocks.pop());//add first block
                            stackBlocks.push(newSequenceBlock);
                            this.seqBlockCounter++;
                        }
                        //now assemble the whole LOOP block
                        LoopBlock newLOOPBlock = processSchema.newLoopBlock("LOOPBlock" + loopBlockCounter, iterationGate.getCondition());
                        newLOOPBlock.addSecondBlock(stackBlocks.pop());//add second block
                        stackBlocks.pop();//remove (X)_LF2
                        newLOOPBlock.addFirstBlock(stackBlocks.pop());//add first block
                        stackBlocks.pop();//remove (X)_LF1
                        stackBlocks.push(newLOOPBlock);
                        this.loopBlockCounter++;
                        continue;
                    }
                    //visiting the gate for the first time (comes after the first check as it clashes with the XOR gate being added to the stack of visited XOR gates)
                    if (!Collections.disjoint(this.visitedNodes, currentNode.getPreviousNodes().list())) {
                        //a XOR split of the loop should have only one successor (i.e., newFrontier.size()=1)!
                        this.bpmnNodeQueue.addFirst(newFrontier.get(0));
                        //add visited start gates for detecting loops: when we see that gate for a second time, then it's a loop block!
                        this.visitedOpenXORLoopGates.push(currentNode);
                        XORLoopGate gate = new XORLoopGate(currentNode.getId());
                        this.visitedLoopGates.push(gate);//needed to perform a loop for creating the first sub-block of the LOOP block
                        stackBlocks.push(gate);//remember where did we start forking
                    }
                }

                //XOR "join" for LOOP
                if (currentNode instanceof ExclusiveGateway && currentNode.getIncoming().size() == 1 && !this.visitedOpenXORLoopGates.empty()) {
                    //System.out.println("TAKING A TURN IN A LOOP BLOCK");
                    //detect which branch brings to the looping gate and take this gate for elements to be put into the frontier
                    FlowNode loopBranchNode = lookAheadLoop(newFrontier, this.visitedOpenXORLoopGates);
                    if (loopBranchNode != null) {
                        newFrontier.remove(loopBranchNode);
                        this.bpmnNodeQueue.addFirst(newFrontier.get(0));//first add the element that we want to check after the loop has been assembled
                        this.bpmnNodeQueue.addFirst(loopBranchNode);
                        XORLoopGate openGate = visitedLoopGates.pop();
                        //generate from all the visited blocks in the first part of the loop a single block
                        while (stackBlocks.search(openGate) > 2) {
                            SequenceBlock newSequenceBlock = processSchema.newSequenceBlock("SEQBlock" + this.seqBlockCounter);
                            newSequenceBlock.addSecondBlock(stackBlocks.pop());//add second block
                            newSequenceBlock.addFirstBlock(stackBlocks.pop());//add first block
                            stackBlocks.push(newSequenceBlock);
                        }
                        ExtensionElements extensionElements = currentNode.getExtensionElements();
                        if (extensionElements == null)
                            throw new Exception("The condition of the XOR split " + currentNode.getId() + " is empty!");
                        XORLoopGate gate = new XORLoopGate(currentNode.getId(), GatewayConditionParser.parseXORCondition(extensionElements, this.dataSchema));
                        visitedLoopGates.push(gate);
                        stackBlocks.push(gate);//pushing the second fork on the stack of blocks
                    }
                }
                //****************************************


                //****************************************
                //process a POSSIBLE COMPLETION block
                //****************************************
                if (currentNode instanceof ExclusiveGateway && currentNode.getIncoming().size() == 1 && !currentNode.getSucceedingNodes().filterByType(EndEvent.class).list().isEmpty()) {
                    EndEvent end = currentNode.getSucceedingNodes().filterByType(EndEvent.class).list().get(0);
                    //check if there are no potential situations in which we have a loop instead of the completion block
                    if (lookAheadLoop(newFrontier, this.visitedOpenXORLoopGates) == null) {
                        ErrorBlock newPossibleCompletionBlock = null;
                        ExtensionElements extensionElements = currentNode.getExtensionElements();
                        if (extensionElements == null)
                            //throw new Exception("The condition of the POSSIBLE COMPLETION block " + currentNode.getId() + " is empty!");
                            newPossibleCompletionBlock = processSchema.newErrorBlock(currentNode.getId());
                        else
                            newPossibleCompletionBlock = processSchema.newErrorBlock(currentNode.getId(), GatewayConditionParser.parseXORCondition(extensionElements, this.dataSchema));
                        //newPossibleCompletionBlock.add(end.getId());
                        //extract the condition from the XOR gate
                        newFrontier.remove(end);
                        stackBlocks.push(newPossibleCompletionBlock);
                        this.bpmnNodeQueue.addFirst(newFrontier.get(0));
                    }
                }
                //****************************************


                //**************************************************
                //END EVENT + RECURSIVE CREATION OF SEQUENCE BLOCKS
                //**************************************************
                //do here the creation of 2-sized sequence block! when you arrive to the end, do a traversal
                //and create a sequence of chained pairs
                if (currentNode instanceof EndEvent && stackBlocks.size() > 1) {
                    while (stackBlocks.size() > 1) {
                        SequenceBlock newSequenceBlock = processSchema.newSequenceBlock("SEQBlock" + this.seqBlockCounter);
                        newSequenceBlock.addSecondBlock(stackBlocks.pop());//add second block
                        newSequenceBlock.addFirstBlock(stackBlocks.pop());//add first block
                        stackBlocks.push(newSequenceBlock);
                        this.seqBlockCounter++;
                    }
                }
            }
        }
    }

    //return a flow node that ends up in a loop
    private static FlowNode lookAheadLoop(List<FlowNode> frontier, Stack<FlowNode> visited) {
        for (FlowNode node : frontier)
            if (containsVisitedXOR(visited, node))
                return node;
        return null;
    }

    private static boolean containsVisitedXOR(Stack<FlowNode> visited, FlowNode start) {
        Set<FlowNode> localVisited = new HashSet<FlowNode>();
        LinkedList<FlowNode> queue = new LinkedList<FlowNode>();
        localVisited.add(start);
        queue.add(start);

        FlowNode current;
        while (!queue.isEmpty()) {
            current = queue.removeFirst();
            for (FlowNode node : current.getSucceedingNodes().list()) {
                if (!localVisited.contains(node))
                    queue.add(node);
                if (node instanceof ExclusiveGateway)
                    if (visited.contains(node))
                        return true;
                    else
                        return false;//this is used to check cases in which a possible completion block is within the loop block
            }
        }
        return false;
    }


}

