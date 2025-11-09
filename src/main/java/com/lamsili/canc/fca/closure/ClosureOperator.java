package com.lamsili.canc.fca.closure;

import com.lamsili.canc.fca.context.NominalContext;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;
import com.yahoo.labs.samoa.instances.Attribute;

import java.util.*;
import java.io.Serializable;

// Import des classes Weka pour les calculs d'information et l'analyse d'attributs
import weka.core.ContingencyTables;
import weka.core.Utils;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.GainRatioAttributeEval;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.unsupervised.attribute.Remove;
import com.yahoo.labs.samoa.instances.SamoaToWekaInstanceConverter;

public class ClosureOperator implements Serializable {
    // Ajouter un serialVersionUID pour la stabilité de la sérialisation
    private static final long serialVersionUID = 1L;

    private final NominalContext context;
    // Convertisseur pour utiliser les fonctions de Weka si nécessaire
    private final SamoaToWekaInstanceConverter converter;
    // Cache pour l'en-tête Weka construit à partir du contexte MOA
    private transient weka.core.Instances wekaHeader = null;

    /**
     * Constructor: we inject the nominal context containing the instances.
     */
    public ClosureOperator(NominalContext context) {
        this.context = context;
        this.converter = new SamoaToWekaInstanceConverter();
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
     * Calcule le score de pertinence d'une valeur en se basant sur l'entropie
     * H(Sv) = -∑(i=1 to k) pi * log2(pi)
     * Plus l'entropie est faible, plus la valeur est pertinente
     *
     * @param attribute Nom de l'attribut
     * @param value Valeur de l'attribut
     * @return Score de pertinence de cette valeur (plus faible = plus pertinent)
     */
    public double calculateRelevanceScore(String attribute, String value) {
        // Find the instances with this attribute-value pair
        Set<Integer> instances = delta(attribute, value);
        int matchCount = instances.size();

        if (matchCount == 0) {
            return Double.MAX_VALUE; // Valeur non pertinente (entropie maximale)
        }

        // Map pour stocker la distribution des classes pour cette valeur (pour le débogage)
        Map<String, Integer> classDistribution = new HashMap<>();

        // Calculer la distribution des classes pour ces instances
        for (Integer idx : instances) {
            Instance instance = context.getInstance(idx);
            int classIndex = instance.classIndex();
            if (classIndex >= 0) {
                String className = instance.attribute(classIndex).value((int) instance.value(classIndex));
                classDistribution.put(className, classDistribution.getOrDefault(className, 0) + 1);
            }
        }

        // Si aucune classe n'est trouvée, retourner entropie maximale
        if (classDistribution.isEmpty()) {
            return Double.MAX_VALUE;
        }

        // Convertir la distribution en tableau pour le calcul d'entropie
        double[] distribution = new double[classDistribution.size()];
        int i = 0;
        double sum = 0.0;
        for (Integer count : classDistribution.values()) {
            distribution[i++] = count;
            sum += count;
        }

        // Calculer l'entropie
        double entropy = calculateEntropy(distribution, sum);

        // Créer un map avec les informations de débogage pour être plus flexible
        Map<String, Object> debugInfo = new HashMap<>();
        debugInfo.put("instanceCount", matchCount);
        debugInfo.put("totalInstances", context.getNumInstances());
        debugInfo.put("frequency", (double) matchCount / context.getNumInstances());
        debugInfo.put("classDistribution", classDistribution);
        debugInfo.put("entropy", entropy);

        // Utiliser la méthode correcte de CANCDebugger
        com.lamsili.canc.app.CANCDebugger.printAttributeEvalDebug(attribute + "=" + value, debugInfo);

        // Retourner l'entropie (plus c'est bas, plus c'est pertinent)
        return entropy;
    }

    /** method 05
     * Finds the most relevant value for a given attribute based on the selected evaluation method
     * (entropy or support)
     *
     * @param attribute The attribute name
     * @return The most relevant value according to the selected method
     */
    public String getMostRelevantValue(String attribute) {
        String mostRelevantValue = null;
        double bestScore = valueEvalMethod == ValueEvalMethod.SUPPORT ?
                          Double.MIN_VALUE :    // Pour SUPPORT, on cherche le maximum (plus c'est grand, plus c'est pertinent)
                          Double.MAX_VALUE;     // Pour ENTROPY, on cherche le minimum (plus c'est petit, plus c'est pertinent)

        // Go through all possible values for this attribute
        Map<String, Set<Integer>> valueMap = new HashMap<>();

        // Collect all values for this attribute
        for (int i = 0; i < context.getNumInstances(); i++) {
            Instance instance = context.getInstance(i);
            for (int j = 0; j < instance.numAttributes(); j++) {
                if (instance.attribute(j).name().equals(attribute) && instance.attribute(j).isNominal()) {
                    String value = instance.attribute(j).value((int) instance.value(j));
                    valueMap.computeIfAbsent(value, val -> new HashSet<>()).add(i);
                }
            }
        }

        // Pour stocker les scores pour le débogage
        Map<String, Double> valueScores = new HashMap<>();

        // Évaluer chaque valeur selon la méthode sélectionnée
        for (String value : valueMap.keySet()) {
            double score;

            // Utiliser la méthode d'évaluation choisie
            switch (valueEvalMethod) {
                case SUPPORT:
                    score = calculateSupportScore(attribute, value);
                    break;
                case ENTROPY:
                default:
                    score = calculateRelevanceScore(attribute, value);
                    break;
            }

            valueScores.put(value, score);

            // Mise à jour de la valeur avec le meilleur score
            if (valueEvalMethod == ValueEvalMethod.SUPPORT) {
                // Pour SUPPORT: on cherche le maximum
                if (score > bestScore || (score == bestScore && mostRelevantValue != null)) {
                    bestScore = score;
                    mostRelevantValue = value;
                }
            } else {
                // Pour ENTROPY: on cherche le minimum
                if (score < bestScore) {
                    bestScore = score;
                    mostRelevantValue = value;
                }
            }
        }

        // Débogage: Afficher les scores finaux des valeurs
        String methodName = (valueEvalMethod == ValueEvalMethod.ENTROPY) ? "Entropie" : "Support";
        String scoreType = (valueEvalMethod == ValueEvalMethod.ENTROPY) ? "entropie" : "support";

        // Un seul affichage via CANCDebugger (évite les doublons)
        com.lamsili.canc.app.CANCDebugger.printRelevantValueCalculation(
                attribute + " (méthode: " + methodName + ")",
                valueScores,
                mostRelevantValue);


        return mostRelevantValue;
    }

    /**
     * Type d'évaluation d'attribut à utiliser
     */
    public enum AttributeEvalMethod {
        INFORMATION_GAIN,
        GAIN_RATIO
    }

    /**
     * Type d'évaluation de valeur à utiliser
     */
    public enum ValueEvalMethod {
        ENTROPY,
        SUPPORT
    }

    // Méthode d'évaluation de valeur par défaut
    private ValueEvalMethod valueEvalMethod = ValueEvalMethod.SUPPORT;

    /**
     * Définir la méthode d'évaluation de valeur à utiliser
     * @param method La méthode d'évaluation (ENTROPY ou SUPPORT)
     */
    public void setValueEvalMethod(ValueEvalMethod method) {
        this.valueEvalMethod = method;
    }

    /**
     * Obtenir la méthode d'évaluation de valeur actuelle
     * @return La méthode d'évaluation de valeur en cours d'utilisation
     */
    public ValueEvalMethod getValueEvalMethod() {
        return this.valueEvalMethod;
    }


    /** method 06
     * Finds the most informative attribute using the specified evaluation method
     *
     * @param evalMethod La méthode d'évaluation à utiliser
     * @return The name of the most informative attribute
     */
    public String getMostInformativeAttribute(AttributeEvalMethod evalMethod) {
        String bestAttribute = null;
        double bestScore = -1.0;

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

            // Calculate score based on selected evaluation method
            double score;
            switch (evalMethod) {
                case GAIN_RATIO:
                    score = calculateGainRatio(attIndex);
                    break;
                case INFORMATION_GAIN:
                default:
                    score = calculateAttributeInfoGain(attIndex);
                    break;
            }

            if (score > bestScore) {
                bestScore = score;
                bestAttribute = firstInstance.attribute(attIndex).name();
            }
        }

        return bestAttribute;
    }

