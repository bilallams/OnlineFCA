package com.lamsili.canc.rules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.InstancesHeader;
import java.io.Serializable;

/**
 * Represents a classification rule of the form "if conditions then class".
 * The conditions are attribute-value pairs that must be satisfied.
 */
public class Rule implements Serializable {
    // Ajouter un serialVersionUID pour la stabilité de la sérialisation
    private static final long serialVersionUID = 1L;

    private final Map<String, String> conditions;
    private String predictedClass; // Suppression du mot-clé "final"
    private double weight;
    private int truePositiveCount; // |X,C|
    private int premiseOccurrence; // |X| nombre d'instances satisfaisant les conditions (prémisse)
    private double supportRule;    // support règle = |X,C|/N
    private double confidence;
    private static int totalInstances = 100; // Par défaut à 100, mais sera mis à jour

    // Liste statique pour stocker l'ordre des attributs tel qu'il apparaît dans le jeu de données
    private static List<String> attributeOrder = new ArrayList<>();

    /**
     * Constructor with weight and support
     *
     * @param conditions Map of rule conditions (attribute -> value)
     * @param predictedClass The class predicted by this rule
     * @param weight The weight of the rule
     * @param occurance The number of instances covered by the rule
     */
    public Rule(Map<String, String> conditions, String predictedClass, double weight, int occurance) {
        this.conditions = new HashMap<>(conditions);
        this.predictedClass = predictedClass;
        this.weight = weight;
        this.truePositiveCount = occurance; // sera recalculé plus tard
        this.premiseOccurrence = occurance; // initialement la taille de l'extent
        this.supportRule = (double) occurance / totalInstances; // support règle
        this.confidence = 1.0; // Par défaut, confiance maximale
    }

    /**
     * Checks if an instance satisfies the conditions of this rule
     *
     * @param instance The instance to check
     * @return true if the instance satisfies all conditions
     */
    public boolean appliesTo(Instance instance) {
        for (Map.Entry<String, String> condition : conditions.entrySet()) {
            String attribute = condition.getKey();
            String expectedValue = condition.getValue();

            // Find the attribute index
            int attrIndex = -1;
            for (int i = 0; i < instance.numAttributes(); i++) {
                if (instance.attribute(i).name().equals(attribute)) {
                    attrIndex = i;
                    break;
                }
            }

            // If the attribute is not found or the value doesn't match
            if (attrIndex == -1) {
                return false;
            }

            // Get the instance value for this attribute (assuming it's nominal)
            String actualValue = instance.attribute(attrIndex).value((int) instance.value(attrIndex));

            if (!actualValue.equals(expectedValue)) {
                return false;
            }
        }

        return true;
    }

    /**
     * @return The conditions of the rule
     */
    public Map<String, String> getConditions() {
        return new HashMap<>(conditions);
    }

    /**
     * @return The predicted class
     */
    public String getPredictedClass() {
        return predictedClass;
    }

    /**
     * @return The weight of the rule
     */
    public double getWeight() {
        return weight;
    }

    /**
     * Sets the weight of the rule
     *
     * @param weight The new weight
     */
    public void setWeight(double weight) {
        this.weight = weight;
    }

    // --- True Positive Count (ex-occurrence) ---
    public int getTruePositiveCount() { return truePositiveCount; }
    public void setTruePositiveCount(int tp) {
        this.truePositiveCount = tp;
        this.supportRule = (double) tp / totalInstances;
    }
    public void setTruePositiveCountWithoutRecalculation(int tp) { this.truePositiveCount = tp; }

    // --- Occurrence de la prémisse ---
    public int getPremiseOccurrence() { return premiseOccurrence; }
    public void setPremiseOccurrence(int premiseOccurrence) { this.premiseOccurrence = premiseOccurrence; }
    public void setPremiseOccurrenceWithoutRecalculation(int premiseOccurrence) { this.premiseOccurrence = premiseOccurrence; }

    // --- Support de la règle ---
    public double getSupportRule() { return supportRule; }

    public void setSupportRule(double supportRule) { this.supportRule = supportRule; }
    public void setSupportRuleWithoutRecalculation(double supportRule) { this.supportRule = supportRule; }

    // --- Confiance ---
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    /**
     * Définit le nombre total d'instances pour le calcul du support
     *
     * @param total Le nombre total d'instances
     */
    public static void setTotalInstances(int total) {
        totalInstances = total;
    }

    /**
     * Retourne le nombre total d'instances utilisé pour calculer le support
     *
     * @return Nombre total d'instances
     */
    public static int getTotalInstances() {
        return totalInstances;
    }

    /**
     * Définit l'ordre des attributs à partir de l'en-tête des instances
     * Cette méthode doit être appelée une fois lors de l'initialisation
     *
     * @param header L'en-tête des instances contenant les informations sur les attributs
     */
    public static void setAttributeOrder(InstancesHeader header) {
        attributeOrder.clear();
        // On parcourt tous les attributs sauf la classe (généralement le dernier)
        for (int i = 0; i < header.numAttributes(); i++) {
            if (i != header.classIndex()) {
                attributeOrder.add(header.attribute(i).name());
            }
        }
    }

    /**
     * Réinitialise l'ordre des attributs
     */
    public static void resetAttributeOrder() {
        attributeOrder.clear();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("IF ");

        // On va parcourir les attributs dans l'ordre défini dans le jeu de données
        boolean first = true;

        // Si l'ordre des attributs a été défini, l'utiliser
        if (!attributeOrder.isEmpty()) {
            // D'abord, afficher les attributs qui suivent l'ordre du jeu de données
            for (String attribute : attributeOrder) {
                if (conditions.containsKey(attribute)) {
                    if (!first) {
                        sb.append(" AND ");
                    }
                    sb.append(attribute).append(" = '").append(conditions.get(attribute)).append("'");
                    first = false;
                }
            }

            // Ensuite, afficher les attributs qui ne sont pas dans l'ordre prédéfini (si présents)
            for (Map.Entry<String, String> condition : conditions.entrySet()) {
                if (!attributeOrder.contains(condition.getKey())) {
                    if (!first) {
                        sb.append(" AND ");
                    }
                    sb.append(condition.getKey()).append(" = '").append(condition.getValue()).append("'");
                    first = false;
                }
            }
        } else {
            // Si aucun ordre n'est défini, afficher les conditions dans l'ordre alphabétique
            List<Map.Entry<String, String>> sortedConditions = new ArrayList<>(conditions.entrySet());
            sortedConditions.sort(Comparator.comparing(Map.Entry::getKey));

            for (Map.Entry<String, String> condition : sortedConditions) {
                if (!first) {
                    sb.append(" AND ");
                }
                sb.append(condition.getKey()).append(" = '").append(condition.getValue()).append("'");
                first = false;
            }
        }

        sb.append(" THEN class = '").append(predictedClass).append("'");
        sb.append(" [Occurence=").append(premiseOccurrence);
        sb.append(", TruePositve=").append(truePositiveCount); // vrais positifs
        sb.append(", supportRule=").append(String.format("%.3f", supportRule));
        sb.append(", confidence=").append(String.format("%.2f", confidence));
        sb.append(", weight=").append(String.format("%.3f", weight)).append("]");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Rule rule = (Rule) o;
        return Objects.equals(conditions, rule.conditions) &&
                Objects.equals(predictedClass, rule.predictedClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conditions, predictedClass);
    }

    /**
     * Sets the predicted class of the rule
     *
     * @param predictedClass The new predicted class
     */
    public void setPredictedClass(String predictedClass) {
        this.predictedClass = predictedClass;
    }
}
