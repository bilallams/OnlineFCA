package com.lamsili.canc.fca.concept;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.io.Serializable;

/**
 * Represents a formal concept in Formal Concept Analysis (FCA).
 * A formal concept is defined by a pair (extent, intent) where
 * extent is a set of instance indices and intent is a set of
 * attribute-value pairs common to these instances.
 */
public class FormalConcept implements Serializable {
    // Ajouter un serialVersionUID pour la stabilité de la sérialisation
    private static final long serialVersionUID = 1L;

    private final Set<Integer> extent;
    private final Set<Map.Entry<String, String>> intent;

    /**
     * Constructor for a formal concept
     *
     * @param extent set of instance indices (I)
     * @param intent set of attribute-value pairs (A,V)
     */
    public FormalConcept(Set<Integer> extent, Set<Map.Entry<String, String>> intent) {
        this.extent = new HashSet<>(extent);
        this.intent = new HashSet<>(intent);
    }

    /**
     * Adds an instance index to the concept's extent.
     * This is used when incrementally updating concepts.
     *
     * @param instanceIndex The index of the instance to add
     * @return true if the extent was modified, false otherwise
     */
    public boolean addToExtent(int instanceIndex) {
        return this.extent.add(instanceIndex);
    }

    /**
     * Removes an instance index from the concept's extent.
     * This is used when removing instances that are no longer valid.
     *
     * @param instanceIndex The index of the instance to remove
     * @return true if the extent was modified, false otherwise
     */
    public boolean removeFromExtent(int instanceIndex) {
        return this.extent.remove(instanceIndex);
    }

    /**
     * @return The concept's extent (instance indices)
     */
    public Set<Integer> getExtent() {
        return new HashSet<>(extent);
    }

    /**
     * @return The concept's intent (attribute-value pairs)
     */
    public Set<Map.Entry<String, String>> getIntent() {
        return new HashSet<>(intent);
    }

    /**
     * @return Size of the extent (number of instances)
     */
    public int getExtentSize() {
        return extent.size();
    }

    /**
     * @return Size of the intent (number of attribute-value pairs)
     */
    public int getIntentSize() {
        return intent.size();
    }

    /**
     * Remappe les indices de l'extension en utilisant une table de correspondance (index échantillon -> index original).
     * @param sampleToOriginal liste où sampleToOriginal[i] = index original de l'instance i de l'échantillon
     */
    public void remapExtentIndices(java.util.List<Integer> sampleToOriginal) {
        if (sampleToOriginal == null || sampleToOriginal.isEmpty()) return;
        java.util.Set<Integer> newSet = new java.util.HashSet<>();
        for (Integer idx : new java.util.HashSet<>(extent)) { // snapshot
            if (idx != null && idx >= 0 && idx < sampleToOriginal.size()) {
                newSet.add(sampleToOriginal.get(idx));
            }
        }
        extent.clear();
        extent.addAll(newSet);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FormalConcept{extent=[");

        // Format the extent
        boolean first = true;
        for (Integer idx : extent) {
            if (!first) sb.append(", ");
            sb.append(idx);
            first = false;
        }
        sb.append("], intent=[");

        // Format the intent
        first = true;
        for (Map.Entry<String, String> pair : intent) {
            if (!first) sb.append(", ");
            sb.append("(").append(pair.getKey()).append(", ").append(pair.getValue()).append(")");
            first = false;
        }
        sb.append("]}");

        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FormalConcept concept = (FormalConcept) o;
        return Objects.equals(extent, concept.extent) &&
               Objects.equals(intent, concept.intent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(extent, intent);
    }
}
