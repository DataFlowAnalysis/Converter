package org.dataflowanalysis.converter.tests;

import static org.dataflowanalysis.analysis.tests.AnalysisUtils.TEST_MODEL_PROJECT_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

import org.dataflowanalysis.analysis.DataFlowConfidentialityAnalysis;
import org.dataflowanalysis.converter.DataFlowDiagramAndDictionary;
import org.dataflowanalysis.converter.DataFlowDiagramConverter;
import org.dataflowanalysis.converter.PCMConverter;
import org.dataflowanalysis.examplemodels.Activator;
import org.dataflowanalysis.analysis.core.AbstractTransposeFlowGraph;
import org.dataflowanalysis.analysis.core.AbstractVertex;
import org.dataflowanalysis.analysis.core.CharacteristicValue;
import org.dataflowanalysis.analysis.core.DataCharacteristic;
import org.dataflowanalysis.analysis.core.FlowGraphCollection;
import org.dataflowanalysis.analysis.dfd.DFDDataFlowAnalysisBuilder;
import org.dataflowanalysis.analysis.dfd.core.*;
import org.dataflowanalysis.analysis.pcm.PCMDataFlowConfidentialityAnalysisBuilder;
import org.dataflowanalysis.analysis.pcm.core.AbstractPCMVertex;

import org.dataflowanalysis.dfd.datadictionary.Assignment;
import org.dataflowanalysis.dfd.datadictionary.DataDictionary;
import org.dataflowanalysis.dfd.datadictionary.ForwardingAssignment;
import org.dataflowanalysis.dfd.dataflowdiagram.DataFlowDiagram;
import org.dataflowanalysis.dfd.dataflowdiagram.Node;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.mdsd.library.standalone.initialization.StandaloneInitializationException;

public class PCMTest extends ConverterTest{
    @Test
    @DisplayName("Test PCM2DFD TravelPlanner")
    public void travelToDfd() {
        testSpecificModel("TravelPlanner", "travelPlanner", TEST_MODELS, null, null);
    }
	
	@Test
    @DisplayName("Test PCM2DFD MaaS")
    public void maasToDfd() {
        testSpecificModel("MaaS_Ticket_System_base", "MaaS", TEST_MODELS, "C:\\Users\\Huell\\Desktop\\Newfolder\\Maas_2.json", null);
    }
	
	@Test
    @DisplayName("Test PCM2DFD CWA")
    public void cwaToDfd() {
        testSpecificModel("CoronaWarnApp", "default", TEST_MODELS, "cwa.json", null);
    }

    @Test
    @Disabled("There is currently no manually converted pcm model")
    @DisplayName("Test manual Palladio to DFD")
    public void manualPCMToDfd() throws StandaloneInitializationException {
        String inputModel = "InternationalOnlineShop";
        String inputFile = "default";
        String dataflowdiagram = Paths.get("models", "OnlineShopDFD", "onlineshop.dataflowdiagram")
                .toString();
        String datadictionary = Paths.get("models", "OnlineShopDFD", "onlineshop.datadictionary")
                .toString();
        testSpecificModel(inputModel, inputFile, TEST_MODELS, null,
                new DataFlowDiagramConverter().loadDFD(TEST_MODELS, dataflowdiagram, datadictionary, Activator.class));

    }
    
