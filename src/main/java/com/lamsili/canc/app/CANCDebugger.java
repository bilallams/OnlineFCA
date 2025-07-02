package com.lamsili.canc.app;

import com.lamsili.canc.fca.closure.ClosureOperator;
import com.lamsili.canc.fca.closure.ClosureOperator.AttributeEvalMethod;
import com.lamsili.canc.fca.concept.FormalConcept;
import com.lamsili.canc.rules.Rule;
import com.lamsili.canc.varriants.Variant;
import com.lamsili.canc.fca.context.NominalContext;
import com.yahoo.labs.samoa.instances.Instance;
import java.text.DecimalFormat;
import java.util.*;

/**
 * CANCDebugger - Version simplifiée pour le débogage de l'algorithme CANC
 */
public class CANCDebugger {

    private static boolean debugEnabled = true;
    private static final DecimalFormat df = new DecimalFormat("0.0000");
    // Format pour l'affichage du gain d'information avec 4 décimales
    private static final DecimalFormat dfGain = new DecimalFormat("0.0000");
    // Cache pour éviter l'affichage redondant des calculs de gain d'information
    private static final Set<String> displayedInfoGains = new HashSet<>();
    // Cache pour éviter l'affichage redondant des calculs de support de valeurs
    private static final Set<String> displayedValueSupports = new HashSet<>();
    // Pour réinitialiser l'affichage à chaque nouveau chunk ou nouvelle analyse
    private static int currentChunkId = 0;

    // Nouvelle variable pour suivre le mode actuel (chunks ou grace period)
    private static boolean isChunksMode = true;

    // Variable pour activer/désactiver la génération de règles disjointes
    private static boolean useDisjointRules = false;

    // Variable pour suivre si les détails de sélection ont déjà été affichés
    private static boolean selectionDetailsDisplayed = false;

    /**
     * Méthode d'évaluation des attributs à utiliser dans toute l'application
     * Changer cette variable pour basculer entre le gain d'information et le gain ratio
     */
    private static AttributeEvalMethod currentEvalMethod = AttributeEvalMethod.INFORMATION_GAIN;

    /**
     * Méthode d'évaluation des valeurs à utiliser dans toute l'application
     * Changer cette variable pour basculer entre l'entropie et le support
     */
    private static ClosureOperator.ValueEvalMethod currentValueEvalMethod = ClosureOperator.ValueEvalMethod.ENTROPY;

    /**
     * Active ou désactive la génération de règles disjointes
     * @param useDisjoint true pour activer, false pour désactiver
     */
    public static void setUseDisjointRules(boolean useDisjoint) {
        useDisjointRules = useDisjoint;
    }

    /**
     * Indique si la génération de règles disjointes est activée
     * @return true si activée, false sinon
     */
    public static boolean isUseDisjointRules() {
        return useDisjointRules;
    }

    /**
     * Retourne la méthode d'évaluation des attributs configurée
     */
    public static AttributeEvalMethod getAttributeEvalMethod() {
        return currentEvalMethod;
    }

    /**
     * Retourne la méthode d'évaluation des valeurs configurée
     */
    public static ClosureOperator.ValueEvalMethod getValueEvalMethod() {
        return currentValueEvalMethod;
    }

    /**
     * Configure la méthode d'évaluation des attributs
     * @param method Méthode à utiliser (INFORMATION_GAIN ou GAIN_RATIO)
     */
    public static void setAttributeEvalMethod(AttributeEvalMethod method) {
        currentEvalMethod = method;
        if (debugEnabled) {
            System.out.println("Méthode d'évaluation d'attribut changée à: " + method);
        }
    }

    /**
     * Configure la méthode d'évaluation des valeurs
     * @param method Méthode à utiliser (ENTROPY ou SUPPORT)
     */
    public static void setValueEvalMethod(ClosureOperator.ValueEvalMethod method) {
        currentValueEvalMethod = method;
        if (debugEnabled) {
            System.out.println("Méthode d'évaluation de valeur changée à: " + method);
        }
    }

    /**
     * Définit le mode de traitement (chunks ou grace period)
     */
    public static void setChunksMode(boolean chunksMode) {
        isChunksMode = chunksMode;
    }

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
     * Réinitialise le cache d'information pour un nouveau chunk ou une nouvelle analyse
     * Cela permet d'afficher à nouveau les informations pour chaque nouveau traitement
     */
    public static void resetDebugCache(int chunkId) {
        if (chunkId != currentChunkId) {
            displayedInfoGains.clear();
            displayedValueSupports.clear();
            currentChunkId = chunkId;
        }
    }

