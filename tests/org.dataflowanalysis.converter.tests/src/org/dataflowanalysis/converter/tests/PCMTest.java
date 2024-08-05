package org.dataflowanalysis.converter.tests;

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

import org.apache.log4j.Level;
import org.dataflowanalysis.analysis.DataFlowConfidentialityAnalysis;
import org.dataflowanalysis.converter.DataFlowDiagramConverter;
import org.dataflowanalysis.converter.PCMConverter;
import org.dataflowanalysis.examplemodels.Activator;
import org.dataflowanalysis.analysis.core.AbstractTransposeFlowGraph;
import org.dataflowanalysis.analysis.core.AbstractVertex;
import org.dataflowanalysis.analysis.core.CharacteristicValue;
import org.dataflowanalysis.analysis.core.FlowGraphCollection;
import org.dataflowanalysis.analysis.pcm.PCMDataFlowConfidentialityAnalysisBuilder;
import org.dataflowanalysis.analysis.pcm.core.AbstractPCMVertex;
import org.dataflowanalysis.dfd.datadictionary.DataDictionary;
import org.dataflowanalysis.dfd.dataflowdiagram.Node;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PCMTest extends ConverterTest{
    @Test
    @DisplayName("Test PCM2DFD TravelPlanner")
    public void travelToDfd() {
        testSpecificModel("TravelPlanner", "travelPlanner", TEST_MODELS, "tp.json");
    }
	
	@Test
    @DisplayName("Test PCM2DFD MaaS")
    public void maasToDfd() {
        testSpecificModel("MaaS_Ticket_System_base", "MaaS", TEST_MODELS, "maas.json");
    }
	
	@Test
    @DisplayName("Test PCM2DFD CWA")
    public void cwaToDfd() {
        testSpecificModel("CoronaWarnApp", "default", TEST_MODELS, "cwa.json");
    }
    
    private void testSpecificModel(String inputModel, String inputFile, String modelLocation, String webTarget) {
        final var usageModelPath = Paths.get("casestudies", inputModel, inputFile + ".usagemodel")
                .toString();
        final var allocationPath = Paths.get("casestudies", inputModel, inputFile + ".allocation")
                .toString();
        final var nodeCharPath = Paths.get("casestudies", inputModel, inputFile + ".nodecharacteristics")
                .toString();

        DataFlowConfidentialityAnalysis analysis = new PCMDataFlowConfidentialityAnalysisBuilder().standalone()
                .modelProjectName(modelLocation)
                .usePluginActivator(Activator.class)
                .useUsageModel(usageModelPath)
                .useAllocationModel(allocationPath)
                .useNodeCharacteristicsModel(nodeCharPath)
                .build();
        
        analysis.setLoggerLevel(Level.ALL);

        analysis.initializeAnalysis();
        var flowGraph = analysis.findFlowGraphs();
        flowGraph.evaluate();

       	List<AbstractPCMVertex<?>> vertices = new ArrayList<>();
        for (AbstractTransposeFlowGraph transposeFlowGraph : flowGraph.getTransposeFlowGraphs()) {
            for (AbstractVertex<?> abstractVertex : transposeFlowGraph.getVertices()) {
            	AbstractPCMVertex<?> v = (AbstractPCMVertex<?>) abstractVertex;
               vertices.add(v);
            }
        }

        
        var complete = new PCMConverter().pcmToDFD(modelLocation, usageModelPath, allocationPath, nodeCharPath, Activator.class);

        var dfdConverter = new DataFlowDiagramConverter();
        var web = dfdConverter.dfdToWeb(complete);
        dfdConverter.storeWeb(web, webTarget);

        var dfd = complete.dataFlowDiagram();
        var dd = complete.dataDictionary();
        

        assertEquals(dfd.getNodes().size(), vertices.size());

        List<String> nodeIds = new ArrayList<>();
        for (Node node : dfd.getNodes()) {
            nodeIds.add(node.getId());
        }
        


        checkLabels(dd, flowGraph);
    }
    
    

    private void checkLabels(DataDictionary dd, FlowGraphCollection flowGraph) {
       Set<CharacteristicValue> values = new HashSet<>();
        for (var pfg : flowGraph.getTransposeFlowGraphs()) {
            for (var vertex : pfg.getVertices()) {
                for (var nodeChar : vertex.getAllVertexCharacteristics()) {
                	values.add(nodeChar);
                }
                for (var dataChar : vertex.getAllIncomingDataCharacteristics()) {
                	for (var charValue : dataChar.getAllCharacteristics()) {
                		values.add(charValue);
                	}
                }
            }
        }

        List<String> labelsPCM = values
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
