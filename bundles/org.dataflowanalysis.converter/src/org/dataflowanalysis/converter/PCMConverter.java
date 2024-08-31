package org.dataflowanalysis.converter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

import org.apache.log4j.Level;
import org.dataflowanalysis.analysis.DataFlowConfidentialityAnalysis;
import org.dataflowanalysis.analysis.core.AbstractTransposeFlowGraph;
import org.dataflowanalysis.analysis.core.CharacteristicValue;
import org.dataflowanalysis.analysis.core.FlowGraphCollection;
import org.dataflowanalysis.analysis.core.DataCharacteristic;
import org.dataflowanalysis.analysis.pcm.PCMDataFlowConfidentialityAnalysisBuilder;
import org.dataflowanalysis.analysis.pcm.core.AbstractPCMVertex;
import org.dataflowanalysis.analysis.pcm.core.seff.CallingSEFFPCMVertex;
import org.dataflowanalysis.analysis.pcm.core.seff.SEFFPCMVertex;
import org.dataflowanalysis.analysis.pcm.core.user.CallingUserPCMVertex;
import org.dataflowanalysis.analysis.pcm.core.user.UserPCMVertex;
import org.dataflowanalysis.analysis.pcm.utils.PCMQueryUtils;
import org.dataflowanalysis.dfd.datadictionary.AbstractAssignment;
import org.dataflowanalysis.dfd.datadictionary.Assignment;
import org.dataflowanalysis.dfd.datadictionary.Behaviour;
import org.dataflowanalysis.dfd.datadictionary.DataDictionary;
import org.dataflowanalysis.dfd.datadictionary.Label;
import org.dataflowanalysis.dfd.datadictionary.LabelType;
import org.dataflowanalysis.dfd.datadictionary.Pin;
import org.dataflowanalysis.dfd.datadictionary.datadictionaryFactory;
import org.dataflowanalysis.dfd.dataflowdiagram.DataFlowDiagram;
import org.dataflowanalysis.dfd.dataflowdiagram.Node;
import org.dataflowanalysis.dfd.dataflowdiagram.dataflowdiagramFactory;
import org.dataflowanalysis.pcm.extension.dictionary.characterized.DataDictionaryCharacterized.Literal;
import org.dataflowanalysis.pcm.extension.dictionary.characterized.DataDictionaryCharacterized.expressions.Term;
import org.dataflowanalysis.pcm.extension.model.confidentiality.ConfidentialityVariableCharacterisation;
import org.dataflowanalysis.pcm.extension.model.confidentiality.expression.NamedEnumCharacteristicReference;
import org.eclipse.core.runtime.Plugin;
import org.palladiosimulator.pcm.core.entity.Entity;
import org.palladiosimulator.pcm.parameter.VariableUsage;
import org.palladiosimulator.pcm.seff.*;
import org.palladiosimulator.pcm.usagemodel.AbstractUserAction;
import org.palladiosimulator.pcm.usagemodel.EntryLevelSystemCall;
import org.palladiosimulator.pcm.usagemodel.Start;
import org.palladiosimulator.pcm.usagemodel.Stop;

