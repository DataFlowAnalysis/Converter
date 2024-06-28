package org.dataflowanalysis.converter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

import org.dataflowanalysis.analysis.DataFlowConfidentialityAnalysis;
import org.dataflowanalysis.analysis.core.AbstractTransposeFlowGraph;
import org.dataflowanalysis.analysis.core.AbstractVertex;
import org.dataflowanalysis.analysis.core.CharacteristicValue;
import org.dataflowanalysis.analysis.core.DataCharacteristic;
import org.dataflowanalysis.analysis.core.FlowGraphCollection;
import org.dataflowanalysis.analysis.pcm.PCMDataFlowConfidentialityAnalysisBuilder;
import org.dataflowanalysis.analysis.pcm.core.AbstractPCMVertex;
import org.dataflowanalysis.analysis.pcm.core.seff.CallingSEFFPCMVertex;
import org.dataflowanalysis.analysis.pcm.core.seff.SEFFPCMVertex;
import org.dataflowanalysis.analysis.pcm.core.user.CallingUserPCMVertex;
import org.dataflowanalysis.analysis.pcm.core.user.UserPCMVertex;
import org.dataflowanalysis.analysis.pcm.utils.PCMQueryUtils;
import org.dataflowanalysis.dfd.datadictionary.Behaviour;
import org.dataflowanalysis.dfd.datadictionary.DataDictionary;
import org.dataflowanalysis.dfd.datadictionary.Label;
import org.dataflowanalysis.dfd.datadictionary.LabelType;
import org.dataflowanalysis.dfd.datadictionary.Pin;
import org.dataflowanalysis.dfd.datadictionary.datadictionaryFactory;
import org.dataflowanalysis.dfd.dataflowdiagram.DataFlowDiagram;
import org.dataflowanalysis.dfd.dataflowdiagram.Flow;
import org.dataflowanalysis.dfd.dataflowdiagram.Node;
import org.dataflowanalysis.dfd.dataflowdiagram.dataflowdiagramFactory;
import org.eclipse.core.runtime.Plugin;
import org.palladiosimulator.pcm.core.entity.Entity;
import org.palladiosimulator.pcm.seff.AbstractBranchTransition;
import org.palladiosimulator.pcm.seff.BranchAction;
import org.palladiosimulator.pcm.seff.ResourceDemandingSEFF;
import org.palladiosimulator.pcm.seff.StartAction;
import org.palladiosimulator.pcm.seff.StopAction;
import org.palladiosimulator.pcm.usagemodel.Start;
import org.palladiosimulator.pcm.usagemodel.Stop;

/**
 * Converts Palladio models to the data flow diagram and dictionary representation. Inherits from {@link Converter} to
 * utilize shared conversion logic while providing specific functionality for handling Palladio models.
 */
public class PCMConverter extends Converter {

    private final Map<AbstractPCMVertex<?>, Node> dfdNodeMap = new HashMap<>();
    private DataDictionary dataDictionary;
    private DataFlowDiagram dataFlowDiagram;
    private Set<String> takenIds = new HashSet<>();

    /**
     * Converts a PCM model into a DataFlowDiagramAndDictionary object.
     * @param modelLocation Location of the model folder.
     * @param usageModelPath Location of the usage model.
     * @param allocationPath Location of the allocation.
     * @param nodeCharPath Location of the node characteristics.
     * @param activator Activator class of the plugin where the model resides.
     * @return DataFlowDiagramAndDictionary object representing the converted Palladio model.
     */
    public DataFlowDiagramAndDictionary pcmToDFD(String modelLocation, String usageModelPath, String allocationPath, String nodeCharPath,
            Class<? extends Plugin> activator) {
        DataFlowConfidentialityAnalysis analysis = new PCMDataFlowConfidentialityAnalysisBuilder().standalone()
                .modelProjectName(modelLocation)
                .usePluginActivator(activator)
                .useUsageModel(usageModelPath)
                .useAllocationModel(allocationPath)
                .useNodeCharacteristicsModel(nodeCharPath)
                .build();

        analysis.initializeAnalysis();
        var flowGraph = analysis.findFlowGraphs();
        flowGraph.evaluate();

        return processPalladio(flowGraph);
    }