    /**
     * Calcule le gain d'information pour un attribut donné en utilisant la formule:
     * IG(Class,Attribute) = H(Class) - H(Class|Attribute)
     *
     * @param attributeIndex L'index de l'attribut
     * @return Le gain d'information pour cet attribut
     */
    public double calculateAttributeInfoGain(int attributeIndex) {
        try {
            // Vérifier que l'index d'attribut est valide
            if (attributeIndex < 0 || context.getNumInstances() == 0) {
                return 0.0;
            }

            // Obtenir l'instance MOA de référence
            Instance moaReference = context.getInstance(0);
            if (attributeIndex >= moaReference.numAttributes() || attributeIndex == moaReference.classIndex()) {
                return 0.0;
            }

            // Convertir toutes les instances MOA en instances Weka
            weka.core.Instances wekaInstances = convertMOAtoWekaInstances();

            if (wekaInstances == null) {
                System.err.println("Erreur: Impossible de créer les instances Weka");
                return 0.0;
            }

            // Vérifier si l'index de classe est défini
            if (wekaInstances.classIndex() < 0) {
                // Si aucun index de classe n'est défini dans MOA, utiliser le dernier attribut
                wekaInstances.setClassIndex(wekaInstances.numAttributes() - 1);
            }

            // Calculer directement le gain d'information selon la formule:
            // IG(Class,Attribute) = H(Class) - H(Class|Attribute)

            // 1. Calculer H(Class) - l'entropie de la classe
            double[] classDistribution = new double[wekaInstances.classAttribute().numValues()];
            for (int i = 0; i < wekaInstances.numInstances(); i++) {
                int classValue = (int) wekaInstances.instance(i).classValue();
                classDistribution[classValue]++;
            }
            double classEntropy = ContingencyTables.entropy(classDistribution);

            // 2. Calculer H(Class|Attribute) - l'entropie conditionnelle
            weka.core.Attribute attribute = wekaInstances.attribute(attributeIndex);
            double[][] contingencyTable = new double[attribute.numValues()][wekaInstances.classAttribute().numValues()];

            // Remplir la table de contingence
            for (int i = 0; i < wekaInstances.numInstances(); i++) {
                weka.core.Instance instance = wekaInstances.instance(i);
                int attValue = (int) instance.value(attributeIndex);
                int classValue = (int) instance.classValue();
                contingencyTable[attValue][classValue]++;
            }

            // Calculer l'entropie conditionnelle
            double conditionalEntropy = 0.0;
            double instanceCount = wekaInstances.numInstances();

            for (int i = 0; i < attribute.numValues(); i++) {
                double[] subsetDist = contingencyTable[i];
                double subsetSum = Utils.sum(subsetDist);

                if (subsetSum > 0) {
                    // Calculer l'entropie de cette distribution avec ContingencyTables.entropy()
                    // qui gère correctement le calcul d'entropie
                    double subsetEntropy = ContingencyTables.entropy(subsetDist);
                    conditionalEntropy += (subsetSum / instanceCount) * subsetEntropy;
                }
            }

            // 3. Calculer le gain d'information
            double informationGain = classEntropy - conditionalEntropy;

            // Collecte des informations pour le debug
            Map<String, Object> debugInfo = new HashMap<>();
            debugInfo.put("attributeName", moaReference.attribute(attributeIndex).name());
            debugInfo.put("attributeIndex", attributeIndex);
            debugInfo.put("numWekaInstances", wekaInstances.numInstances());
            debugInfo.put("wekaClassIndex", wekaInstances.classIndex());
            debugInfo.put("classEntropy", classEntropy);
            debugInfo.put("conditionalEntropy", conditionalEntropy);
            debugInfo.put("infoGain", informationGain);

            // Désactiver complètement tout affichage direct depuis cette méthode
            // Pour éviter complètement les doublons, nous laissons l'affichage à CANCLearnerMOA

            return informationGain;
        } catch (Exception e) {
            System.err.println("Error calculating information gain: " + e.getMessage());
            e.printStackTrace();
            return 0.0;
        }
    }