import de.uka.ipd.sdq.stoex.AbstractNamedReference;
import org.dataflowanalysis.dfd.datadictionary.*;
import org.dataflowanalysis.pcm.extension.dictionary.characterized.DataDictionaryCharacterized.EnumCharacteristicType;
import org.dataflowanalysis.pcm.extension.dictionary.characterized.DataDictionaryCharacterized.expressions.And;
import org.dataflowanalysis.pcm.extension.dictionary.characterized.DataDictionaryCharacterized.expressions.False;
import org.dataflowanalysis.pcm.extension.dictionary.characterized.DataDictionaryCharacterized.expressions.Or;
import org.dataflowanalysis.pcm.extension.dictionary.characterized.DataDictionaryCharacterized.expressions.True;
import org.dataflowanalysis.pcm.extension.model.confidentiality.expression.LhsEnumCharacteristicReference;
import org.palladiosimulator.pcm.seff.ExternalCallAction;
import org.palladiosimulator.pcm.seff.SetVariableAction;

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
    	this.logger.setLevel(Level.TRACE);
        DataFlowConfidentialityAnalysis analysis = new PCMDataFlowConfidentialityAnalysisBuilder().standalone()
                .modelProjectName(modelLocation)
                .usePluginActivator(activator)
                .useUsageModel(usageModelPath)
                .useAllocationModel(allocationPath)
                .useNodeCharacteristicsModel(nodeCharPath)
                .build();

    	analysis.setLoggerLevel(Level.TRACE);
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
    	this.logger.setLevel(Level.TRACE);
    	DataFlowConfidentialityAnalysis analysis = new PCMDataFlowConfidentialityAnalysisBuilder().standalone()
                .modelProjectName(modelLocation)
                .useUsageModel(usageModelPath)
                .useAllocationModel(allocationPath)
                .useNodeCharacteristicsModel(nodeCharPath)
                .build();

    	analysis.setLoggerLevel(Level.TRACE);
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
        	transposeFlowGraph.getVertices()
        	.stream()
        	.filter(it -> it instanceof AbstractPCMVertex<?>)
        	.map(it -> (AbstractPCMVertex<?>) it)
        	.forEach(it -> processVertex(it));
        }
        for (AbstractTransposeFlowGraph transposeFlowGraph : flowGraphCollection.getTransposeFlowGraphs()) {
        	transposeFlowGraph.getVertices()
        	.stream()
        	.filter(it -> it instanceof AbstractPCMVertex<?>)
        	.map(it -> (AbstractPCMVertex<?>) it)
        	.forEach(it -> createFlowsForVertex(it));
        } 
        for (AbstractTransposeFlowGraph transposeFlowGraph : flowGraphCollection.getTransposeFlowGraphs()) {
        	transposeFlowGraph.getVertices()
        	.stream()
        	.filter(it -> it instanceof AbstractPCMVertex<?>)
        	.map(it -> (AbstractPCMVertex<?>) it)
        	.forEach(it -> createBehaviour(it));
        }
        
        flowGraphCollection.getTransposeFlowGraphs().stream().map(it -> it.getSink()).forEach(sink -> {
        	var node = dfdNodeMap.get(sink);
        	node.getBehaviour().getOutPin().clear();
        	node.getBehaviour().getAssignment().clear();
        });
        
        return new DataFlowDiagramAndDictionary(dataFlowDiagram, dataDictionary);
    }
    
    /**
     * Creates DFD Node from PCM Vertex and annotates the pins according to incoming and outgoing data characteristics
     * @param pcmVertex PCm Vertex to be converted
     */
    private void processVertex(AbstractPCMVertex<? extends Entity> pcmVertex) {
    	var node = getDFDNode(pcmVertex);
    	createPinsFromVertex(node, pcmVertex);    	
    }
    
    /**
     * Creates the flows to each DFD Node according to previous elements
     * @param pcmVertex PCMVertex whichs corresponding note is the flow target
     */
    private void createFlowsForVertex(AbstractPCMVertex<? extends Entity> pcmVertex) {
    	pcmVertex.getPreviousElements().forEach(previousElement -> createFlows(previousElement, pcmVertex));
    }
    
    
    /**
     * Creates the behaviour for the DFD Node from its corresponding PCM logic
     * @param pcmVertex PCMVertex whichs corresponding note is to be annotated
     */
    private void createBehaviour(AbstractPCMVertex<? extends Entity> pcmVertex) {
    	var node = getDFDNode(pcmVertex);
    	convertBehavior(pcmVertex, node, dataDictionary);
    }
    
    /**
     * Creates and adds the pins to the node supplied according to the incoming and outgoing data characteristics of the supplied vertex
     * @param node to be annotated
     * @param pcmVertex holding the data characteristics
     */
    private void createPinsFromVertex(Node node, AbstractPCMVertex<? extends Entity> pcmVertex) {
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
    	});
    }
    
    /**
     * Creates flows between the nodes corresponding to the source and destination vertex
     * @param sourceVertex
     * @param destinationVertex
     */
    private void createFlows(AbstractPCMVertex<? extends Entity> sourceVertex, AbstractPCMVertex<? extends Entity> destinationVertex) {
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
    
    /**
     * Creates flows in case of source and destination vertex holding similar data characteristics
     * @param sourceVertex
     * @param destinationVertex
     * @param intersectingDataCharacteristics
     */
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
    
    /**
     * Creates flow in case of no similar characteristics between source and destination node
     * @param sourceVertex
     * @param destinationVertex
     */
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
    	
    	var assignment = datadictionaryFactory.eINSTANCE.createAssignment();
    	assignment.setTerm(datadictionaryFactory.eINSTANCE.createTRUE());
    	
    	assignment.setOutputPin(outPin);
    }

   /**
    * Returns the corresponding DFD node or creates and annotates it with properties 
    * @param pcmVertex to be converted
    * @return The corresponding or created DFD Node
    */
    private Node getDFDNode(AbstractPCMVertex<? extends Entity> pcmVertex) {
        Node dfdNode = dfdNodeMap.get(pcmVertex);

        if (dfdNode == null) {
            dfdNode = createCorrespondingDFDNode(pcmVertex);
            addNodeCharacteristicsToNode(dfdNode, pcmVertex.getAllVertexCharacteristics());
            dfdNodeMap.put(pcmVertex, dfdNode);
        }        

        return dfdNode;
    }

    /**
     * Creates DFD Node from corresponding PCM Vertex
     * @param pcmVertex to be converted
     * @return new DFD Node
     */
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
        int occurrences = 1;
        while (takenIds.contains(id)) {
        	id = pcmVertex.getReferencedElement().getId() + "_" + occurrences;
        	occurrences++;
        }
        
        node.setId(id);
        takenIds.add(id);
        node.setBehaviour(behaviour);
        dataDictionary.getBehaviour()
                .add(behaviour);
        dataFlowDiagram.getNodes()
                .add(node);
        return node;
    }

    /**
     * Converts the CharacteristicsValues supplied into labels and adds them as DFD Node properties
     * @param node the node to add characteristics to
     * @param charValues the list of characteristic values
     */
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
    
    /**
     * Get or create a LabelType with the supplied String name
     * @param name the name of the LabelType
     * @return the LabelType
     */
    private LabelType getOrCreateLabelType(String name) {
    	 LabelType type = dataDictionary.getLabelTypes()
                 .stream()
                 .filter(f -> f.getEntityName()
                         .equals(name))
                 .findFirst()
                 .orElseGet(() -> createLabelType(name));
    	 
    	 return type;
    }

    /**
     * Get or create a Label from the supplied CharacteristicValue
     * @param charValue the CharacteristicValue
     * @return the Label
     */
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
    
    /**
     * Get or create a Label with the supplied String name and LabelType
     * @param labelName the name of the Label
     * @param type the LabelType
     * @return the Label
     */
    private Label getOrCreateDFDLabel(String labelName, LabelType type) {
        Label label = type.getLabel()
                .stream()
                .filter(f -> f.getEntityName()
                        .equals(labelName))
                .findFirst()
                .orElseGet(() -> createLabel(labelName, type));

        return label;
    }

    /**
     * Creates a Label from the supplied CharacteristicValue and adds it to the LabelType
     * @param charValue the CharacteristicValue
     * @param type the LabelType
     * @return the created Label
     */
    private Label createLabel(CharacteristicValue charValue, LabelType type) {
        Label label = datadictionaryFactory.eINSTANCE.createLabel();
        label.setEntityName(charValue.getValueName());
        type.getLabel()
                .add(label);
        return label;
    }
    
    /**
     * Creates a Label with the supplied name and adds it to the LabelType
     * @param name the name of the Label
     * @param type the LabelType
     * @return the created Label
     */
    private Label createLabel(String name, LabelType type) {
        Label label = datadictionaryFactory.eINSTANCE.createLabel();
        label.setEntityName(name);
        type.getLabel()
                .add(label);
        return label;
    }

    /**
     * Creates a LabelType from the supplied CharacteristicValue
     * @param charValue the CharacteristicValue
     * @return the created LabelType
     */
    private LabelType createLabelType(CharacteristicValue charValue) {
        LabelType type = datadictionaryFactory.eINSTANCE.createLabelType();
        type.setEntityName(charValue.getTypeName());
        this.dataDictionary.getLabelTypes()
                .add(type);
        return type;
    }
    
    /**
     * Creates a LabelType with the supplied name
     * @param name the name of the LabelType
     * @return the created LabelType
     */
    private LabelType createLabelType(String name) {
        LabelType type = datadictionaryFactory.eINSTANCE.createLabelType();
        type.setEntityName(name);
        this.dataDictionary.getLabelTypes()
                .add(type);
        return type;
    }
    
    /**
     * Determines the variable characterizations for a given PCM vertex
     * @param vertex Given PCM vertex of which the variable characterizations should be found
     * @return Returns a list of variable characterizations of the given vertex
     */
    private List<ConfidentialityVariableCharacterisation> getVariableCharacterizations(AbstractPCMVertex<?> vertex) {
    	if (vertex.getReferencedElement() instanceof SetVariableAction setVariableAction) {
            return setVariableAction.getLocalVariableUsages_SetVariableAction().stream()
            		.map(VariableUsage::getVariableCharacterisation_VariableUsage)
            		.flatMap(List::stream)
            		.filter(ConfidentialityVariableCharacterisation.class::isInstance)
            		.map(ConfidentialityVariableCharacterisation.class::cast)
            		.toList();
        } else if (vertex.getReferencedElement() instanceof ExternalCallAction externalCallAction
                && vertex instanceof CallingSEFFPCMVertex callingSEFFPCMVertex
                && callingSEFFPCMVertex.isCalling()) {
            return externalCallAction.getInputVariableUsages__CallAction().stream()
            		.map(VariableUsage::getVariableCharacterisation_VariableUsage)
            		.flatMap(List::stream)
            		.filter(ConfidentialityVariableCharacterisation.class::isInstance)
            		.map(ConfidentialityVariableCharacterisation.class::cast)
            		.toList();
        } else if (vertex.getReferencedElement() instanceof ExternalCallAction externalCallAction
                && vertex instanceof CallingSEFFPCMVertex callingSEFFPCMVertex
                && callingSEFFPCMVertex.isReturning()) {
            return externalCallAction.getReturnVariableUsage__CallReturnAction().stream()
            		.map(VariableUsage::getVariableCharacterisation_VariableUsage)
            		.flatMap(List::stream)
            		.filter(ConfidentialityVariableCharacterisation.class::isInstance)
            		.map(ConfidentialityVariableCharacterisation.class::cast)
            		.toList();
        } else if (vertex.getReferencedElement() instanceof EntryLevelSystemCall entryLevelSystemCall
                && vertex instanceof CallingUserPCMVertex callingUserPCMVertex
                && callingUserPCMVertex.isCalling()) {
            return entryLevelSystemCall.getInputParameterUsages_EntryLevelSystemCall().stream()
            		.map(VariableUsage::getVariableCharacterisation_VariableUsage)
            		.flatMap(List::stream)
            		.filter(ConfidentialityVariableCharacterisation.class::isInstance)
            		.map(ConfidentialityVariableCharacterisation.class::cast)
            		.toList();
        } else if (vertex.getReferencedElement() instanceof EntryLevelSystemCall entryLevelSystemCall
                && vertex instanceof CallingUserPCMVertex callingUserPCMVertex
                && callingUserPCMVertex.isReturning()) {
            return entryLevelSystemCall.getOutputParameterUsages_EntryLevelSystemCall().stream()
            		.map(VariableUsage::getVariableCharacterisation_VariableUsage)
            		.flatMap(List::stream)
            		.filter(ConfidentialityVariableCharacterisation.class::isInstance)
            		.map(ConfidentialityVariableCharacterisation.class::cast)
            		.toList();
        } else {
        	return List.of();
        }
    }
    
    /**
     * Determines the assignments for the DFD nodes that can be inferred by the characteristics of the PCM vertex
     * @param vertex PCM vertex of which the characteristics are read from
     * @param node DFD node to which the assignments should be added
     * @return Returns a list of assignments that should be added to the DFD node
     */
    private List<AbstractAssignment> getAssignments(AbstractPCMVertex<?> vertex, Node node) {
    	if (vertex instanceof UserPCMVertex<?> && vertex.getReferencedElement() instanceof Start) {
    		return List.of();
    	}
    	List<AbstractAssignment> assignments = new ArrayList<>();
    	
    	if (vertex.getAllIncomingDataCharacteristics().isEmpty()) {
    		vertex.getAllOutgoingDataCharacteristics().forEach(it -> {
    			Pin outPin = node.getBehaviour().getOutPin().stream()
                        .filter(pin -> pin.getEntityName().equals(it.getVariableName()))
                        .findAny().orElseThrow();
    			Assignment assignment = datadictionaryFactory.eINSTANCE.createAssignment();
    			assignment.setTerm(datadictionaryFactory.eINSTANCE.createTRUE());
    			assignment.setOutputPin(outPin);
    			assignment.getOutputLabels().addAll(it.getAllCharacteristics().stream().map(characteristicValue -> getOrCreateDFDLabel(characteristicValue)).toList());
    			assignments.add(assignment);
    		});
    	}
    	
        List<DataCharacteristic> dataCharacteristicsForwarded = vertex.getAllIncomingDataCharacteristics().stream()
        	.filter(it -> vertex.getAllOutgoingDataCharacteristics().stream().anyMatch(ot -> it.getVariableName().equals(ot.getVariableName())))
        	.toList();
        for(DataCharacteristic dataCharacteristic : dataCharacteristicsForwarded) {
            assignments.add(this.forwardCharacteristic(node, vertex, dataCharacteristic));          	
        }
        if (dataCharacteristicsForwarded.isEmpty() 
        		&& vertex.getAllIncomingDataCharacteristics().isEmpty()
        		&& vertex.getAllOutgoingDataCharacteristics().isEmpty()
        		&& !(vertex instanceof UserPCMVertex<?> && ((AbstractUserAction) vertex.getReferencedElement())
                    .getScenarioBehaviour_AbstractUserAction()
                    .getUsageScenario_SenarioBehaviour() != null)) {
        	assignments.add(this.preserveControlFlow(node, vertex));
        }
        return assignments;
    }
    
    /**
     * Creates an assignment that forwards the given data characteristics of the PCM vertex in the given DFD node
     * @param node DFD node that should receive the created assignment
     * @param vertex PCM vertex that determines the assignment behavior
     * @param dataCharacteristic Data characteristic that is forwarded
     * @return Returns an assignment that forwards the given data characteristic
     */
    private AbstractAssignment forwardCharacteristic(Node node, AbstractPCMVertex<?> vertex, DataCharacteristic dataCharacteristic) {
    	AbstractAssignment assignment = datadictionaryFactory.eINSTANCE.createForwardingAssignment();            	
        Pin inPin = node.getBehaviour().getInPin().stream()
        		.filter(it -> it.getEntityName().equals(dataCharacteristic.getVariableName()))
                .findAny().orElseThrow(() -> {
                	logger.error("Cannot find required in-pin " + dataCharacteristic.getVariableName() + " at vertex with name " + vertex);
                    return new NoSuchElementException();
                });
    	Pin outPin = node.getBehaviour().getOutPin().stream()
            .filter(it -> it.getEntityName().equals(dataCharacteristic.getVariableName()))
            .findAny()
            .orElseThrow(() -> {
                logger.error("Cannot find required out-pin " + dataCharacteristic.getVariableName() + " at vertex with name " + vertex);
                return new NoSuchElementException();
            });
    	assignment.getInputPins().add(inPin);
        assignment.setOutputPin(outPin);
        return assignment;
    }
    
    /**
     * Preserves the control flow between converted vertices, by forwarding an empty value
     * @param node DFD node that should reflect the control flow of the given PCM vertex
     * @param vertex PCM vertex that dictates the control flow
     * @return Returns an assignment that preserves the control flow between the elements in the conversion
     */
    private AbstractAssignment preserveControlFlow(Node node, AbstractPCMVertex<?> vertex) {
		AbstractAssignment assignment = datadictionaryFactory.eINSTANCE.createForwardingAssignment();            	
    	Pin inPin = node.getBehaviour().getInPin().stream()
                .filter(it -> it.getEntityName().equals(""))
                .findAny()
                .orElseThrow(() -> {
                	logger.error("Cannot find required in-pin with empty name at vertex with name " + vertex);
                	return new NoSuchElementException();
                });
		Pin outPin = node.getBehaviour().getOutPin().stream()
            .filter(it -> it.getEntityName().equals(""))
            .findAny()
            .orElseThrow(() -> {
            	logger.error("Cannot find required out-pin with empty name at vertex with name " + vertex);
            	return new NoSuchElementException();
            });
		assignment.getInputPins().add(inPin);
    	assignment.setOutputPin(outPin);
    	return assignment;
    }
    
    /**
     * Processes the given variable characterization with the given converted DFD behavior and node with a given PCM vertex
     * @param variableCharacterisation Variable characterization of the PCM vertex
     * @param behaviour Resulting behavior of the DFD node
     * @param node DFD node that is converted to
     * @param vertex PCM vertex that is converted from
     * @return Returns a list of all assignments that must be added to the DFD behavior to reflect the PCM variable characterization
     */ 
    private List<AbstractAssignment> processCharacterization(ConfidentialityVariableCharacterisation variableCharacterisation, Behaviour behaviour, Node node, AbstractPCMVertex<?> vertex) {
        var leftHandSide = (LhsEnumCharacteristicReference) variableCharacterisation.getLhs();

        EnumCharacteristicType characteristicType = (EnumCharacteristicType) leftHandSide.getCharacteristicType();
        Literal characteristicValue = leftHandSide.getLiteral();
        AbstractNamedReference reference = variableCharacterisation.getVariableUsage_VariableCharacterisation()
                .getNamedReference__VariableUsage();


        Term rightHandSide = variableCharacterisation.getRhs();

        if (characteristicType == null && characteristicValue == null) {
        	return List.of(this.processFullForwardingAssignment(node, rightHandSide, reference));
        } else if (characteristicValue == null) {
        	return this.processHalfForwardingAssignment(node, rightHandSide, reference, vertex, behaviour, characteristicType);
        } else {
        	return List.of(this.processAssignment(node, rightHandSide, behaviour, reference, characteristicType, characteristicValue));
        }
    }
    
    /**
     * Processes a full forwarding assignment on a PCM vertex, resulting in a single DFD forwarding assignment
     * @param node DFD node that is converted to
     * @param rightHandSide Term that determines the origin of the forwarded variable
     * @param reference Reference that determines the destination of the forwarded variable
     * @return Returns an DFD assignment that reflects the PCM full forwarding assignment
     */
    private AbstractAssignment processFullForwardingAssignment(Node node, Term rightHandSide, AbstractNamedReference reference) {
    	if (!(rightHandSide instanceof NamedEnumCharacteristicReference namedEnumCharacteristicReference)) {
    		return datadictionaryFactory.eINSTANCE.createForwardingAssignment();
    	}
    	ForwardingAssignment assignment = datadictionaryFactory.eINSTANCE.createForwardingAssignment();
        Pin outPin = node.getBehaviour().getOutPin().stream()
                .filter(it -> it.getEntityName().equals(reference.getReferenceName()))
                .findAny().orElseThrow();
        assignment.setOutputPin(outPin);    
        Pin inPin = node.getBehaviour().getInPin().stream()
        		.filter(it -> it.getEntityName().equals(namedEnumCharacteristicReference.getNamedReference().getReferenceName()) || 
                    		it.getEntityName().equals(""))
                .findAny()
                .orElseThrow();
        assignment.getInputPins().add(inPin);	   
        return assignment;
    }
    
    /**
     * Process a half forwarding assignment on a PCM vertex, resulting in multiple DFD assignments that model the assignment
     * @param node DFD node that is converted to
     * @param rightHandSide Term that determines the data characteristic origin
     * @param reference Reference that determines the data characteristic destination
     * @param vertex PCM vertex that has the given assignment
     * @param behaviour DFD behavior that is converted to
     * @param characteristicType PCM characteristic type that is forwarded
     * @return Returns a list of abstract assignments that reflects the forwarding of all characteristic values of a given type
     */
    private List<AbstractAssignment> processHalfForwardingAssignment(Node node, Term rightHandSide, AbstractNamedReference reference, AbstractPCMVertex<?> vertex, Behaviour behaviour, EnumCharacteristicType characteristicType) {
    	List<AbstractAssignment> assignments = new ArrayList<>();
    	List<DataCharacteristic> forwardedValues = vertex.getAllIncomingDataCharacteristics().stream()
    			.filter(it -> it.getVariableName().equals(reference.getReferenceName()))
    			.filter(it -> it.getAllCharacteristics().stream().anyMatch(dc -> dc.getTypeName().equals(characteristicType.getName())))
    			.toList();
    	for (DataCharacteristic forwardedValue : forwardedValues) {
    		for(CharacteristicValue forwardedCharacterisicValue : forwardedValue.getAllCharacteristics()) {
    			Assignment assignment = datadictionaryFactory.eINSTANCE.createAssignment();
        		LabelType labelType = dataDictionary.getLabelTypes().stream()
                        .filter(it -> it.getEntityName().equals(characteristicType.getName()))
                        .findAny().orElseThrow();

                Label label = labelType.getLabel().stream()
                        .filter(it -> it.getEntityName().equals(forwardedCharacterisicValue.getTypeName()))
                        .findAny().orElseThrow();
                assignment.getOutputLabels().add(label);

                Pin outPin = node.getBehaviour().getOutPin().stream()
                        .filter(it -> it.getEntityName().equals(reference.getReferenceName()))
                        .findAny().orElseThrow();
                assignment.setOutputPin(outPin);
                org.dataflowanalysis.dfd.datadictionary.Term term = parseTerm(rightHandSide, dataDictionary);
                assignment.setTerm(term);
                if (rightHandSide instanceof NamedEnumCharacteristicReference namedEnumCharacteristicReference) {
                    Pin inPin = node.getBehaviour().getInPin().stream()
                            .filter(it -> it.getEntityName().equals(namedEnumCharacteristicReference.getNamedReference().getReferenceName()))
                            .findAny().orElseThrow();
                    assignment.getInputPins().add(inPin);
                } else {
                	assignment.getInputPins().addAll(behaviour.getInPin());
                }
                assignments.add(assignment);
    		}
    	}
    	return assignments;
    }
    
    /**
     * Process an assignment on the given node with the given characteristic type and value as well as the pin
     * @param node DFD node that is converted to
     * @param rightHandSide Condition that reflects the value of the targeted data characteristic
     * @param behaviour DFD behavior that is converted to
     * @param reference Reference that determines the destination of the assignment
     * @param characteristicType Characteristic type that is assigned to
     * @param characteristicValue Characteristic value that is assigned to
     * @return Returns an assignment that models the PCM behavior at the given vertex
     */
    private AbstractAssignment processAssignment(Node node, Term rightHandSide, Behaviour behaviour, AbstractNamedReference reference, EnumCharacteristicType characteristicType, Literal characteristicValue) {
        Assignment assignment = datadictionaryFactory.eINSTANCE.createAssignment();
    	LabelType labelType = getOrCreateLabelType(characteristicType.getName());

        Label label = getOrCreateDFDLabel(characteristicValue.getName(), labelType);
        assignment.getOutputLabels().add(label);

        Pin outPin = node.getBehaviour().getOutPin().stream()
                .filter(it -> it.getEntityName().equals(reference.getReferenceName()))
                .findAny().orElseThrow();
        assignment.setOutputPin(outPin);
        org.dataflowanalysis.dfd.datadictionary.Term term = parseTerm(rightHandSide, dataDictionary);
        assignment.setTerm(term);
        if (rightHandSide instanceof NamedEnumCharacteristicReference namedEnumCharacteristicReference) {
            Pin inPin = node.getBehaviour().getInPin().stream()
                    .filter(it -> it.getEntityName().equals(namedEnumCharacteristicReference.getNamedReference().getReferenceName()))
                    .findAny().orElseThrow();
            assignment.getInputPins().add(inPin);
        } else {
        	assignment.getInputPins().addAll(behaviour.getInPin());
        }
        return assignment;
    }
    
    /**
     * Converts behavior from PCM Vertex to Node and adds it to the Data Dictionary
     * @param pcmVertex the PCM Vertex
     * @param node the DFD Node
     * @param dataDictionary the Data Dictionary
     */
    public void convertBehavior(AbstractPCMVertex<?> pcmVertex, Node node, DataDictionary dataDictionary) {
    	List<ConfidentialityVariableCharacterisation> variableCharacterisations = this.getVariableCharacterizations(pcmVertex);

        Behaviour behaviour = node.getBehaviour();
        List<AbstractAssignment> assignments = this.getAssignments(pcmVertex, node);
        for (ConfidentialityVariableCharacterisation variableCharacterization : variableCharacterisations) {
        	assignments.addAll(this.processCharacterization(variableCharacterization, behaviour, node, pcmVertex));
        }
        behaviour.getAssignment().clear();
        behaviour.getAssignment().addAll(assignments);

        behaviour.getOutPin().stream().forEach(pin -> {
        	Assignment assignment = datadictionaryFactory.eINSTANCE.createAssignment();
        	assignment.setEntityName("Dummy Assignment");
        	assignment.getInputPins().addAll(behaviour.getInPin());
        	assignment.setOutputPin(pin);
        	assignment.setTerm(datadictionaryFactory.eINSTANCE.createTRUE());
        	behaviour.getAssignment().add(assignment);
        });
    }

    /**
     * Parses a Term object into a corresponding Data Dictionary Term
     * @param rightHandSide the Term object to parse
     * @param dataDictionary the Data Dictionary
     * @return the parsed Term
     */
    public org.dataflowanalysis.dfd.datadictionary.Term parseTerm(Term rightHandSide, DataDictionary dataDictionary) {
        if(rightHandSide instanceof True) {
            return datadictionaryFactory.eINSTANCE.createTRUE();
        } else if (rightHandSide instanceof False) {
            NOT term = datadictionaryFactory.eINSTANCE.createNOT();
            term.setNegatedTerm(datadictionaryFactory.eINSTANCE.createTRUE());
            return term;
        } else if (rightHandSide instanceof Or or) {
            OR term = datadictionaryFactory.eINSTANCE.createOR();
            term.getTerms().add(parseTerm(or.getLeft(), dataDictionary));
            term.getTerms().add(parseTerm(or.getRight(), dataDictionary));
            return term;
        } else if (rightHandSide instanceof And and) {
            AND term = datadictionaryFactory.eINSTANCE.createAND();
            term.getTerms().add(parseTerm(and.getLeft(), dataDictionary));
            term.getTerms().add(parseTerm(and.getRight(), dataDictionary));
            return term;
        } else if (rightHandSide instanceof NamedEnumCharacteristicReference characteristicReference) {
            LabelReference term = datadictionaryFactory.eINSTANCE.createLabelReference();
            LabelType labelType = dataDictionary.getLabelTypes().stream()
                    .filter(it -> it.getEntityName().equals(characteristicReference.getCharacteristicType().getName()))
                    .findAny().orElseThrow();
            Label label = labelType.getLabel().stream()
                    .filter(it -> it.getEntityName().equals(characteristicReference.getLiteral().getName()))
                    .findAny().orElseThrow();
            term.setLabel(label);
        }
        throw new IllegalStateException();
    }
}

