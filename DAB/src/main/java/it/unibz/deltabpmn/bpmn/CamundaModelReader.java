package it.unibz.deltabpmn.bpmn;

import it.unibz.deltabpmn.bpmn.extractors.CaseVariableExtractor;
import it.unibz.deltabpmn.bpmn.extractors.CatalogRelationExtractor;
import it.unibz.deltabpmn.bpmn.extractors.RepositoryRelationExtractor;
import it.unibz.deltabpmn.bpmn.parsers.GatewayConditionParser;
import it.unibz.deltabpmn.bpmn.parsers.UpdateExpressionParser;
import it.unibz.deltabpmn.dataschema.core.DataSchema;
import it.unibz.deltabpmn.processschema.blocks.Block;
import it.unibz.deltabpmn.processschema.blocks.ExclusiveChoiceBlock;
import it.unibz.deltabpmn.processschema.blocks.ProcessBlock;
import it.unibz.deltabpmn.processschema.blocks.SequenceBlock;
import it.unibz.deltabpmn.processschema.core.ProcessSchema;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.impl.instance.ServiceTaskImpl;
import org.camunda.bpm.model.bpmn.impl.instance.StartEventImpl;
import org.camunda.bpm.model.bpmn.impl.instance.TaskImpl;
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
    private Stack<Block> stackBlocks = new Stack<>();
    // private int taskBlockCounter = 1;//counter for task blocks
    private int seqBlockCounter = 1; //counter for sequence blocks
    private int xorBlockCounter = 1;//counter for XOR (exclusive choice) blocks


    public CamundaModelReader(String filePath) {
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
        String processName = file.getName().replaceAll("(.bpmn)*", "");
        ProcessBlock root = processSchema.newProcessBlock(processName);
    }


    public DataSchema getDataSchema() {
        return this.dataSchema;
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
        Collection<BoundaryEvent> boundaryEvents = modelInstance.getModelElementsByType(BoundaryEvent.class);
        for (BoundaryEvent event : boundaryEvents)
            boundaryEventMap.put(event.getAttachedTo(), event);

        //3. initialize the queue, prepare a set of visited nodes and a current node
        bpmnNodeQueue.add(start);
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


                //detect the type of node and, if needed, put it into stack

                if (currentNode instanceof StartEventImpl) {
                    newFrontier.stream().forEach(c -> bpmnNodeQueue.addLast(c));
                    visitedNodes.add(currentNode);

                    //System.out.println("SIMPLE: Start Event");
                    //System.out.println("BLOCK: Process");
                    //stackBlocks.push(currentNode);//this might not be even needed as start events are currently treated as labels
                }

                //process a Catch Event
                if (currentNode instanceof CatchEvent && !currentNode.getIncoming().isEmpty() && !currentNode.getOutgoing().isEmpty()) {
                    newFrontier.stream().forEach(c -> bpmnNodeQueue.addLast(c));
                    visitedNodes.add(currentNode);
                    // --> (E) -->
                    //System.out.println("SIMPLE: Catch Event");//ToDo: in our case, Catch Events can be only of certain types --> NARROW THE SCOPE
                }


                //process a Task
                if (currentNode instanceof TaskImpl || currentNode instanceof ServiceTaskImpl) {
                    //--> [ T ] -->
                    newFrontier.stream().forEach(c -> bpmnNodeQueue.addFirst(c));
                    visitedNodes.add(currentNode);
                    //start parsing the task data update (if any is available)
                    currentNode.getId();
                    it.unibz.deltabpmn.processschema.blocks.Task taskBlock = null;
                    ExtensionElements extensionElements = currentNode.getExtensionElements();
                    if (extensionElements != null)
                        taskBlock = processSchema.newTask(currentNode.getId(), UpdateExpressionParser.parseTask(currentNode.getId(), extensionElements, dataSchema));
                    else
                        taskBlock = processSchema.newTask(currentNode.getId());
                    stackBlocks.push(taskBlock);
                    //this.taskBlockCounter++;
                }

                //process a Deferred Choice block
                //OR split
                if (currentNode instanceof ExclusiveGateway && currentNode.getIncoming().size() == 1) {
                    //the order has to be reversed as forEach reverses the order of added elements; when we start forking, add the frontier to the front of the deque
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
                    stackBlocks.push(new XORSplitGate(currentNode.getId(), GatewayConditionParser.extractXORCondition(extensionElements, this.dataSchema)));//remember where did we start forking

                    //ToDo: add visited start gates for detecting loops; if we saw that gate twice, then it's a loop
                }

                //OR join
                if (currentNode instanceof ExclusiveGateway && currentNode.getIncoming().size() == 2) {
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
                    } else {    //we are on one of the branches of the XOR block, walk back until (X)_F on the stack so as to create a unique block on the branch
                        while (stackBlocks.search("(X)_F") > 2) {
                            SequenceBlock newSequenceBlock = processSchema.newSequenceBlock("SEQblock" + this.seqBlockCounter);
                            newSequenceBlock.addSecondBlock(stackBlocks.pop());//add second block
                            newSequenceBlock.addFirstBlock(stackBlocks.pop());//add first block
                            stackBlocks.push(newSequenceBlock);
                            this.seqBlockCounter++;
                        }
                    }
                }

                //do here the creation of 2-sized sequence block! when you arrive to the end, do a traversal
                //and create a sequence of chained pairs
                if (currentNode instanceof EndEvent && stackBlocks.size() > 1) {
                    while (stackBlocks.size() > 1) {
                        SequenceBlock newSequenceBlock = processSchema.newSequenceBlock("SEQblock" + this.seqBlockCounter);
                        newSequenceBlock.addSecondBlock(stackBlocks.pop());//add second block
                        newSequenceBlock.addFirstBlock(stackBlocks.pop());//add first block
                        stackBlocks.push(newSequenceBlock);
                        this.seqBlockCounter++;
                    }
                }
            }

        }

    }


}
