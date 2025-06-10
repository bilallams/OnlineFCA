package com.lamsili.canc.fca.closure;

import com.lamsili.canc.fca.context.NominalContext;
import com.yahoo.labs.samoa.instances.Instance;

import java.util.*;

public class ClosureOperator {

    private final NominalContext context;

    /**
     * Constructor: we inject the nominal context containing the instances.
     */
    public ClosureOperator(NominalContext context) {
        this.context = context;
    }

    /** method 01
     * Operator δ: for a pair (attribute, value), returns
     * the set of indices of instances that have this pair.
     */
    public Set<Integer> delta(String attribute, String value) {
        return context.delta(attribute, value);
    }

    /**
     * Extracts attribute-value pairs from an instance
     *
     * @param instance The instance to extract pairs from
     * @return Map of attribute names to their values
     */
    private Map<String, String> extractAttributeValuePairs(Instance instance) {
        Map<String, String> pairs = new HashMap<>();

        for (int i = 0; i < instance.numAttributes(); i++) {
            // Ignore class attribute
            if (i == instance.classIndex()) {
                continue;
            }

            // Check if the attribute is nominal
            if (instance.attribute(i).isNominal()) {
                String attrName = instance.attribute(i).name();
                // Get the attribute value as a string
                String val;
                if (instance.isMissing(i)) {
                    val = "?";
                } else {
                    int valueIndex = (int) instance.value(i);
                    val = instance.attribute(i).value(valueIndex);
                }
                pairs.put(attrName, val);
            }
        }

        return pairs;
    }

    /** method 02
     * Operator φ: for a set of instance indices,
     * returns the (attribute, value) pairs common to all these instances.
     */
    public Set<Map.Entry<String, String>> phi(Set<Integer> instanceIndices) {
        if (instanceIndices == null || instanceIndices.isEmpty()) {
            return Collections.emptySet();
        }

        Iterator<Integer> it = instanceIndices.iterator();

        // Get the first instance to initialize the common pairs
        int firstIndex = it.next();
        Instance firstInst = context.getInstance(firstIndex);

        // Extract attribute-value pairs from the first instance
        Map<String, String> commonPairs = extractAttributeValuePairs(firstInst);

        // Process other instances to keep only common pairs
        while (it.hasNext()) {
            int currentIndex = it.next();
            Instance currentInst = context.getInstance(currentIndex);
            Map<String, String> currentAttrs = extractAttributeValuePairs(currentInst);

            // Remove from commonPairs the pairs absent or different in currentAttrs
            commonPairs.entrySet().removeIf(e ->
                    !currentAttrs.containsKey(e.getKey()) ||
                            !currentAttrs.get(e.getKey()).equals(e.getValue())
            );
        }

        return commonPairs.entrySet();
    }

    /** method 04
     * Calcule le score de pertinence d'une valeur S(v) basé sur sa fréquence et la pureté de classe
     *
     * @param attribute Nom de l'attribut
     * @param value Valeur de l'attribut
     * @return Score de pertinence de cette valeur (plus élevé = plus pertinent)
     */
    public double calculateRelevanceScore(String attribute, String value) {
        // Find the instances with this attribute-value pair
        Set<Integer> instances = new HashSet<>();
        int matchCount = 0;

        // Map pour stocker la distribution des classes pour cette valeur (pour le débogage)
        Map<String, Integer> classDistribution = new HashMap<>();

        for (int i = 0; i < context.getNumInstances(); i++) {
            Instance instance = context.getInstance(i);
            for (int j = 0; j < instance.numAttributes(); j++) {
                if (instance.attribute(j).name().equals(attribute) &&
                        instance.attribute(j).value((int) instance.value(j)).equals(value)) {
                    matchCount++;

                    // Pour le débogage: compter la distribution des classes
                    int classIndex = instance.classIndex();
                    String className = instance.attribute(classIndex).value((int) instance.value(classIndex));
                    classDistribution.put(className, classDistribution.getOrDefault(className, 0) + 1);

                    instances.add(i);
                    break;
                }
            }
        }

        if (matchCount == 0) {
            return 0.0;
        }

        // Trouver la classe majoritaire parmi ces instances
        Map<String, Integer> classCount = new HashMap<>();
        int totalInstances = 0;

        for (Integer idx : instances) {
            Instance instance = context.getInstance(idx);
            int classIdx = instance.classIndex();
            if (classIdx >= 0) {
                String className = instance.attribute(classIdx).value((int) instance.value(classIdx));
                classCount.put(className, classCount.getOrDefault(className, 0) + 1);
                totalInstances++;
            }
        }

        // Trouver la classe majoritaire
        int maxCount = 0;
        for (int count : classCount.values()) {
            maxCount = Math.max(maxCount, count);
        }

        if (totalInstances == 0) {
            return 0.0;
        }

        // Débogage détaillé: affichage des calculs intermédiaires
        double frequency = (double) matchCount / context.getNumInstances();
        double purity = (double) maxCount / totalInstances;
        double score = frequency * purity;

        // Créer un map avec les informations de débogage pour être plus flexible
        Map<String, Object> debugInfo = new HashMap<>();
        debugInfo.put("instanceCount", matchCount);
        debugInfo.put("totalInstances", context.getNumInstances());
        debugInfo.put("frequency", frequency);
        debugInfo.put("classDistribution", classDistribution);
        debugInfo.put("majorityClassCount", maxCount);
        debugInfo.put("purity", purity);
        debugInfo.put("score", score);

        // Utiliser la méthode correcte de CANCDebugger
        com.lamsili.canc.app.CANCDebugger.printAttributeValueDebug(attribute + "=" + value, debugInfo);

        // Le score est un produit de la fréquence et de la pureté
        return score;
    }

