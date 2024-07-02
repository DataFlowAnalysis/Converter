package org.dataflowanalysis.converter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

import org.dataflowanalysis.analysis.DataFlowConfidentialityAnalysis;
import org.dataflowanalysis.analysis.core.AbstractTransposeFlowGraph;
import org.dataflowanalysis.analysis.core.CharacteristicValue;
import org.dataflowanalysis.analysis.core.FlowGraphCollection;
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
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.palladiosimulator.pcm.core.entity.Entity;
import org.palladiosimulator.pcm.parameter.VariableUsage;
import org.palladiosimulator.pcm.seff.*;
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
    	convertBehavior(pcmVertex, node, dataDictionary);
    	

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
    
    public void convertBehavior(AbstractPCMVertex<?> pcmVertex, Node node, DataDictionary dataDictionary) {
        List<ConfidentialityVariableCharacterisation> variableCharacterisations = new ArrayList<>();
        if (pcmVertex.getReferencedElement() instanceof SetVariableAction setVariableAction) {
            variableCharacterisations.addAll(setVariableAction.getLocalVariableUsages_SetVariableAction().stream().map(VariableUsage::getVariableCharacterisation_VariableUsage).flatMap(List::stream).filter(ConfidentialityVariableCharacterisation.class::isInstance).map(ConfidentialityVariableCharacterisation.class::cast).toList());
        } else if (pcmVertex.getReferencedElement() instanceof ExternalCallAction externalCallAction
                && pcmVertex instanceof CallingSEFFPCMVertex callingSEFFPCMVertex
                && callingSEFFPCMVertex.isCalling()) {
            variableCharacterisations.addAll(externalCallAction.getInputVariableUsages__CallAction().stream().map(VariableUsage::getVariableCharacterisation_VariableUsage).flatMap(List::stream).filter(ConfidentialityVariableCharacterisation.class::isInstance).map(ConfidentialityVariableCharacterisation.class::cast).toList());
        } else if (pcmVertex.getReferencedElement() instanceof ExternalCallAction externalCallAction
                && pcmVertex instanceof CallingSEFFPCMVertex callingSEFFPCMVertex
                && callingSEFFPCMVertex.isReturning()) {
            variableCharacterisations.addAll(externalCallAction.getReturnVariableUsage__CallReturnAction().stream().map(VariableUsage::getVariableCharacterisation_VariableUsage).flatMap(List::stream).filter(ConfidentialityVariableCharacterisation.class::isInstance).map(ConfidentialityVariableCharacterisation.class::cast).toList());
        } else if (pcmVertex.getReferencedElement() instanceof EntryLevelSystemCall entryLevelSystemCall
                && pcmVertex instanceof CallingUserPCMVertex callingUserPCMVertex
                && callingUserPCMVertex.isCalling()) {
            variableCharacterisations.addAll(entryLevelSystemCall.getInputParameterUsages_EntryLevelSystemCall().stream().map(VariableUsage::getVariableCharacterisation_VariableUsage).flatMap(List::stream).filter(ConfidentialityVariableCharacterisation.class::isInstance).map(ConfidentialityVariableCharacterisation.class::cast).toList());
        } else if (pcmVertex.getReferencedElement() instanceof EntryLevelSystemCall entryLevelSystemCall
                && pcmVertex instanceof CallingUserPCMVertex callingUserPCMVertex
                && callingUserPCMVertex.isReturning()) {
            variableCharacterisations.addAll(entryLevelSystemCall.getOutputParameterUsages_EntryLevelSystemCall().stream().map(VariableUsage::getVariableCharacterisation_VariableUsage).flatMap(List::stream).filter(ConfidentialityVariableCharacterisation.class::isInstance).map(ConfidentialityVariableCharacterisation.class::cast).toList());
        }

        Behaviour behaviour = EcoreUtil.copy(node.getBehaviour());
        List<AbstractAssignment> assignments = new ArrayList<>();
        if (!(pcmVertex instanceof UserPCMVertex<?> vertex && vertex.getReferencedElement() instanceof Start)) {
            assignments.add(datadictionaryFactory.eINSTANCE.createForwardingAssignment());
        }
        for (ConfidentialityVariableCharacterisation variableCharacterisation : variableCharacterisations) {
            var leftHandSide = (LhsEnumCharacteristicReference) variableCharacterisation.getLhs();

            EnumCharacteristicType characteristicType = (EnumCharacteristicType) leftHandSide.getCharacteristicType();
            Literal characteristicValue = leftHandSide.getLiteral();
            AbstractNamedReference reference = variableCharacterisation.getVariableUsage_VariableCharacterisation()
                    .getNamedReference__VariableUsage();


            Term rightHandSide = variableCharacterisation.getRhs();

            if (characteristicType == null && characteristicValue == null) {
            	ForwardingAssignment assignment = datadictionaryFactory.eINSTANCE.createForwardingAssignment();
                Pin outPin = node.getBehaviour().getOutPin().stream()
                        .filter(it -> it.getEntityName().equals(reference.getReferenceName()))
                        .findAny().orElseThrow();
                assignment.setOutputPin(outPin);     
                if (rightHandSide instanceof NamedEnumCharacteristicReference namedEnumCharacteristicReference) {
                    Pin inPin = node.getBehaviour().getInPin().stream()
                            .filter(it -> it.getEntityName().equals(namedEnumCharacteristicReference.getNamedReference().getReferenceName()))
                            .findAny().orElseThrow();
                    assignment.getInputPins().add(inPin);
                }
                assignments.add(assignment);
            } else if (characteristicType == null) {
            	throw new IllegalStateException("Forwarding of specific label types not supported");
            } else {
                Assignment assignment = datadictionaryFactory.eINSTANCE.createAssignment();
            	LabelType labelType = dataDictionary.getLabelTypes().stream()
                        .filter(it -> it.getEntityName().equals(characteristicType.getName()))
                        .findAny().orElseThrow();

                Label label = labelType.getLabel().stream()
                        .filter(it -> it.getEntityName().equals(characteristicValue.getName()))
                        .findAny().orElseThrow();
                assignment.getOutputLabels().add(label);

                Pin outPin = node.getBehaviour().getOutPin().stream()
                        .filter(it -> it.getEntityName().equals(reference.getReferenceName()))
                        .findAny().orElseThrow();
                assignment.setOutputPin(outPin);
                org.dataflowanalysis.dfd.datadictionary.Term term = parseTerm(rightHandSide, dataDictionary);
                if (rightHandSide instanceof NamedEnumCharacteristicReference namedEnumCharacteristicReference) {
                    assignment.setTerm(term);
                    Pin inPin = node.getBehaviour().getInPin().stream()
                            .filter(it -> it.getEntityName().equals(namedEnumCharacteristicReference.getNamedReference().getReferenceName()))
                            .findAny().orElseThrow();
                    assignment.getInputPins().add(inPin);
                }
                assignments.add(assignment);
            }
        }
        behaviour.getAssignment().clear();
        behaviour.getAssignment().addAll(assignments);
        node.setBehaviour(behaviour);
    }

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

