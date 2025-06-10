package com.lamsili.canc.app;

import com.lamsili.canc.fca.concept.FormalConcept;
import com.lamsili.canc.rules.Rule;
import com.lamsili.canc.varriants.Variant;
import com.yahoo.labs.samoa.instances.Instance;
import java.text.DecimalFormat;
import java.util.*;

/**
 * CANCDebugger - Version simplifiée pour le débogage de l'algorithme CANC
 */
public class CANCDebugger {

    private static boolean debugEnabled = false;
    private static final DecimalFormat df = new DecimalFormat("0.0000");

    /**
     * Active ou désactive le débogage
     */
    public static void setDebugEnabled(boolean enable) {
        debugEnabled = enable;
    }

    /**
     * Vérifie si le débogage est activé
     */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Affiche un message horodaté
     */
    public static void printTimestampedMessage(String message) {
        if (!debugEnabled) return;
        System.out.println("-----------[DEBUG]-----------");
        System.out.println(message);
    }

    /**
     * Affiche l'initialisation de l'extracteur de règles
     */
    public static void printRuleExtractorInit(Variant variant) {
        if (!debugEnabled) return;
        printTimestampedMessage("RuleExtractor initialized\nVariant: " + variant);
    }

    /**
     * Affiche un en-tête de débogage
     */
    public static void printDebugHeader(String title) {
        if (!debugEnabled) return;
        System.out.println("\n===============================");
        System.out.println(" " + title);
        System.out.println("===============================");
    }

    /**
     * Affiche le pied de page du débogage
     */
    public static void printDebugFooter() {
        if (!debugEnabled) return;
        System.out.println("===============================");
    }

    /**
     * Affiche une section de débogage avec un titre
     */
    public static void printDebugSection(String title, String message) {
        if (!debugEnabled) return;
        System.out.println("\n=== " + title + " ===");
        System.out.println(message);
    }

    /**
     * Affiche un séparateur de section avec un titre
     */
    public static void printSectionSeparator(String title) {
        if (!debugEnabled) return;
        System.out.println("\n---------- " + title + " ----------");
    }

    /**
     * Affiche le processus détaillé de sélection de l'attribut pertinent
     */
    public static void printAttributeSelectionProcess(Map<String, Double> attributeEntropies,
                                                     Map<String, Double> classDistribution,
                                                     String selectedAttribute) {
        if (!debugEnabled) return;

        // Vérifier si on est dans une variante qui utilise l'attribut pertinent
        if (selectedAttribute == null || selectedAttribute.isEmpty()) {
            // Pour les variantes CaNC, afficher simplement les informations des attributs
            printSectionSeparator("SCORES D'INFORMATION DES ATTRIBUTS");

            // Calcul de l'entropie globale
            double globalEntropy = calculateEntropy(classDistribution.values());
            System.out.println("\nEntropie globale H(S): " + df.format(globalEntropy));

            // Afficher les attributs et leurs scores
            System.out.println("\nAttributs et scores d'information:");
            for (Map.Entry<String, Double> entry : attributeEntropies.entrySet()) {
                double informationGain = globalEntropy - entry.getValue();
                System.out.println("  - " + entry.getKey() + ": H(S|a)=" + df.format(entry.getValue()) +
                                  ", IG(a)=" + df.format(informationGain));
            }
        } else {
            // Pour les variantes CpNC (attribut pertinent)
            System.out.println("\n=== SCORES D'INFORMATION ===");

            // Calcul de l'entropie globale
            double globalEntropy = calculateEntropy(classDistribution.values());
            System.out.println("Entropie globale H(S): " + df.format(globalEntropy));

            // En-tête du tableau
            System.out.println("\nRank  Attribute      H(S|a)   IG(a)");
            System.out.println("----- -------------- ------- -------");

            // Trier les attributs par gain d'information (décroissant)
            List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(attributeEntropies.entrySet());
            sortedEntries.sort((e1, e2) -> {
                double ig1 = globalEntropy - e1.getValue();
                double ig2 = globalEntropy - e2.getValue();
                return Double.compare(ig2, ig1); // tri décroissant par IG
            });

            // Afficher les attributs et leurs scores dans le format tabulaire
            int rank = 1;
            for (Map.Entry<String, Double> entry : sortedEntries) {
                String attrName = entry.getKey();
                double conditionalEntropy = entry.getValue();
                double informationGain = globalEntropy - conditionalEntropy;

                String marker = attrName.equals(selectedAttribute) ? " ← sélectionné" : "";
                System.out.printf("  %-4d %-14s %7s %7s%s%n",
                                 rank,
                                 attrName,
                                 df.format(conditionalEntropy),
                                 df.format(informationGain),
                                 marker);
                rank++;
            }

            System.out.println("\nAttribut pertinent = IG max (last-attr tie-break)");
        }
    }

    /**
     * Affiche les détails de débogage spécifiques à une valeur d'attribut
     */
    public static void printAttributeValueDebug(String attrValue, Map<String, Object> debugInfo) {
        // Désactivé pour toutes les variantes - ne rien afficher
        return;
    }

    /**
     * Affiche le calcul de la valeur pertinente pour un attribut
     *
     * @param attribute Attribut concerné
     * @param valueScores Map des scores pour chaque valeur de l'attribut
     * @param selectedValue Valeur sélectionnée comme la plus pertinente
     */
    public static void printRelevantValueCalculation(String attribute, Map<String, Double> valueScores, String selectedValue) {
        // Désactivé pour toutes les variantes - ne rien afficher
        return;
    }

    /**
     * Affiche les concepts formels générés
     */
    public static void printConcepts(int numConcepts, String conceptsDescription) {
        if (!debugEnabled) return;
        System.out.println("\n=== CONCEPTS FORMELS ===");
        System.out.println("Nombre de concepts: " + numConcepts);
        System.out.println(conceptsDescription);
    }

    /**
     * Affiche les règles extraites
     */
    public static void printRules(String rules) {
        if (!debugEnabled) return;
        System.out.println("\n=== RÈGLES EXTRAITES ===");
        System.out.println(rules);
    }

    /**
     * Affiche les détails de classification d'une instance
     */
    public static void printClassificationDebug(Instance instance, List<Map<String, Object>> matchedRules,
            Map<String, Double> classScores, String finalDecision, double finalScore) {
        if (!debugEnabled) return;

        System.out.println("\n=== CLASSIFICATION ===");
        System.out.println("Instance: " + instance.toString());
        System.out.println("Règles appliquées: " + matchedRules.size());
        System.out.println("Décision finale: " + finalDecision + " (Score: " + df.format(finalScore) + ")");
    }

    /**
     * Fonction utilitaire pour calculer l'entropie
     */
    private static double calculateEntropy(Collection<Double> probabilities) {
        double entropy = 0.0;
        double sum = probabilities.stream().mapToDouble(Double::doubleValue).sum();

        for (double prob : probabilities) {
            if (prob > 0) {
                double normalizedProb = prob / sum;
                entropy -= normalizedProb * Math.log(normalizedProb) / Math.log(2);
            }
        }
        return entropy;
    }
}