    /** method 05
     * Finds the most relevant value for a given attribute
     *
     * @param attribute The attribute name
     * @return The most relevant value for this attribute
     */
    public String getMostRelevantValue(String attribute) {
        String mostRelevantValue = null;
        double bestScore = -1.0;

        // Go through all possible values for this attribute
        Map<String, Set<Integer>> valueMap = new HashMap<>();

        // Collect all values for this attribute
        for (int i = 0; i < context.getNumInstances(); i++) {
            Instance instance = context.getInstance(i);
            for (int j = 0; j < instance.numAttributes(); j++) {
                if (instance.attribute(j).name().equals(attribute) && instance.attribute(j).isNominal()) {
                    String value = instance.attribute(j).value((int) instance.value(j));
                    valueMap.computeIfAbsent(value, _ -> new HashSet<>()).add(i);
                }
            }
        }

        // Pour stocker les scores pour le débogage
        Map<String, Double> valueScores = new HashMap<>();

        // Calculate relevance score for each value
        for (String value : valueMap.keySet()) {
            double score = calculateRelevanceScore(attribute, value); // Utiliser seulement 2 arguments
            valueScores.put(value, score);
            if (score > bestScore) {
                bestScore = score;
                mostRelevantValue = value;
            }
        }

        // Débogage: Afficher uniquement les scores finaux des valeurs
        com.lamsili.canc.app.CANCDebugger.printRelevantValueCalculation(attribute, valueScores, mostRelevantValue);

        return mostRelevantValue;
    }

    /** method 06
     * Finds the attribute with the maximum information gain using
     * MOA/SAMOA implementations
     *
     * @return The name of the most informative attribute
     */
    public String getMostInformativeAttribute() {
        String bestAttribute = null;
        double bestInfoGain = -1.0;

        // Vérifier qu'il y a au moins une instance
        int numInstances = context.getNumInstances();
        if (numInstances == 0) {
            return null;
        }

        // Get the number of attributes from the first instance
        Instance firstInstance = context.getInstance(0);

        // Evaluate each attribute
        for (int attIndex = 0; attIndex < firstInstance.numAttributes(); attIndex++) {
            // Ignore the class attribute or non-nominal attributes
            if (attIndex == firstInstance.classIndex() || !firstInstance.attribute(attIndex).isNominal()) {
                continue;
            }

            // Calculate information gain for this attribute
            double infoGain = calculateAttributeInformationGain(attIndex);
            if (infoGain > bestInfoGain) {
                bestInfoGain = infoGain;
                bestAttribute = firstInstance.attribute(attIndex).name();
            }
        }

        return bestAttribute;
    }

    /** method 07
     * Calculates the information gain for a given attribute using MOA
     *
     * @param attributeIndex The attribute index
     * @return The information gain for this attribute
     */
    private double calculateAttributeInformationGain(int attributeIndex) {
        int numInstances = context.getNumInstances();

        // Get class indices for all instances
        double[] classDistribution = new double[10]; // Assuming max 10 classes
        double totalWeight = 0.0;

        // Calculate class distribution for the complete set
        for (int i = 0; i < numInstances; i++) {
            Instance instance = context.getInstance(i);
            int classValue = (int) instance.classValue();
            if (classValue >= classDistribution.length) {
                // Increase array size if needed
                double[] newDist = new double[classValue + 1];
                System.arraycopy(classDistribution, 0, newDist, 0, classDistribution.length);
                classDistribution = newDist;
            }
            classDistribution[classValue] += instance.weight();
            totalWeight += instance.weight();
        }

        // Calculate entropy before splitting
        double entropyBefore = calculateEntropy(classDistribution, totalWeight);

        // Calculate entropy after splitting by attribute
        Map<Double, double[]> splitDistribution = new HashMap<>();
        Map<Double, Double> splitWeights = new HashMap<>();

        for (int i = 0; i < numInstances; i++) {
            Instance instance = context.getInstance(i);
            double attributeValue = instance.value(attributeIndex);
            int classValue = (int) instance.classValue();

            if (!splitDistribution.containsKey(attributeValue)) {
                splitDistribution.put(attributeValue, new double[Math.max(10, classValue + 1)]);
                splitWeights.put(attributeValue, 0.0);
            }

            double[] dist = splitDistribution.get(attributeValue);
            if (classValue >= dist.length) {
                double[] newDist = new double[classValue + 1];
                System.arraycopy(dist, 0, newDist, 0, dist.length);
                dist = newDist;
                splitDistribution.put(attributeValue, dist);
            }

            dist[classValue] += instance.weight();
            splitWeights.put(attributeValue, splitWeights.get(attributeValue) + instance.weight());
        }

        // Calculate weighted entropy after splitting
        double entropyAfter = 0.0;
        for (Map.Entry<Double, double[]> entry : splitDistribution.entrySet()) {
            double splitWeight = splitWeights.get(entry.getKey());
            double[] splitDist = entry.getValue();
            entropyAfter += splitWeight / totalWeight * calculateEntropy(splitDist, splitWeight);
        }

        // Information gain is the reduction in entropy
        return entropyBefore - entropyAfter;
    }

    /** method 08
     * Calculation of the entropy of a distribution
     *
     * @param distribution Distribution of values
     * @param sum Total sum of values in the distribution
     * @return The entropy of the distribution
     */
    private double calculateEntropy(double[] distribution, double sum) {
        if (sum <= 0) {
            return 0.0;
        }

        double entropy = 0.0;
        for (double d : distribution) {
            if (d > 0) {
                double p = d / sum;
                entropy -= p * Math.log(p) / Math.log(2);
            }
        }

        return entropy;
    }
}
