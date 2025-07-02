package com.lamsili.canc.varriants;

import com.lamsili.canc.fca.context.NominalContext;
import com.lamsili.canc.fca.closure.ClosureOperator;
import com.yahoo.labs.samoa.instances.Instance;

import java.util.*;

/**
 * Classe responsable de la sélection des couples attribut-valeur selon
 * la variante de l'algorithme choisie.
 */
public class NCACoupleSelector {
    private final NominalContext context;

    /**
     * Constructeur avec contexte nominal
     *
     * @param context Le contexte nominal utilisé pour la sélection
     */
    public NCACoupleSelector(NominalContext context) {
        this.context = context;
    }

    /**
     * Sélectionne les couples attribut-valeur à partir d'une instance, selon la variante choisie.
     *
     * @param instance L'instance à analyser
     * @param variant La variante de l'algorithme à utiliser
     * @return Ensemble des couples attribut-valeur sélectionnés
     */
    public Set<Map.Entry<String, String>> selectCouples(Instance instance, Variant variant) {
        switch (variant) {
            case CpNC_COMV:
                return cpncComvPairs(instance);
            case CpNC_CORV:
                return cpncCorvPair(instance);
            case CaNC_COMV:
                return cancComvPairs(instance);
            case CaNC_CORV:
                return cancCorvPairs(instance);
            default:
                throw new IllegalArgumentException("Variante non reconnue: " + variant);
        }
    }

    /**
     * Retourne les scores d'information de tous les attributs nominaux.
     * Utilisé pour le débogage et la visualisation des scores.
     *
     * @return Map associant chaque attribut à son score d'information
     */
    public Map<String, Double> getAttributeScores() {
        Map<String, Double> attributeScores = new HashMap<>();

        if (context.getNumInstances() == 0) {
            return attributeScores;
        }

        // Utiliser ClosureOperator pour calculer le vrai gain d'information (ou gain ratio si configuré)
        ClosureOperator closure = new ClosureOperator(context);

        // Récupérer tous les attributs nominaux
        Instance firstInstance = context.getInstance(0);

        // Pour chaque attribut nominal, calculer le gain d'information réel
        for (int i = 0; i < firstInstance.numAttributes(); i++) {
            if (i == firstInstance.classIndex() || !firstInstance.attribute(i).isNominal()) {
                continue;
            }

            String attributeName = firstInstance.attribute(i).name();

            // Utiliser la méthode d'évaluation configurée (IG ou GR)
            ClosureOperator.AttributeEvalMethod evalMethod =
                com.lamsili.canc.app.CANCDebugger.getAttributeEvalMethod();

            double score;
            if (evalMethod == ClosureOperator.AttributeEvalMethod.GAIN_RATIO) {
                score = closure.calculateGainRatio(i);
            } else {
                // Utiliser une méthode publique pour calculer le gain d'information
                score = closure.calculateAttributeInfoGain(i);
            }

            attributeScores.put(attributeName, score);
        }

        return attributeScores;
    }

    /**
     * Retourne le nom de l'attribut le plus pertinent selon le gain d'information.
     *
     * @return Nom de l'attribut le plus pertinent
     */
    public String getMostPertinentAttribute() {
        ClosureOperator closure = new ClosureOperator(context);
        return closure.getMostInformativeAttribute(com.lamsili.canc.app.CANCDebugger.getAttributeEvalMethod());
    }