    /**
     * Converts a PCM model into a DataFlowDiagramAndDictionary object.
     * @param modelLocation Location of the model folder.
     * @param usageModelPath Location of the usage model.
     * @param allocationPath Location of the allocation.
     * @param nodeCharPath Location of the node characteristics.
     * @return DataFlowDiagramAndDictionary object representing the converted Palladio model.
     */
    public DataFlowDiagramAndDictionary pcmToDFD(String modelLocation, String usageModelPath, String allocationPath, String nodeCharPath) {
        DataFlowConfidentialityAnalysis analysis = new PCMDataFlowConfidentialityAnalysisBuilder().standalone()
                .modelProjectName(modelLocation)
                .useUsageModel(usageModelPath)
                .useAllocationModel(allocationPath)
                .useNodeCharacteristicsModel(nodeCharPath)
                .build();

        analysis.initializeAnalysis();
        var flowGraph = analysis.findFlowGraphs();
        flowGraph.evaluate();

        return processPalladio(flowGraph);
    }

    /**
     * This method compute the complete name of a PCM vertex depending on its type
     * @param vertex
     * @return String containing the complete name
     */
    public static String computeCompleteName(AbstractPCMVertex<?> vertex) {
        if (vertex instanceof SEFFPCMVertex<?> cast) {
            String elementName = cast.getReferencedElement()
                    .getEntityName();
            if (cast.getReferencedElement() instanceof StartAction) {
                Optional<ResourceDemandingSEFF> seff = PCMQueryUtils.findParentOfType(cast.getReferencedElement(), ResourceDemandingSEFF.class,
                        false);
                if (seff.isPresent()) {
                    elementName = "Beginning " + seff.get()
                            .getDescribedService__SEFF()
                            .getEntityName();
                }
                if (cast.isBranching() && seff.isPresent()) {
                    BranchAction branchAction = PCMQueryUtils.findParentOfType(cast.getReferencedElement(), BranchAction.class, false)
                            .orElseThrow(() -> new IllegalStateException("Cannot find branch action"));
                    AbstractBranchTransition branchTransition = PCMQueryUtils
                            .findParentOfType(cast.getReferencedElement(), AbstractBranchTransition.class, false)
                            .orElseThrow(() -> new IllegalStateException("Cannot find branch transition"));
                    elementName = "Branching " + seff.get()
                            .getDescribedService__SEFF()
                            .getEntityName() + "." + branchAction.getEntityName() + "." + branchTransition.getEntityName();
                }
            }
            if (cast.getReferencedElement() instanceof StopAction) {
                Optional<ResourceDemandingSEFF> seff = PCMQueryUtils.findParentOfType(cast.getReferencedElement(), ResourceDemandingSEFF.class,
                        false);
                if (seff.isPresent()) {
                    elementName = "Ending " + seff.get()
                            .getDescribedService__SEFF()
                            .getEntityName();
                }
            }
            return elementName;
        }
        if (vertex instanceof UserPCMVertex<?> cast) {
            if (cast.getReferencedElement() instanceof Start || cast.getReferencedElement() instanceof Stop) {
                return cast.getEntityNameOfScenarioBehaviour();
            }
            return cast.getReferencedElement()
                    .getEntityName();
        }
        if (vertex instanceof CallingSEFFPCMVertex cast) {
            return cast.getReferencedElement()
                    .getEntityName();
        }
        if (vertex instanceof CallingUserPCMVertex cast) {
            return cast.getReferencedElement()
                    .getEntityName();
        }
        return vertex.getReferencedElement()
                .getEntityName();
    }