    private void testSpecificModel(String inputModel, String inputFile, String modelLocation, String webTarget,
            DataFlowDiagramAndDictionary complete) {
        final var usageModelPath = Paths.get("models", inputModel, inputFile + ".usagemodel")
                .toString();
        final var allocationPath = Paths.get("models", inputModel, inputFile + ".allocation")
                .toString();
        final var nodeCharPath = Paths.get("models", inputModel, inputFile + ".nodecharacteristics")
                .toString();

        DataFlowConfidentialityAnalysis analysis = new PCMDataFlowConfidentialityAnalysisBuilder().standalone()
                .modelProjectName(modelLocation)
                .usePluginActivator(Activator.class)
                .useUsageModel(usageModelPath)
                .useAllocationModel(allocationPath)
                .useNodeCharacteristicsModel(nodeCharPath)
                .build();

        analysis.initializeAnalysis();
        var flowGraph = analysis.findFlowGraphs();
        flowGraph.evaluate();

        Map<String, String> assIdToName = new HashMap<>();
        for (AbstractTransposeFlowGraph transposeFlowGraph : flowGraph.getTransposeFlowGraphs()) {
            for (AbstractVertex<?> abstractVertex : transposeFlowGraph.getVertices()) {
                var cast = (AbstractPCMVertex<?>) abstractVertex;
                assIdToName.putIfAbsent(cast.getReferencedElement()
                        .getId(), PCMConverter.computeCompleteName(cast));
            }
        }

        if (complete == null) {
            complete = new PCMConverter().pcmToDFD(modelLocation, usageModelPath, allocationPath, nodeCharPath, Activator.class);
        }
        
        var test = complete.dataFlowDiagram().getNodes().stream().filter(n -> n.getEntityName().equals("Ending addCustomer")).toList();

        if (webTarget != null) {
            var dfdConverter = new DataFlowDiagramConverter();
            var web = dfdConverter.dfdToWeb(complete);
            dfdConverter.storeWeb(web, webTarget);
        }
        
        flowGraph.getTransposeFlowGraphs().forEach(tfg -> {
        	tfg.getVertices().forEach(v -> {
        		v.getAllOutgoingDataCharacteristics().forEach(dc -> {
        			if (dc.getVariableName().equals("staffAuthenticationCredentials")) {
        				System.out.println();
        			};
        			assert(!dc.getAllCharacteristics().isEmpty());
        		});
        	});
        });

        DataFlowDiagram dfd = complete.dataFlowDiagram();
        var dd = complete.dataDictionary();
        
        var t = new DFDTransposeFlowGraphFinder(dd, dfd);
        var fg = t.findTransposeFlowGraphs();
        

        assertEquals(dfd.getNodes()
                .size(),
                assIdToName.keySet()
                        .size());

        List<String> nodeIds = new ArrayList<>();
        for (Node node : dfd.getNodes()) {
            nodeIds.add(node.getId());
        }
        Collections.sort(nodeIds);
        List<String> assIds = new ArrayList<>(assIdToName.keySet());
        Collections.sort(assIds);

        assertEquals(assIds, nodeIds);

        for (Node node : dfd.getNodes()) {
            assertEquals(node.getEntityName(), assIdToName.get(node.getId()));
        }

        Set<String> flowNames = new HashSet<>();
        for (AbstractTransposeFlowGraph as : flowGraph.getTransposeFlowGraphs()) {
            AbstractVertex<?> previousAse = null;
            for (AbstractVertex<?> ase : as.getVertices()) {
                if(!ase.getPreviousElements().isEmpty()) {
                    List<DataCharacteristic> variables = ase.getAllDataCharacteristics();
                    if (variables.isEmpty()) flowNames.add(previousAse.hashCode()+ase.hashCode()+"");
                    for (DataCharacteristic variable : variables) {
                        flowNames.add(previousAse.hashCode()+ase.hashCode()+variable.variableName());
                    } 
                }
                previousAse=ase;
            }
        }

        assertEquals(flowNames.size(), dfd.getFlows()
                .size());
                
        

        // When transforming PCM to DFD, we represent all outputs through forwarding assignments.
        // This approach omits certain behaviors and labels that are not essential for visual representation.
        for (var behavior : dd.getBehaviour()) {
            for (var assignment : behavior.getAssignment()) {
                assertTrue(assignment instanceof Assignment);
            }
        }

        checkLabels(dd, flowGraph);
    }

    private void checkLabels(DataDictionary dd, FlowGraphCollection flowGraph) {
        Map<String, CharacteristicValue> chars = new HashMap<>();
        for (var pfg : flowGraph.getTransposeFlowGraphs()) {
            for (var vertex : pfg.getVertices()) {
                for (var nodeChar : vertex.getAllVertexCharacteristics()) {
                    chars.putIfAbsent(nodeChar.getValueId(), nodeChar);
                }
            }
        }

        List<String> labelsPCM = chars.values()
                .stream()
                .map(c -> c.getTypeName() + "." + c.getValueName())
                .collect(Collectors.toList());
        List<String> labelsDFD = new ArrayList<>();

        Map<String, List<String>> labelMap = new HashMap<>();
        for (var labelType : dd.getLabelTypes()) {
            labelMap.put(labelType.getEntityName(), new ArrayList<>());
            for (var label : labelType.getLabel()) {
                var labels = labelMap.get(labelType.getEntityName());
                // prevent duplicate labels
                assertTrue(!labels.contains(label.getEntityName()));
                labels.add(label.getEntityName());
                labelMap.put(labelType.getEntityName(), labels);
                labelsDFD.add(labelType.getEntityName() + "." + label.getEntityName());
            }
        }

        Collections.sort(labelsPCM);
        Collections.sort(labelsDFD);

        assertEquals(labelsPCM, labelsDFD);
    }
}