    /**
     * Implémente la variante CpNC_COMV (Algorithm 1)
     * Classifier pertinent Nominal Concept basé sur Closure Operator pour multi-values.
     *
     * Pour l'attribut avec le meilleur gain d'information, on calcule la fermeture
     * pour chacune de ses valeurs pour construire des concepts nominaux.
     *
     * @param instance L'instance à analyser
     * @return Ensemble des couples attribut-valeur correspondant à l'intent des concepts
     */
    private Set<Map.Entry<String, String>> cpncComvPairs(Instance instance) {
        // Créer l'opérateur de fermeture
        ClosureOperator closure = new ClosureOperator(context);

        // 1. Trouver l'attribut avec le gain d'information maximal (a*)
        String mostInformativeAttribute = closure.getMostInformativeAttribute(com.lamsili.canc.app.CANCDebugger.getAttributeEvalMethod());

        // Ensemble de résultat qui contiendra les intents des concepts (couples attribut-valeur)
        Set<Map.Entry<String, String>> resultPairs = new HashSet<>();

        // 2. Pour chaque valeur possible v_p_l de l'attribut le plus informatif
        Map<String, Set<Integer>> valueToInstancesMap = new HashMap<>();

        // Collecte toutes les valeurs possibles pour cet attribut dans le contexte
        for (int i = 0; i < context.getNumInstances(); i++) {
            Instance inst = context.getInstance(i);
            for (int j = 0; j < inst.numAttributes(); j++) {
                if (inst.attribute(j).name().equals(mostInformativeAttribute) && inst.attribute(j).isNominal()) {
                    String value = inst.attribute(j).value((int) inst.value(j));
                    valueToInstancesMap.computeIfAbsent(value, k -> new HashSet<>()).add(i);
                }
            }
        }

        // Pour chaque valeur, calcule la fermeture et construit un concept nominal
        for (Map.Entry<String, Set<Integer>> entry : valueToInstancesMap.entrySet()) {
            String value = entry.getKey();

            // 2.1. Calcul de l'étendue (extent) du concept: δ(v_p_l)
            Set<Integer> extent = closure.delta(mostInformativeAttribute, value);

            // 2.2. Calcul de l'intent du concept: δ ∘ φ(v_p_l)
            Set<Map.Entry<String, String>> intent = closure.phi(extent);

            // L'intent constitue la condition de la règle
            resultPairs.addAll(intent);
        }

        return resultPairs;
    }

    /**
     * Implémente la variante CpNC_CORV (Algorithm 2)
     * Classifier pertinent Nominal Concept basé sur Closure Operator pour la valeur pertinente.
     *
     * Pour l'attribut le plus informatif, on identifie sa valeur la plus pertinente
     * et on calcule la fermeture pour construire un concept nominal.
     *
     * @param instance L'instance à analyser
     * @return Ensemble des couples attribut-valeur correspondant à l'intent du concept
     */
    private Set<Map.Entry<String, String>> cpncCorvPair(Instance instance) {
        // Créer l'opérateur de fermeture
        ClosureOperator closure = new ClosureOperator(context);

        // 1. Trouver l'attribut avec le gain d'information maximal (a*)
        String mostInformativeAttribute = closure.getMostInformativeAttribute(com.lamsili.canc.app.CANCDebugger.getAttributeEvalMethod());

        // 2. Trouver la valeur la plus pertinente (v*) pour cet attribut
        String mostRelevantValue = closure.getMostRelevantValue(mostInformativeAttribute);

        // 3. Calcule la fermeture pour cette valeur pertinente pour construire le concept nominal
        // 3.1. Calcul de l'étendue (extent) du concept: δ(v*_p_l)
        Set<Integer> extent = closure.delta(mostInformativeAttribute, mostRelevantValue);

        // 3.2. Calcul de l'intent du concept: δ ∘ φ(v*_p_l)
        Set<Map.Entry<String, String>> intent = closure.phi(extent);

        // L'intent constitue la condition de la règle
        return intent;
    }