    private DataFlowDiagramAndDictionary processPalladio(FlowGraphCollection flowGraphCollection) {
        dataDictionary = datadictionaryFactory.eINSTANCE.createDataDictionary();
        dataFlowDiagram = dataflowdiagramFactory.eINSTANCE.createDataFlowDiagram();
        for (AbstractTransposeFlowGraph transposeFlowGraph : flowGraphCollection.getTransposeFlowGraphs()) {
        	var sink = transposeFlowGraph.getSink();
	    	 if (sink instanceof AbstractPCMVertex<?> abstractPCMVertex) {
	         	processSink((AbstractPCMVertex<?>)sink);
	         }
        }
        System.out.println(dfdNodeMap.values().stream().filter(n -> n.getEntityName().equals("Ending addCustomer")).map(n -> n.getEntityName()).toList());
        return new DataFlowDiagramAndDictionary(dataFlowDiagram, dataDictionary);
    }

    private void processSink(AbstractPCMVertex<? extends Entity> pcmVertex) {
    	if (computeCompleteName(pcmVertex).equals("customer sends check out message")) {
    		System.out.println("Test");
    	}
    	Node node;
    	if (dfdNodeMap.get(pcmVertex) == null) {
    		node = getDFDNode(pcmVertex);
    	} else return;
    	
    	createPinsAndAssignementsFromVertex(node, pcmVertex);

    	pcmVertex.getPreviousElements().forEach(this::processSink);
    	
    	pcmVertex.getPreviousElements().forEach(previousElement -> createFlow(previousElement, pcmVertex));
    }
    
    private void createPinsAndAssignementsFromVertex(Node node, AbstractPCMVertex<? extends Entity> pcmVertex) {
    	var behaviour = node.getBehaviour();
    	pcmVertex.getAllIncomingDataCharacteristics().forEach(idc -> {
    		var pin = datadictionaryFactory.eINSTANCE.createPin();
    		pin.setEntityName(idc.getVariableName());
    		behaviour.getInPin().add(pin);
    	});
    	pcmVertex.getAllOutgoingDataCharacteristics().forEach(odc -> {
    		var pin = datadictionaryFactory.eINSTANCE.createPin();
    		pin.setEntityName(odc.getVariableName());
    		behaviour.getOutPin().add(pin);
    		
    		var assignment = datadictionaryFactory.eINSTANCE.createAssignment();
    		assignment.setEntityName(odc.getVariableName());
    		assignment.setTerm(datadictionaryFactory.eINSTANCE.createTRUE());
    		assignment.getOutputLabels().addAll(odc.getAllCharacteristics().stream().map(characteristicValue -> getOrCreateDFDLabel(characteristicValue)).toList());
    		assignment.setOutputPin(pin);
    		
    		behaviour.getAssignment().add(assignment);
    	});
    }
    
    private void createFlow(AbstractPCMVertex<? extends Entity> sourceVertex, AbstractPCMVertex<? extends Entity> destinationVertex) {
    	var intersectingDataCharacteristics = sourceVertex.getAllOutgoingDataCharacteristics().stream()
    			.map(odc -> odc.getVariableName())
    			.filter(odc -> {
    				return destinationVertex.getAllIncomingDataCharacteristics().stream().map(idc -> idc.getVariableName()).toList().contains(odc);
    			}).toList() ;  
    	if (intersectingDataCharacteristics.size() == 0) {
    		createEmptyFlowForNoDataCharacteristics(sourceVertex, destinationVertex);
    	} else {
    		createFlowForListOfCharacteristics(sourceVertex, destinationVertex, intersectingDataCharacteristics);
    	}
    	
    	
    	
    }
    
