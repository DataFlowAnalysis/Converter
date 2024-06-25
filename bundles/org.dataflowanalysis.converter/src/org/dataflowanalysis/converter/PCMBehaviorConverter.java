package org.dataflowanalysis.converter;

import org.dataflowanalysis.analysis.pcm.core.AbstractPCMVertex;
import org.dataflowanalysis.analysis.pcm.core.seff.CallingSEFFPCMVertex;
import org.dataflowanalysis.analysis.pcm.core.user.CallingUserPCMVertex;
import org.dataflowanalysis.analysis.pcm.core.user.UserPCMVertex;
import org.dataflowanalysis.dfd.datadictionary.*;
import org.dataflowanalysis.dfd.dataflowdiagram.Node;
import org.dataflowanalysis.pcm.extension.dictionary.characterized.DataDictionaryCharacterized.expressions.And;
import org.dataflowanalysis.pcm.extension.dictionary.characterized.DataDictionaryCharacterized.expressions.False;
import org.dataflowanalysis.pcm.extension.dictionary.characterized.DataDictionaryCharacterized.expressions.Or;
import org.dataflowanalysis.pcm.extension.dictionary.characterized.DataDictionaryCharacterized.expressions.Term;
import org.dataflowanalysis.pcm.extension.dictionary.characterized.DataDictionaryCharacterized.expressions.True;
import org.dataflowanalysis.pcm.extension.model.confidentiality.ConfidentialityVariableCharacterisation;
import org.dataflowanalysis.pcm.extension.model.confidentiality.expression.NamedEnumCharacteristicReference;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.palladiosimulator.pcm.parameter.VariableUsage;
import org.palladiosimulator.pcm.seff.ExternalCallAction;
import org.palladiosimulator.pcm.seff.SetVariableAction;
import org.palladiosimulator.pcm.usagemodel.EntryLevelSystemCall;
import org.palladiosimulator.pcm.usagemodel.Start;

import java.util.ArrayList;
import java.util.List;

public class PCMBehaviorConverter {

    public static void convertBehavior(AbstractPCMVertex<?> pcmVertex, Node node, DataDictionary dataDictionary) {
        List<ConfidentialityVariableCharacterisation> variableCharacterisations = new ArrayList<>();
        if (pcmVertex.getReferencedElement() instanceof SetVariableAction setVariableAction) {
            variableCharacterisations.addAll(setVariableAction.getLocalVariableUsages_SetVariableAction().stream().map(VariableUsage::getVariableCharacterisation_VariableUsage).flatMap(List::stream).filter(ConfidentialityVariableCharacterisation.class::isInstance).map(ConfidentialityVariableCharacterisation.class::cast).toList());
        } else if (pcmVertex.getReferencedElement() instanceof ExternalCallAction externalCallAction
        && pcmVertex instanceof CallingSEFFPCMVertex callingSEFFPCMVertex && callingSEFFPCMVertex.isCalling()) {
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
            //TODO: Parse Term from CVS 
            //TODO: Find Output Pin in DD 
            //TODO: Input pins are refrenced DFV in CVS
        }
        behaviour.getAssignment().clear();
        behaviour.getAssignment().addAll(assignments);
        node.setBehaviour(behaviour);
    }

    public static org.dataflowanalysis.dfd.datadictionary.Term parseTerm(Term rightHandSide, DataDictionary dataDictionary) {
        if(rightHandSide instanceof True) {
            return datadictionaryFactory.eINSTANCE.createTRUE();
        } else if (rightHandSide instanceof False) {
            NOT term = datadictionaryFactory.eINSTANCE.createNOT();
            term.setNegatedTerm(datadictionaryFactory.eINSTANCE.createTRUE());
            return term;
        } else if (rightHandSide instanceof Or or) {
            OR term = datadictionaryFactory.eINSTANCE.createOR();
            term.getTerms().add(PCMBehaviorConverter.parseTerm(or.getLeft(), dataDictionary));
            term.getTerms().add(PCMBehaviorConverter.parseTerm(or.getRight(), dataDictionary));
            return term;
        } else if (rightHandSide instanceof And and) {
            AND term = datadictionaryFactory.eINSTANCE.createAND();
            term.getTerms().add(PCMBehaviorConverter.parseTerm(and.getLeft(), dataDictionary));
            term.getTerms().add(PCMBehaviorConverter.parseTerm(and.getRight(), dataDictionary));
            return term;
        } else if (rightHandSide instanceof NamedEnumCharacteristicReference characteristicReference) {
            // TODO: Add variable name to term
            LabelReference term = datadictionaryFactory.eINSTANCE.createLabelReference();
            LabelType labelType = dataDictionary.getLabelTypes().stream()
                    .filter(it -> it.getEntityName().equals(characteristicReference.getCharacteristicType().getName()))
                    .findAny().orElseThrow();
            Label label = labelType.getLabel().stream()
                    .filter(it -> it.getEntityName().equals(characteristicReference.getLiteral().getName()))
                    .findAny().orElseThrow();
            term.setLabel(label);
            term.set
        }
    }
}