    /**
     * Implémente la variante CaNC_COMV (Algorithm 3)
     * Classifier nominal basé sur Closure Operator pour multi-values de chaque attribut nominal.
     *
     * Pour chaque attribut et chaque valeur, on calcule la fermeture
     * pour construire des concepts nominaux.
     *
     * @param instance L'instance à analyser
     * @return Ensemble des couples attribut-valeur correspondant à l'intent des concepts
     */
    private Set<Map.Entry<String, String>> cancComvPairs(Instance instance) {
        // Créer l'opérateur de fermeture
        ClosureOperator closure = new ClosureOperator(context);

        // Ensemble de résultat qui contiendra les intents des concepts (couples attribut-valeur)
        Set<Map.Entry<String, String>> resultPairs = new HashSet<>();

        // 1. Pour chaque attribut nominal
        Set<String> nominalAttributes = new HashSet<>();

        // Récupère tous les attributs nominaux
        for (int i = 0; i < instance.numAttributes(); i++) {
            if (i == instance.classIndex() || !instance.attribute(i).isNominal()) {
                continue;
            }
            nominalAttributes.add(instance.attribute(i).name());
        }

        // 2. Pour chaque attribut nominal et chaque valeur possible
        for (String attribute : nominalAttributes) {
            // Collecte toutes les valeurs possibles pour cet attribut dans le contexte
            Map<String, Set<Integer>> valueToInstancesMap = new HashMap<>();

            for (int i = 0; i < context.getNumInstances(); i++) {
                Instance inst = context.getInstance(i);
                for (int j = 0; j < inst.numAttributes(); j++) {
                    if (inst.attribute(j).name().equals(attribute)) {
                        String value = inst.attribute(j).value((int) inst.value(j));
                        valueToInstancesMap.computeIfAbsent(value, k -> new HashSet<>()).add(i);
                    }
                }
            }

            // Pour chaque valeur, calcule la fermeture et construit un concept nominal
            for (Map.Entry<String, Set<Integer>> entry : valueToInstancesMap.entrySet()) {
                String value = entry.getKey();

                // 2.1. Calcul de l'étendue (extent) du concept: δ(v_p_l)
                Set<Integer> extent = closure.delta(attribute, value);

                // 2.2. Calcul de l'intent du concept: δ ∘ φ(v_p_l)
                Set<Map.Entry<String, String>> intent = closure.phi(extent);

                // L'intent constitue la condition de la règle
                resultPairs.addAll(intent);
            }
        }

        return resultPairs;
    }

    /**
     * Implémente la variante CaNC_CORV (Algorithm 4)
     * Classifier nominal basé sur Closure Operator pour une valeur pertinente de chaque attribut nominal.
     *
     * Pour chaque attribut nominal, on identifie sa valeur la plus pertinente
     * et on calcule la fermeture pour construire un concept nominal.
     *
     * @param instance L'instance à analyser
     * @return Ensemble des couples attribut-valeur correspondant à l'intent des concepts
     */
    private Set<Map.Entry<String, String>> cancCorvPairs(Instance instance) {
        // Créer l'opérateur de fermeture
        ClosureOperator closure = new ClosureOperator(context);

        // Ensemble de résultat qui contiendra les intents des concepts (couples attribut-valeur)
        Set<Map.Entry<String, String>> resultPairs = new HashSet<>();

        // 1. Pour chaque attribut nominal
        Set<String> nominalAttributes = new HashSet<>();

        // Récupère tous les attributs nominaux
        for (int i = 0; i < instance.numAttributes(); i++) {
            if (i == instance.classIndex() || !instance.attribute(i).isNominal()) {
                continue;
            }
            nominalAttributes.add(instance.attribute(i).name());
        }

        // 2. Pour chaque attribut nominal et sa valeur pertinente
        for (String attribute : nominalAttributes) {
            // Trouver la valeur la plus pertinente pour cet attribut
            String mostRelevantValue = closure.getMostRelevantValue(attribute);

            if (mostRelevantValue != null) {
                // Calcul de l'étendue (extent) du concept: δ(v*_p_l)
                Set<Integer> extent = closure.delta(attribute, mostRelevantValue);

                // Calcul de l'intent du concept: δ ∘ φ(v*_p_l)
                Set<Map.Entry<String, String>> intent = closure.phi(extent);

                // L'intent constitue la condition de la règle
                resultPairs.addAll(intent);
            }
        }

        return resultPairs;
    }
}
