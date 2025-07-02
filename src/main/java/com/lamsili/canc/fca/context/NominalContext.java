package com.lamsili.canc.fca.context;


import com.yahoo.labs.samoa.instances.Instance;  // Utilisation de l'Instance de MOA via SAMOA
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NominalContext {
    // List of instances added in order
    private final List<Instance> instances;

    // Delta index: attribute -> (value -> set of instance indices)
    private final Map<String, Map<String, Set<Integer>>> deltaIndex;

    // Paramètres de fenêtrage
    private int maxInstances = Integer.MAX_VALUE; // Par défaut, pas de limite
    private boolean useWindowing = false;

    public NominalContext() {
        this.instances = new ArrayList<>();
        this.deltaIndex = new HashMap<>();
    }

    /**
     * Construit un contexte nominal avec une taille maximale de fenêtre
     * @param maxSize La taille maximale de la fenêtre
     */
    public NominalContext(int maxSize) {
        this();
        if (maxSize > 0) {
            this.maxInstances = maxSize;
            this.useWindowing = true;
        }
    }

    /** method 01
     * Adds an instance to the nominal context and updates the index of (attr, val) pairs.
     * @param instance The instance to add
     */
    public void addInstance(Instance instance) {
        int idx = instances.size();
        instances.add(instance);

        // Update delta index for each nominal attribute
        for (int i = 0; i < instance.numAttributes(); i++) {
            if (i == instance.classIndex() || !instance.attribute(i).isNominal()) {
                continue;
            }

            String attrName = instance.attribute(i).name();
            String attrValue = instance.attribute(i).value((int) instance.value(i));

            // Update index for this attribute-value pair
            deltaIndex
                .computeIfAbsent(attrName, _ -> new HashMap<>())
                .computeIfAbsent(attrValue, _ -> new HashSet<>())
                .add(idx);
        }

        // Appliquer le fenêtrage si nécessaire
        if (useWindowing && instances.size() > maxInstances) {
            removeOldestInstance();
        }
    }

    /**
     * Supprime l'instance la plus ancienne du contexte et met à jour les index
     */
    private void removeOldestInstance() {
        if (instances.isEmpty()) return;

        // Retirer l'instance 0 qui est la plus ancienne
        Instance oldInstance = instances.remove(0);

        // Pour chaque attribut nominal, retirer la référence à l'instance 0 et décaler tous les autres indices
        for (int i = 0; i < oldInstance.numAttributes(); i++) {
            if (i == oldInstance.classIndex() || !oldInstance.attribute(i).isNominal()) {
                continue;
            }

            String attrName = oldInstance.attribute(i).name();
            String attrValue = oldInstance.attribute(i).value((int) oldInstance.value(i));

            Map<String, Set<Integer>> valueMap = deltaIndex.get(attrName);
            if (valueMap != null) {
                Set<Integer> instanceIndices = valueMap.get(attrValue);
                if (instanceIndices != null) {
                    // Optimisation : utiliser removeIf pour supprimer et transformer en une seule passe
                    Set<Integer> newIndices = new HashSet<>();
                    instanceIndices.removeIf(idx -> {
                        if (idx > 0) {
                            newIndices.add(idx - 1); // Décrémente les indices > 0
                        }
                        return true; // Supprime tous les anciens indices
                    });
                    instanceIndices.addAll(newIndices);

                    // Si plus aucune instance n'a cette valeur, nettoyer
                    if (instanceIndices.isEmpty()) {
                        valueMap.remove(attrValue);
                        if (valueMap.isEmpty()) {
                            deltaIndex.remove(attrName);
                        }
                    }
                }
            }
        }
    }

    /** method 02
     * Gets all instances that have a specific attribute-value pair.
     * @param attribute The attribute name
     * @param value The attribute value
     * @return Set of indices of instances with this attribute-value pair
     */
    public Set<Integer> delta(String attribute, String value) {
        Map<String, Set<Integer>> valueMap = deltaIndex.get(attribute);
        if (valueMap == null) {
            return Collections.emptySet();
        }

        Set<Integer> instanceIndices = valueMap.get(value);
        if (instanceIndices == null) {
            return Collections.emptySet();
        }

        return new HashSet<>(instanceIndices);
    }

    /** method 03
     * Gets the class value of an instance.
     * @param instanceIdx The index of the instance
     * @return The class value as a string
     */
    public String getInstanceClass(int instanceIdx) {
        if (instanceIdx >= instances.size()) {
            return null;
        }

        Instance instance = instances.get(instanceIdx);
        int classIdx = instance.classIndex();
        if (classIdx < 0) {
            return null;
        }

        return instance.attribute(classIdx).value((int) instance.value(classIdx));
    }

    /** method 04
     * Gets the number of instances in the context.
     * @return The number of instances
     */
    public int getNumInstances() {
        return instances.size();
    }

    /** method 05
     * Gets an instance by its index.
     * @param idx The instance index
     * @return The instance
     */
    public Instance getInstance(int idx) {
        if (idx < 0 || idx >= instances.size()) {
            throw new IndexOutOfBoundsException("Invalid instance index: " + idx);
        }
        return instances.get(idx);
    }

    /**
     * Efface toutes les instances et réinitialise les indices
     */
    public void clear() {
        instances.clear();
        deltaIndex.clear();
    }

    /**
     * Accéder à l'index delta utilisé pour le lookup rapide
     * @return Map des attributs, valeurs et instances correspondantes
     */
    public Map<String, Map<String, Set<Integer>>> getDeltaIndex() {
        return deltaIndex;
    }
}