    /**
     * Calculates the gain ratio for a given attribute using Weka's GainRatioAttributeEval
     *
     * @param attributeIndex The attribute index
     * @return The gain ratio for this attribute
     */
    public double calculateGainRatio(int attributeIndex) {
        try {
            // Vérifier que l'index d'attribut est valide
            if (attributeIndex < 0 || context.getNumInstances() == 0) {
                return 0.0;
            }

            // Obtenir l'instance MOA de référence
            Instance moaReference = context.getInstance(0);
            if (attributeIndex >= moaReference.numAttributes() || attributeIndex == moaReference.classIndex()) {
                return 0.0;
            }

            // Convertir toutes les instances MOA en instances Weka avec le même en-tête
            weka.core.Instances wekaInstances = convertMOAtoWekaInstances();

            if (wekaInstances == null) {
                System.err.println("Erreur: Impossible de créer les instances Weka");
                return 0.0;
            }

            // Vérifier si l'index de classe est défini
            if (wekaInstances.classIndex() < 0) {
                // Si aucun index de classe n'est défini dans MOA, utiliser le dernier attribut
                wekaInstances.setClassIndex(wekaInstances.numAttributes() - 1);
            }

            // Utiliser directement l'évaluateur de gain ratio de Weka
            GainRatioAttributeEval evaluator = new GainRatioAttributeEval();
            evaluator.buildEvaluator(wekaInstances);
            double gainRatio = evaluator.evaluateAttribute(attributeIndex);

            // Afficher quelques informations de débogage
            Map<String, Object> debugInfo = new HashMap<>();
            debugInfo.put("attributeName", moaReference.attribute(attributeIndex).name());
            debugInfo.put("attributeIndex", attributeIndex);
            debugInfo.put("numWekaInstances", wekaInstances.numInstances());
            debugInfo.put("wekaClassIndex", wekaInstances.classIndex());
            debugInfo.put("gainRatio", gainRatio);

            // Afficher les informations de débogage détaillées via le debugger
            com.lamsili.canc.app.CANCDebugger.printAttributeEvalDebug("GainRatio", debugInfo);

            return gainRatio;

        } catch (Exception e) {
            System.err.println("Error calculating gain ratio: " + e.getMessage());
            e.printStackTrace();
            return 0.0;
        }
    }

