package com.lamsili.canc.rules;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import com.yahoo.labs.samoa.instances.Instance;

/**
 * Represents a classification rule of the form "if conditions then class".
 * The conditions are attribute-value pairs that must be satisfied.
 */
public class Rule {
    private final Map<String, String> conditions;
    private final String predictedClass;
    private double weight;
    private int support;
    private double confidence; // Nouvelle propriété pour la confiance

    /**
     * Constructor for a simple rule
     *
     * @param conditions Map of rule conditions (attribute -> value)
     * @param predictedClass The class predicted by this rule
     */
    public Rule(Map<String, String> conditions, String predictedClass) {
        this.conditions = new HashMap<>(conditions);
        this.predictedClass = predictedClass;
        this.weight = 1.0;
        this.support = 0;
        this.confidence = 1.0; // Par défaut, confiance maximale
    }

    /**
     * Constructor with weight and support
     *
     * @param conditions Map of rule conditions (attribute -> value)
     * @param predictedClass The class predicted by this rule
     * @param weight The weight of the rule
     * @param support The support of the rule (number of instances)
     */
    public Rule(Map<String, String> conditions, String predictedClass, double weight, int support) {
        this(conditions, predictedClass, weight, support, 1.0);
    }

    /**
     * Constructor with weight, support and confidence
     *
     * @param conditions Map of rule conditions (attribute -> value)
     * @param predictedClass The class predicted by this rule
     * @param weight The weight of the rule
     * @param support The support of the rule (number of instances)
     * @param confidence The confidence of the rule (proportion of correct predictions)
     */
    public Rule(Map<String, String> conditions, String predictedClass, double weight, int support, double confidence) {
        this.conditions = new HashMap<>(conditions);
        this.predictedClass = predictedClass;
        this.weight = weight;
        this.support = support;
        this.confidence = confidence;
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
     * @return The support of the rule
     */
    public int getSupport() {
        return support;
    }

    /**
     * @return The confidence of the rule
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * Sets the confidence of the rule
     * @param confidence The new confidence value
     */
    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    /**
     * Sets the support of the rule
     * @param support The new support value
     */
    public void setSupport(int support) {
        this.support = support;
    }

    /**
     * Sets the weight of the rule
     * @param weight The new weight value
     */
    public void setWeight(double weight) {
        this.weight = weight;
    }

    /**
     * String representation of the rule
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("IF ");

        int i = 0;
        for (Map.Entry<String, String> entry : conditions.entrySet()) {
            if (i > 0) {
                sb.append(" AND ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            i++;
        }

        sb.append(" THEN ").append(predictedClass)
          .append(" (support=").append(support).append(", confidence=")
          .append(String.format("%.2f", confidence)).append(", weight=")
          .append(String.format("%.2f", weight)).append(")");

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
}