    private void createFlowForListOfCharacteristics(AbstractPCMVertex<? extends Entity> sourceVertex, AbstractPCMVertex<? extends Entity> destinationVertex, List<String> intersectingDataCharacteristics) {
    	var sourceNode = dfdNodeMap.get(sourceVertex);
    	var destinationNode = dfdNodeMap.get(destinationVertex);
    	for (var dataCharacteristic : intersectingDataCharacteristics) {
    		var flow = dataflowdiagramFactory.eINSTANCE.createFlow();
    		var inPin = destinationNode.getBehaviour().getInPin().stream().filter(pin -> pin.getEntityName().equals(dataCharacteristic)).toList().get(0);    		
    		var outPin = sourceNode.getBehaviour().getOutPin().stream().filter(pin -> pin.getEntityName().equals(dataCharacteristic)).toList().get(0);
    		
    		flow.setEntityName(dataCharacteristic);
    		flow.setDestinationNode(destinationNode);
    		flow.setSourceNode(sourceNode);
    		flow.setDestinationPin(inPin);
    		flow.setSourcePin(outPin);
    		
    		dataFlowDiagram.getFlows().add(flow);
    	}     	
    }
    
    private void createEmptyFlowForNoDataCharacteristics(AbstractPCMVertex<? extends Entity> sourceVertex, AbstractPCMVertex<? extends Entity> destinationVertex) {
    	var sourceNode = dfdNodeMap.get(sourceVertex);
    	var destinationNode = dfdNodeMap.get(destinationVertex);
    	
    	var flow = dataflowdiagramFactory.eINSTANCE.createFlow();
    	var inPin = datadictionaryFactory.eINSTANCE.createPin();
    	var outPin = datadictionaryFactory.eINSTANCE.createPin();
    	
    	inPin.setEntityName("");
    	outPin.setEntityName("");
    	
    	flow.setDestinationNode(destinationNode);
    	flow.setSourceNode(sourceNode);
    	flow.setDestinationPin(inPin);
    	flow.setSourcePin(outPin);
    	flow.setEntityName("");
    	dataFlowDiagram.getFlows().add(flow);
    	
    	sourceNode.getBehaviour().getOutPin().add(outPin);
    	destinationNode.getBehaviour().getInPin().add(inPin);
    }

    private void createFlowBetweenPreviousAndCurrentNode(Node source, Node dest, AbstractPCMVertex<? extends Entity> pcmVertex) {
        if (source == null || dest == null) {
            return;
        }
        List<DataCharacteristic> dataCharacteristics = pcmVertex.getAllIncomingDataCharacteristics();
        if (dataCharacteristics.size() == 0) findOrCreateFlow(source, dest, "");
        for (DataCharacteristic dataCharacteristic : dataCharacteristics) {
            findOrCreateFlow(source, dest, dataCharacteristic.variableName());
        } 
    }

    private void findOrCreateFlow(Node source, Node dest, String flowName) {
        dataFlowDiagram.getFlows()
                .stream()
                .filter(f -> f.getSourceNode()
                        .equals(source))
                .filter(f -> f.getDestinationNode()
                        .equals(dest))
                .filter(f -> f.getEntityName()
                        .equals(flowName))
                .findFirst()
                .orElseGet(() -> createFlow(source, dest, flowName));
    }

    private Flow createFlow(Node source, Node dest, String flowName) {
        Flow newFlow = dataflowdiagramFactory.eINSTANCE.createFlow();
        newFlow.setSourceNode(source);
        newFlow.setDestinationNode(dest);
        newFlow.setEntityName(flowName);
        Pin sourceOutPin = findOutputPin(source, flowName);
        Pin destInPin = findInputPin(dest, flowName);
        newFlow.setSourcePin(sourceOutPin);
        newFlow.setDestinationPin(destInPin);

        this.dataFlowDiagram.getFlows()
                .add(newFlow);
        return newFlow;
    }
    
   
    

    // A pin is equivalent if the same parameters are passed
    private Pin findOutputPin(Node source, String parameters) {
        return source.getBehaviour()
                .getOutPin()
                .stream()
                .filter(p -> p.getEntityName()
                        .equals(parameters))
                .findAny()
                .orElseGet(() -> createPin(source, parameters, false));
    }