    /** method 08
     * Calculation of the entropy of a distribution using Weka's ContingencyTables.entropy method
     * which implements the standard entropy formula H(X) = -sum(p(x) * log2(p(x)))
     *
     * @param distribution Distribution of values
     * @param sum Total sum of values in the distribution (if 0, will be calculated)
     * @return The entropy of the distribution
     */
    public static double calculateEntropy(double[] distribution, double sum) {
        if (distribution == null || distribution.length == 0) {
            return 0.0;
        }

        // Si aucun élément n'est positif, l'entropie est 0
        boolean hasPositive = false;
        for (double value : distribution) {
            if (value > 0) {
                hasPositive = true;
                break;
            }
        }

        if (!hasPositive) {
            return 0.0;
        }

        // Utiliser directement la méthode de Weka pour plus de cohérence
        return ContingencyTables.entropy(distribution);
    }

    /**
     * Crée un en-tête Weka complet à partir du contexte MOA
     * incluant toutes les valeurs nominales possibles
     *
     * @return L'en-tête Weka avec tous les attributs et valeurs nominales
     */
    private weka.core.Instances createWekaHeaderFromMOAContext() {
        if (context.getNumInstances() == 0) {
            return null;
        }

        try {
            // Récupérer la première instance pour initialiser la structure
            Instance firstInstance = context.getInstance(0);
            int numAttributes = firstInstance.numAttributes();
            int classIndex = firstInstance.classIndex();

            // Créer les attributs Weka avec toutes les valeurs nominales collectées
            ArrayList<weka.core.Attribute> wekaAttributes = new ArrayList<>();

            // Collecter toutes les valeurs possibles pour chaque attribut nominal
            Map<Integer, Set<String>> attributeValues = new HashMap<>();
            for (int i = 0; i < context.getNumInstances(); i++) {
                Instance instance = context.getInstance(i);
                for (int j = 0; j < numAttributes; j++) {
                    if (instance.attribute(j).isNominal()) {
                        String value = instance.attribute(j).value((int) instance.value(j));
                        attributeValues.computeIfAbsent(j, k -> new HashSet<>()).add(value);
                    }
                }
            }

            // Créer chaque attribut Weka
            for (int i = 0; i < numAttributes; i++) {
                com.yahoo.labs.samoa.instances.Attribute moaAttribute = firstInstance.attribute(i);
                String attrName = moaAttribute.name();

                if (moaAttribute.isNominal()) {
                    // Récupérer toutes les valeurs pour cet attribut nominal
                    Set<String> collectedValues = attributeValues.getOrDefault(i, new HashSet<>());

                    // S'assurer que toutes les valeurs du contexte original sont incluses
                    for (int j = 0; j < moaAttribute.numValues(); j++) {
                        collectedValues.add(moaAttribute.value(j));
                    }

                    // Créer l'attribut avec toutes les valeurs possibles
                    List<String> values = new ArrayList<>(collectedValues);
                    Collections.sort(values); // Pour garantir l'ordre constant

                    // Supprimer l'affichage des attributs nominaux
                    // System.out.println("Attribut nominal: " + attrName + ", valeurs: " + values);

                    wekaAttributes.add(new weka.core.Attribute(attrName, values));
                } else if (moaAttribute.isNumeric()) {
                    // Attribut numérique
                    wekaAttributes.add(new weka.core.Attribute(attrName));
                } else {
                    // Autres types (date, string, etc.)
                    wekaAttributes.add(new weka.core.Attribute(attrName, (List<String>)null));
                }
            }

            // Créer l'en-tête Weka
            weka.core.Instances header = new weka.core.Instances("MOAContext", wekaAttributes, 0);

            // Définir l'index de classe sans afficher de message
            if (classIndex >= 0 && classIndex < header.numAttributes()) {
                header.setClassIndex(classIndex);
                // L'affichage de l'index de classe a été supprimé car redondant
            }

            return header;
        } catch (Exception e) {
            System.err.println("Erreur lors de la création de l'en-tête Weka: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Convertit toutes les instances MOA en instances Weka en utilisant un en-tête commun
     *
     * @return Un ensemble d'instances Weka représentant le contexte MOA complet
     */
    private weka.core.Instances convertMOAtoWekaInstances() {
        try {
            // Créer ou récupérer l'en-tête Weka
            if (wekaHeader == null) {
                wekaHeader = createWekaHeaderFromMOAContext();
            }

            if (wekaHeader == null) {
                return null;
            }

            // Créer un nouvel ensemble d'instances avec l'en-tête
            weka.core.Instances wekaInstances = new weka.core.Instances(wekaHeader);

            // Ajouter chaque instance convertie
            for (int i = 0; i < context.getNumInstances(); i++) {
                Instance moaInstance = context.getInstance(i);
                weka.core.Instance wekaInstance = convertMOAInstanceToWeka(moaInstance, wekaHeader);
                wekaInstances.add(wekaInstance);
            }

            return wekaInstances;
        } catch (Exception e) {
            System.err.println("Erreur lors de la conversion des instances: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Convertit une instance MOA en instance Weka en utilisant l'en-tête fourni
     *
     * @param moaInstance L'instance MOA à convertir
     * @param header L'en-tête Weka à utiliser
     * @return L'instance Weka correspondante
     */
    private weka.core.Instance convertMOAInstanceToWeka(Instance moaInstance, weka.core.Instances header) {
        // Créer une nouvelle instance Weka avec les valeurs appropriées
        double[] values = new double[header.numAttributes()];

        for (int i = 0; i < moaInstance.numAttributes(); i++) {
            if (moaInstance.attribute(i).isNominal()) {
                // Pour les attributs nominaux, on doit convertir l'index de valeur MOA en index Weka
                String nominalValue = moaInstance.attribute(i).value((int)moaInstance.value(i));
                int wekaValueIndex = header.attribute(i).indexOfValue(nominalValue);

                // Vérifier que la valeur a été trouvée dans l'en-tête Weka
                if (wekaValueIndex == -1) {
                    System.err.println("ERREUR: Valeur nominale '" + nominalValue +
                                       "' non trouvée dans l'attribut Weka '" +
                                       header.attribute(i).name() + "'");

                    // Afficher les valeurs disponibles dans l'attribut Weka
                    System.err.println("Valeurs disponibles: ");
                    for (int j = 0; j < header.attribute(i).numValues(); j++) {
                        System.err.println("  - '" + header.attribute(i).value(j) + "'");
                    }

                    // Utiliser une valeur par défaut (la première)
                    values[i] = 0;
                } else {
                    // La valeur a été trouvée, utiliser son index
                    values[i] = wekaValueIndex;
                }
            } else if (moaInstance.isMissing(i)) {
                // Pour les valeurs manquantes
                values[i] = weka.core.Utils.missingValue();
            } else {
                // Pour les autres types d'attributs (numériques), on copie directement la valeur
                values[i] = moaInstance.value(i);
            }
        }

        weka.core.DenseInstance wekaInstance = new weka.core.DenseInstance(1.0, values);
        wekaInstance.setDataset(header);
        return wekaInstance;
    }

    /**
     * Calcule le score de pertinence d'une valeur en se basant sur le support (nombre d'occurrences)
     * Plus le support est élevé, plus la valeur est pertinente
     * Note: La méthode retourne directement le nombre d'occurrences (support)
     *
     * @param attribute Nom de l'attribut
     * @param value Valeur de l'attribut
     * @return Support (nombre d'occurrences) - plus élevé = plus pertinent
     */
    public double calculateSupportScore(String attribute, String value) {
        // Trouver les instances avec cette paire attribut-valeur
        Set<Integer> instances = delta(attribute, value);
        int matchCount = instances.size();

        if (matchCount == 0) {
            return 0; // Valeur non pertinente (aucune occurrence)
        }

        // Calculer le support (nombre d'occurrences)
        double support = matchCount;
        double supportRatio = (double) matchCount / context.getNumInstances();

        // Map pour stocker la distribution des classes pour cette valeur (pour le débogage)
        Map<String, Integer> classDistribution = new HashMap<>();

        // Calculer la distribution des classes pour ces instances
        for (Integer idx : instances) {
            Instance instance = context.getInstance(idx);
            int classIndex = instance.classIndex();
            if (classIndex >= 0) {
                String className = instance.attribute(classIndex).value((int) instance.value(classIndex));
                classDistribution.put(className, classDistribution.getOrDefault(className, 0) + 1);
            }
        }

        // Créer un map avec les informations de débogage
        Map<String, Object> debugInfo = new HashMap<>();
        debugInfo.put("instanceCount", matchCount);
        debugInfo.put("totalInstances", context.getNumInstances());
        debugInfo.put("frequency", supportRatio);
        debugInfo.put("nombreOccurrences", matchCount);
        debugInfo.put("support", String.format("%d (%.3f)", matchCount, supportRatio) + " [support = |δ("+attribute+"="+value+")| / |S| = " + matchCount + "/" + context.getNumInstances() + "]");
        debugInfo.put("classDistribution", classDistribution);

        // Utiliser la méthode correcte de CANCDebugger
        com.lamsili.canc.app.CANCDebugger.printAttributeEvalDebug(attribute + "=" + value + " (nombre d'occurrences: " + matchCount + ", support: " + String.format("%.3f", supportRatio) + ")", debugInfo);

        return support;
    }

    /**
     * Implémente la fermeture de Galois (γ = δ ∘ φ).
     * Cette opération applique successivement φ puis δ pour obtenir la fermeture d'un ensemble d'instances.
     *
     * Le résultat est le plus petit ensemble d'instances qui contient l'ensemble initial et
     * qui est fermé par rapport aux attributs communs.
     *
     * @param instanceIndices L'ensemble d'indices d'instances initial
     * @return L'ensemble fermé d'indices d'instances
     */
    public Set<Integer> galoisClosure(Set<Integer> instanceIndices) {
        // Calculer l'intention (les attributs-valeurs communs)
        Set<Map.Entry<String, String>> intent = phi(instanceIndices);

        // Si aucun attribut commun, retourner l'ensemble de toutes les instances
        if (intent.isEmpty()) {
            Set<Integer> allInstances = new HashSet<>();
            for (int i = 0; i < context.getNumInstances(); i++) {
                allInstances.add(i);
            }
            return allInstances;
        }

        // Calculer l'extension (les instances qui partagent ces attributs-valeurs)
        Set<Integer> closedExtent = null;

        for (Map.Entry<String, String> pair : intent) {
            // Pour chaque paire attribut-valeur, obtenir son extension
            Set<Integer> pairExtent = delta(pair.getKey(), pair.getValue());

            if (closedExtent == null) {
                closedExtent = new HashSet<>(pairExtent);
            } else {
                // Intersection des extensions
                closedExtent.retainAll(pairExtent);
            }
        }

        return closedExtent;
    }
}