    /**
     * Réinitialise le flag pour permettre l'affichage des détails de sélection
     * Cela permet d'afficher à nouveau les informations de gain pour chaque chunk
     */
    public static void resetSelectionDetailsFlag() {
        selectionDetailsDisplayed = false;
    }

    /**
     * Vérifie si les détails de sélection ont déjà été affichés
     *
     * @return true si les détails ont été affichés, false sinon
     */
    public static boolean isSelectionDetailsDisplayed() {
        return selectionDetailsDisplayed;
    }

    /**
     * Définit si les détails de sélection ont été affichés
     *
     * @param displayed true si les détails ont été affichés, false sinon
     */
    public static void setSelectionDetailsDisplayed(boolean displayed) {
        selectionDetailsDisplayed = displayed;
    }

    /**
     * Vérifie si une information de gain pour un attribut donné a déjà été affichée
     */
    public static boolean hasDisplayedInfoGain(String attributeName, double value) {
        return !displayedInfoGains.add(attributeName + "_" + dfGain.format(value));
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
     * Affiche un message horodaté
     */
    public static void printTimestampedMessage(String message) {
        if (!debugEnabled) return;
        System.out.println("-----------[DEBUG]-----------");
        System.out.println(message);
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
     * Affiche les détails de débogage spécifiques à une valeur d'attribut
     */
    public static void printAttributeValueDebug(String attrValue, Map<String, Object> debugInfo) {
        // Désactivé pour toutes les variantes - ne rien afficher
    }

    /**
     * Affiche le calcul de la valeur pertinente pour un attribut
     */
    public static void printRelevantValueCalculation(String attribute, Map<String, Double> valueScores, String selectedValue) {
        if (!debugEnabled) return;

        // Afficher uniquement lorsqu'on utilise les variantes CORV
        String variantName = getCurrentVariant();
        if (!(variantName != null && (variantName.contains("CORV")))) return;

        // Créer une clé unique pour cet ensemble de supports d'attribut
        String cacheKey = attribute + "_" + selectedValue + "_" + String.join("_", valueScores.keySet());

        // Vérifier si on a déjà affiché cette information
        if (displayedValueSupports.contains(cacheKey)) {
            // Information déjà affichée, ne rien faire
            return;
        }

        // Ajouter au cache pour éviter la répétition
        displayedValueSupports.add(cacheKey);

        // Déterminer si on utilise Support ou Entropie pour l'affichage
        boolean isSupport = currentValueEvalMethod == ClosureOperator.ValueEvalMethod.SUPPORT;
        String metricName = isSupport ? "Support" : "Entropie";

        System.out.println("\n=== " + metricName + " des valeurs pour " + attribute + " (méthode: " +
                          (isSupport ? "Support" : "Entropie") + ") ===");

        // Trier les valeurs
        List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(valueScores.entrySet());
        if (isSupport) {
            // Pour le support, trier par valeur décroissante (plus élevée d'abord)
            sortedEntries.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));
        } else {
            // Pour l'entropie, trier par valeur croissante (plus basse d'abord)
            sortedEntries.sort(Map.Entry.comparingByValue());
        }

        for (Map.Entry<String, Double> entry : sortedEntries) {
            String value = entry.getKey();
            Double score = entry.getValue();
            String marker = value.equals(selectedValue) ? " <- SÉLECTIONNÉE" : "";
            System.out.println("  " + value + " : " + df.format(score) + marker);
        }

        System.out.println("Valeur sélectionnée: " + selectedValue + " (" +
            metricName.toLowerCase() + ": " + df.format(valueScores.get(selectedValue)) + ")");
    }