    // A pin is equivalent if the same parameters are passed
    private Pin findInputPin(Node dest, String parameters) {
        return dest.getBehaviour()
                .getInPin()
                .stream()
                .filter(p -> p.getEntityName()
                        .equals(parameters))
                .findAny()
                .orElseGet(() -> createPin(dest, parameters, true));
    }

    private Pin createPin(Node node, String parameters, boolean isInPin) {
        Pin pin = datadictionaryFactory.eINSTANCE.createPin();
        pin.setEntityName(parameters);
        if (isInPin) {
            node.getBehaviour()
                    .getInPin()
                    .add(pin);
        } else {
            node.getBehaviour()
                    .getOutPin()
                    .add(pin);
        }
        return pin;
    }

    private Node getDFDNode(AbstractPCMVertex<? extends Entity> pcmVertex) {
        Node dfdNode = dfdNodeMap.get(pcmVertex);

        if (dfdNode == null) {
            dfdNode = createDFDNode(pcmVertex);
        }

        addNodeCharacteristicsToNode(dfdNode, pcmVertex.getAllVertexCharacteristics());

        return dfdNode;
    }

    private Node createDFDNode(AbstractPCMVertex<? extends Entity> pcmVertex) {
        Node dfdNode = createCorrespondingDFDNode(pcmVertex);
        dfdNodeMap.put(pcmVertex, dfdNode);
        return dfdNode;
    }

    private Node createCorrespondingDFDNode(AbstractPCMVertex<? extends Entity> pcmVertex) {
        Node node;

        if (pcmVertex instanceof UserPCMVertex<?>) {
            node = dataflowdiagramFactory.eINSTANCE.createExternal();
        } else if (pcmVertex instanceof SEFFPCMVertex<?>) {
            node = dataflowdiagramFactory.eINSTANCE.createProcess();
        } else {
            logger.error("Unregcognized palladio element");
            return null;
        }

        Behaviour behaviour = datadictionaryFactory.eINSTANCE.createBehaviour();

        node.setEntityName(computeCompleteName(pcmVertex));
        
        var id = pcmVertex.getReferencedElement()
                .getId();
        if (takenIds.contains(id)) id += "_1";
        
        node.setId(id);
        takenIds.add(id);
        node.setBehaviour(behaviour);
        dataDictionary.getBehaviour()
                .add(behaviour);
        dataFlowDiagram.getNodes()
                .add(node);
        return node;
    }

    private void addNodeCharacteristicsToNode(Node node, List<CharacteristicValue> charValues) {
        for (CharacteristicValue charValue : charValues) {
            Label label = getOrCreateDFDLabel(charValue);
            if (!node.getProperties()
                    .contains(label)) {
                node.getProperties()
                        .add(label);
            }
        }
    }

    private Label getOrCreateDFDLabel(CharacteristicValue charValue) {
        LabelType type = dataDictionary.getLabelTypes()
                .stream()
                .filter(f -> f.getEntityName()
                        .equals(charValue.getTypeName()))
                .findFirst()
                .orElseGet(() -> createLabelType(charValue));

        Label label = type.getLabel()
                .stream()
                .filter(f -> f.getEntityName()
                        .equals(charValue.getValueName()))
                .findFirst()
                .orElseGet(() -> createLabel(charValue, type));

        return label;
    }

    private Label createLabel(CharacteristicValue charValue, LabelType type) {
        Label label = datadictionaryFactory.eINSTANCE.createLabel();
        label.setEntityName(charValue.getValueName());
        type.getLabel()
                .add(label);
        return label;
    }

    private LabelType createLabelType(CharacteristicValue charValue) {
        LabelType type = datadictionaryFactory.eINSTANCE.createLabelType();
        type.setEntityName(charValue.getTypeName());
        this.dataDictionary.getLabelTypes()
                .add(type);
        return type;
    }

}