    /**
     * Récupère le nom de la variante en cours d'utilisation
     */
    private static String getCurrentVariant() {
        try {
            // Tenter de trouver la variante en cours depuis le thread principal
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stackTrace) {
                if (element.getClassName().contains("NCACoupleSelector") ||
                    element.getClassName().contains("CANCLearnerMOA")) {
                    if (element.getMethodName().contains("CORV")) {
                        return element.getMethodName();
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Affiche les détails d'une évaluation d'attribut (InfoGain ou GainRatio)
     */
    public static void printAttributeEvalDebug(String evalType, Map<String, Object> debugInfo) {
        // Ne rien afficher - cette méthode est désactivée
    }

    /**
     * Affiche un résumé des gains d'information pour tous les attributs
     */
    public static void printInfoGainSummary(Map<String, Double> attributeGains, String selectedAttr) {
        if (!debugEnabled) return;

        if (selectedAttr != null && !selectedAttr.isEmpty()) {
            Double gain = attributeGains.get(selectedAttr);
            if (gain != null && !hasDisplayedInfoGain("selected_" + selectedAttr, gain)) {
                System.out.println("Attribut sélectionné : " + selectedAttr +
                                  " (Gain d'information : " + dfGain.format(gain) + ")");
            }
        }
    }

    /**
     * Affiche les détails des valeurs pertinentes pour un attribut donné
     *
     * @param context Le contexte nominal contenant les données
     * @param closureOperator L'opérateur de fermeture pour calculer les scores de pertinence
     * @param attribute Attribut dont on veut afficher les valeurs
     * @param currentVariant La variante actuelle de l'algorithme
     * @param getRelevantValueCallback Fonction pour obtenir la valeur la plus pertinente
     */
    public static void printValueDetails(NominalContext context, ClosureOperator closureOperator,
                                        String attribute, Variant currentVariant,
                                        java.util.function.Function<String, String> getRelevantValueCallback) {
        if (!debugEnabled) return;

        // Obtenir les valeurs possibles pour cet attribut
        Map<String, Set<Integer>> valuesMap = context.getDeltaIndex().get(attribute);
        if (valuesMap == null || valuesMap.isEmpty()) return;

        // Calculer l'entropie pour chaque valeur
        Map<String, Double> valueEntropies = new HashMap<>();
        for (String value : valuesMap.keySet()) {
            double entropy = closureOperator.calculateRelevanceScore(attribute, value);
            valueEntropies.put(value, entropy);
        }

        // Préparer le texte pour l'affichage
        StringBuilder sb = new StringBuilder();
        sb.append("Valeurs pour l'attribut '").append(attribute).append("':\n");

        // Afficher l'entropie pour chaque valeur (plus basse = plus pertinente)
        for (Map.Entry<String, Double> entry : valueEntropies.entrySet()) {
            sb.append("Valeur '").append(entry.getKey()).append("' : ")
              .append(String.format("%.4f", entry.getValue())).append("\n");
        }

        // Si on est dans une variante qui utilise la valeur pertinente
        if (currentVariant == Variant.CpNC_CORV || currentVariant == Variant.CaNC_CORV) {
            String relevantValue = getRelevantValueCallback.apply(attribute);
            Double entropy = valueEntropies.get(relevantValue);
            if (relevantValue != null && entropy != null) {
                sb.append("Valeur pertinente sélectionnée pour ").append(attribute).append(" : ")
                  .append(relevantValue).append(" (Entropie : ").append(String.format("%.4f", entropy)).append(")");
            }
        }

        // Afficher le résultat
        printDebugSection("Détails des valeurs pour " + attribute, sb.toString());
    }

    /**
     * Affiche les informations de gain d'information pour les attributs et les valeurs
     * Cette méthode peut être appelée à n'importe quel moment pour afficher les statistiques
     * de gain d'information actuelles.
     *
     * @param learner Instance du CANCLearnerMOA contenant le contexte et les opérateurs
     */
    public static void printInformationGainDetails(com.lamsili.canc.classifier.CANCLearnerMOA learner) {
        if (!debugEnabled) {
            return;
        }

        // Vérifier que le learner n'est pas null
        if (learner == null) {
            System.out.println("Erreur: Impossible d'afficher les informations de gain, learner est null");
            return;
        }

        // Récupérer le contexte et vérifier qu'il contient des instances
        NominalContext context = learner.getNominalContext();
        if (context == null || context.getNumInstances() < 2) {
            System.out.println("Pas assez de données pour calculer le gain d'information");
            return;
        }

        // Récupérer les scores d'information pour tous les attributs
        Map<String, Double> attributeScores = learner.getAttributeScores();
        if (attributeScores == null || attributeScores.isEmpty()) {
            System.out.println("Pas de scores d'attributs disponibles");
            return;
        }

        // Afficher l'attribut sélectionné avec son score
        String pertinentAttribute = learner.getMostPertinentAttribute();

        // Utiliser printInfoGainSummary pour afficher le résumé
        printInfoGainSummary(attributeScores, pertinentAttribute);

        // Créer un ClosureOperator pour l'affichage des valeurs (on utilise celui du learner)
        ClosureOperator closureOperator = new ClosureOperator(context);

        // Récupérer la variante actuelle
        Variant currentVariant = null;
        try {
            // On doit utiliser la réflexion pour accéder au champ privé currentVariant
            java.lang.reflect.Field variantField = learner.getClass().getDeclaredField("currentVariant");
            variantField.setAccessible(true);
            currentVariant = (Variant) variantField.get(learner);
        } catch (Exception e) {
            System.out.println("Erreur lors de l'accès à la variante actuelle: " + e.getMessage());
            return;
        }

        // Afficher les entropies des valeurs selon la variante
        if (currentVariant == Variant.CpNC_CORV) {
            printValueDetails(context, closureOperator,
                pertinentAttribute, currentVariant, learner::getRelevantValue);
        }
        // Si nous sommes dans une variante qui utilise tous les attributs
        else if (currentVariant == Variant.CaNC_CORV) {
            for (String attribute : attributeScores.keySet()) {
                printValueDetails(context, closureOperator,
                    attribute, currentVariant, learner::getRelevantValue);
            }
        }
        // Pour les autres variantes, afficher les valeurs de l'attribut pertinent
        else if (pertinentAttribute != null) {
            printValueDetails(context, closureOperator,
                pertinentAttribute, currentVariant, learner::getRelevantValue);
        }
    }

    /**
     * Affiche les attributs avec toutes leurs valeurs
     *
     * @param learner Instance du CANCLearnerMOA contenant le contexte et la variante actuelle
     */
    public static void printAttributesWithAllValuesViaCancDebugger(com.lamsili.canc.classifier.CANCLearnerMOA learner) {
        if (!debugEnabled) return;

        if (learner == null) {
            System.out.println("Erreur: Impossible d'afficher les attributs et valeurs, learner est null");
            return;
        }

        // Récupérer la variante actuelle
        Variant currentVariant = null;
        try {
            // On doit utiliser la réflexion pour accéder au champ privé currentVariant
            java.lang.reflect.Field variantField = learner.getClass().getDeclaredField("currentVariant");
            variantField.setAccessible(true);
            currentVariant = (Variant) variantField.get(learner);
        } catch (Exception e) {
            System.out.println("Erreur lors de l'accès à la variante actuelle: " + e.getMessage());
            return;
        }

        if (currentVariant == Variant.CpNC_COMV || currentVariant == Variant.CpNC_CORV) {
            // Affiche l'attribut pertinent et ses valeurs
            String pertinentAttribute = learner.getMostPertinentAttribute();
            NominalContext context = learner.getNominalContext();

            if (context != null && pertinentAttribute != null) {
                Map<String, Set<Integer>> valuesMap = context.getDeltaIndex().get(pertinentAttribute);

                if (valuesMap != null && !valuesMap.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Attribut pertinent sélectionné : ").append(pertinentAttribute).append("\n");

                    // Formatter la liste des valeurs comme dans l'exemple
                    List<String> valuesList = new ArrayList<>(valuesMap.keySet());
                    Collections.sort(valuesList); // Trier les valeurs par ordre alphabétique

                    sb.append("Valeurs pertinentes pour ").append(pertinentAttribute).append(" : [");
                    sb.append(String.join(", ", valuesList));
                    sb.append("] <-- selected");

                    printDebugSection("Attribut pertinent", sb.toString());
                }
            }
        }
    }

    /**
     * Affiche les détails de la sélection d'attributs et valeurs selon la variante choisie.
     * Pour les variantes CpNC_COMV et CaNC_COMV, affiche simplement les attributs et leurs valeurs.
     * Pour les variantes CpNC_CORV et CaNC_CORV, affiche les attributs et leurs valeurs avec leur gain d'information.
     *
     * @param learner Instance du CANCLearnerMOA contenant le contexte et la variante actuelle
     * @return true si les détails ont été affichés, false sinon
     */
    public static boolean printSelectionDetails(com.lamsili.canc.classifier.CANCLearnerMOA learner) {
        if (!debugEnabled) {
            return false;
        }

        if (learner == null) {
            System.out.println("Erreur: Impossible d'afficher les détails de sélection, learner est null");
            return false;
        }

        // Récupérer le contexte
        NominalContext context = learner.getNominalContext();
        if (context == null) {
            System.out.println("Erreur: Contexte non disponible pour afficher les détails de sélection");
            return false;
        }

        // Récupérer la variante actuelle
        Variant currentVariant = null;
        try {
            // On doit utiliser la réflexion pour accéder au champ privé currentVariant
            java.lang.reflect.Field variantField = learner.getClass().getDeclaredField("currentVariant");
            variantField.setAccessible(true);
            currentVariant = (Variant) variantField.get(learner);
        } catch (Exception e) {
            System.out.println("Erreur lors de l'accès à la variante actuelle: " + e.getMessage());
            return false;
        }

        // Créer un ClosureOperator pour les calculs
        ClosureOperator closureOperator = new ClosureOperator(context);

        // Traitement spécifique selon la variante
        switch (currentVariant) {
            case CpNC_COMV:
                // Afficher le gain d'information pour tous les attributs
                printDebugSection("Calcul du gain d'information",
                    formatAttributeScoresInfo(learner.getAttributeScores()));

                // Afficher l'attribut pertinent avec ses valeurs
                printAttributesWithAllValuesViaCancDebugger(learner);
                break;
            case CaNC_COMV:
                // Pour CaNC_COMV, on n'affiche pas les gains d'information
                printAttributesWithAllValuesViaCancDebugger(learner);
                break;
            case CpNC_CORV:
                // Afficher le gain d'information pour tous les attributs
                Map<String, Double> attributeScoresCpNC = learner.getAttributeScores();
                printDebugSection("Calcul du gain d'information",
                    formatAttributeScoresInfo(attributeScoresCpNC));

                // Afficher l'attribut sélectionné avec son score
                String pertinentAttribute = learner.getMostPertinentAttribute();
                printInfoGainSummary(attributeScoresCpNC, pertinentAttribute);

                // Affichage des valeurs pertinentes
                printValueDetails(context, closureOperator,
                    pertinentAttribute, currentVariant, learner::getRelevantValue);
                break;
            case CaNC_CORV:
                // Pour CaNC_CORV, on affiche les valeurs pertinentes pour chaque attribut
                for (String attribute : learner.getAttributeScores().keySet()) {
                    printValueDetails(context, closureOperator,
                        attribute, currentVariant, learner::getRelevantValue);
                }
                break;
        }

        return true;
    }

    /**
     * Formate les scores d'information pour l'affichage
     */
    private static String formatAttributeScoresInfo(Map<String, Double> attributeScores) {
        StringBuilder sb = new StringBuilder();

        // Trier les entrées par valeur de gain décroissante
        List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(attributeScores.entrySet());
        sortedEntries.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        for (Map.Entry<String, Double> entry : sortedEntries) {
            sb.append("Gain d'information pour l'attribut '").append(entry.getKey()).append("' : ")
              .append(dfGain.format(entry.getValue())).append("\n");
        }
        return sb.toString();
    }

    /**
     * Gère l'affichage des détails de sélection pour un learner.
     * Cette méthode encapsule toute la logique de vérification du flag et d'affichage des détails.
     *
     * @param learner Instance du CANCLearnerMOA pour lequel afficher les détails
     */
    public static void handleSelectionDetails(com.lamsili.canc.classifier.CANCLearnerMOA learner) {
        if (!debugEnabled) {
            return;
        }

        // Vérifier si les détails ont déjà été affichés
        if (!selectionDetailsDisplayed) {
            // Afficher les détails de la sélection d'attributs et valeurs selon la variante
            if (printSelectionDetails(learner)) {
                // Marquer que les détails ont été affichés
                selectionDetailsDisplayed = true;
            }
        }
    }

    /**
     * Génère une description textuelle des concepts formels
     *
     * @param concepts Liste des concepts à décrire
     * @param context Contexte nominal utilisé
     * @param variant Variante de l'algorithme
     * @param pertinentAttribute Attribut le plus pertinent (peut être null)
     * @param closure Opérateur de fermeture
     * @return Description formatée des concepts
     */
    public static String getConceptsDescription(
            List<FormalConcept> concepts,
            NominalContext context,
            Variant variant,
            String pertinentAttribute,
            ClosureOperator closure) {

        StringBuilder sb = new StringBuilder();

        // Si pas de concepts, retourner un message approprié
        if (concepts == null || concepts.isEmpty()) {
            sb.append("Aucun concept généré.");
            return sb.toString();
        }

        // Pour chaque concept, ajouter sa description
        for (int i = 0; i < concepts.size(); i++) {
            FormalConcept concept = concepts.get(i);
            sb.append("Concept #").append(i+1).append(":\n");
            sb.append("  Intent: ").append(concept.getIntent()).append("\n");
            sb.append("  Extent: ").append(concept.getExtent()).append("\n");

            // Ajouter des informations supplémentaires si disponibles
            if (pertinentAttribute != null) {
                sb.append("  Attribut pertinent: ").append(pertinentAttribute).append("\n");
            }
        }

        return sb.toString();
    }
}
