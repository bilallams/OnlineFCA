package com.lamsili.canc.app;

import com.lamsili.canc.fca.closure.ClosureOperator;
import com.lamsili.canc.fca.closure.ClosureOperator.AttributeEvalMethod;
import com.lamsili.canc.fca.concept.FormalConcept;
import com.lamsili.canc.rules.Rule;
import com.lamsili.canc.varriants.Variant;
import com.lamsili.canc.fca.context.NominalContext;
import com.yahoo.labs.samoa.instances.Instance;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.*;

/**
 * CANCDebugger - Version simplifiée pour le débogage de l'algorithme CANC
 */
public class CANCDebugger implements Serializable {
    // Ajouter un serialVersionUID pour la stabilité de la sérialisation
    private static final long serialVersionUID = 1L;
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

    // Structure pour stocker les prédictions (instanceNumber, predictedClass, actualClass, isCorrect)
    private static List<Map<String, Object>> predictionsTable = new ArrayList<>();

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

    // Variable pour stocker l'option showPredictions
    private static boolean showPredictions = false;

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
     * Définit si les détails de sélection ont été affichés
     *
     * @param displayed true si les détails ont été affichés, false sinon
     */
    public static void setSelectionDetailsDisplayed(boolean displayed) {
        selectionDetailsDisplayed = displayed;
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
     * Affiche une section de débogage avec un titre
     */
    public static void printDebugSection(String title, String message) {
        if (!debugEnabled) return;
        System.out.println("\n=== " + title + " ===");
        System.out.println(message);
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

        // Créer une clé unique pour cet ensemble de supports d'attribut
        String cacheKey = currentChunkId + "_relevance_" + attribute;

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
                          metricName + ") ===");

        // Trier les valeurs selon la métrique appropriée
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
            if (gain != null) {
                // Créer une clé unique pour ce gain d'information
                String cacheKey = currentChunkId + "_" + selectedAttr + "_" + dfGain.format(gain);

                // Vérifier si on a déjà affiché cette information
                if (!displayedInfoGains.contains(cacheKey)) {
                    // Ajouter au cache pour éviter la répétition
                    displayedInfoGains.add(cacheKey);

                    System.out.println("Attribut sélectionné : " + selectedAttr +
                                      " (Gain d'information : " + dfGain.format(gain) + ")");
                }
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

        // Créer une clé unique pour éviter l'affichage redondant
        String cacheKey = currentChunkId + "_details_" + attribute;
        if (displayedValueSupports.contains(cacheKey)) {
            // Information déjà affichée pour cet attribut dans ce chunk
            return;
        }

        // Ajouter au cache pour éviter la répétition
        displayedValueSupports.add(cacheKey);

        // Obtenir les valeurs possibles pour cet attribut
        Map<String, Set<Integer>> valuesMap = context.getDeltaIndex().get(attribute);
        if (valuesMap == null || valuesMap.isEmpty()) return;

        // Calculer l'entropie/support pour chaque valeur
        Map<String, Double> valueScores = new HashMap<>();
        for (String value : valuesMap.keySet()) {
            double score = closureOperator.calculateRelevanceScore(attribute, value);
            valueScores.put(value, score);
        }

        // Préparer le texte pour l'affichage
        StringBuilder sb = new StringBuilder();
        sb.append("Valeurs pour l'attribut '").append(attribute).append("':\n");

        // Afficher le score pour chaque valeur
        for (Map.Entry<String, Double> entry : valueScores.entrySet()) {
            sb.append("Valeur '").append(entry.getKey()).append("' : ")
              .append(String.format("%.4f", entry.getValue())).append("\n");
        }

        // Si on est dans une variante qui utilise la valeur pertinente
        if (currentVariant == Variant.CpNC_CORV || currentVariant == Variant.CaNC_CORV) {
            String relevantValue = getRelevantValueCallback.apply(attribute);
            Double score = valueScores.get(relevantValue);
            if (relevantValue != null && score != null) {
                // Utiliser le nom correct de la métrique (support ou entropie)
                String metricName = (currentValueEvalMethod == ClosureOperator.ValueEvalMethod.SUPPORT)
                                    ? "Support" : "Entropie";
                sb.append("Valeur pertinente sélectionnée pour ").append(attribute).append(" : ")
                  .append(relevantValue).append(" (").append(metricName).append(" : ")
                  .append(String.format("%.4f", score)).append(")");
            }
        }

        // Afficher le résultat
        printDebugSection("Détails des valeurs pour " + attribute, sb.toString());

        // Nous supprimons l'appel à printRelevantValueCalculation pour éviter la redondance
        // car les informations nécessaires sont déjà affichées ci-dessus
        // Le code suivant est commenté pour éliminer la redondance
        /*
        if (currentVariant == Variant.CpNC_CORV || currentVariant == Variant.CaNC_CORV) {
            String relevantValue = getRelevantValueCallback.apply(attribute);
            if (relevantValue != null) {
                printRelevantValueCalculation(attribute, valueScores, relevantValue);
            }
        }
        */
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
        Variant currentVariant = learner.getCurrentVariant();
        if (currentVariant == null) {
            System.out.println("Erreur: Variante actuelle non disponible");
            return;
        }

        if (currentVariant == Variant.CpNC_COMV || currentVariant == Variant.CpNC_CORV) {
            // Affiche l'attribut pertinent et ses valeurs
            String pertinentAttribute = learner.getMostPertinentAttribute();
            NominalContext context = learner.getNominalContext();

            if (context != null && pertinentAttribute != null) {
                // Nouveau: vérifier si on est en mode restreint (après rejet)
                String restrictedValue = null;
                try {
                    restrictedValue = learner.getRejectedPertinentAttributeValueIfRestricted();
                } catch (Throwable ignored) {}
                if (restrictedValue != null) {
                    // Afficher uniquement la valeur de l'instance rejetée
                    StringBuilder sb = new StringBuilder();
                    sb.append("Attribut pertinent sélectionné : ").append(pertinentAttribute).append("\n");
                    sb.append("Valeurs pertinentes pour ").append(pertinentAttribute).append(" : [").append(restrictedValue).append("] <-- selected (mode restreint)");
                    printDebugSection("Attribut pertinent (rejet)", sb.toString());
                    return; // ne pas afficher toutes les valeurs
                }

                // Comportement normal (toutes les valeurs)
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
     * Pour les variantes CpNC_CORV et CaNC_CORV, affiche les attributs et leurs valeurs avec leur évaluation.
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

        // Récupérer la variante actuelle en utilisant le getter au lieu de la réflexion
        Variant currentVariant = learner.getCurrentVariant();
        if (currentVariant == null) {
            System.out.println("Erreur: Variante actuelle non disponible");
            return false;
        }

        // Créer un ClosureOperator pour les calculs
        ClosureOperator closureOperator = new ClosureOperator(context);

        // Traitement spécifique selon la variante et la méthode d'évaluation choisie
        switch (currentVariant) {
            case CpNC_COMV:
                // Afficher le gain d'information ou le gain ratio selon la méthode choisie
                if (currentEvalMethod == AttributeEvalMethod.INFORMATION_GAIN) {
                    printDebugSection("Calcul du gain d'information",
                        formatAttributeScoresInfoWithLabel(learner.getAttributeScores(), "Gain d'information"));
                } else if (currentEvalMethod == AttributeEvalMethod.GAIN_RATIO) {
                    printDebugSection("Calcul du gain ratio",
                        formatAttributeScoresInfoWithLabel(learner.getAttributeScores(), "Gain ratio"));
                }

                // Afficher l'attribut pertinent avec ses valeurs
                printAttributesWithAllValuesViaCancDebugger(learner);
                break;
            case CaNC_COMV:
                // Pour CaNC_COMV, on n'affiche pas les gains d'information ni les gains ratio
                printAttributesWithAllValuesViaCancDebugger(learner);
                break;
            case CpNC_CORV:
                // Afficher le gain d'information ou le gain ratio selon la méthode choisie
                if (currentEvalMethod == AttributeEvalMethod.INFORMATION_GAIN) {
                    printDebugSection("Calcul du gain d'information",
                        formatAttributeScoresInfoWithLabel(learner.getAttributeScores(), "Gain d'information"));
                } else if (currentEvalMethod == AttributeEvalMethod.GAIN_RATIO) {
                    printDebugSection("Calcul du gain ratio",
                        formatAttributeScoresInfoWithLabel(learner.getAttributeScores(), "Gain ratio"));
                }

                // Afficher l'attribut sélectionné avec son score
                String pertinentAttribute = learner.getMostPertinentAttribute();
                printInfoGainSummary(learner.getAttributeScores(), pertinentAttribute);
                break;
            case CaNC_CORV:
                // Pour CaNC_CORV, on n'affiche pas les gains d'information ni les gains ratio
                // On affiche uniquement les valeurs pertinentes pour chaque attribut
                for (String attribute : learner.getAttributeScores().keySet()) {
                    printValueDetails(context, closureOperator,
                        attribute, currentVariant, learner::getRelevantValue);
                }
                break;
        }

        return true;
    }

    /**
     * Formate les scores d'information pour l'affichage avec un label personnalisé
     * @param attributeScores Les scores des attributs à afficher
     * @param label Le label à utiliser pour l'affichage (ex: "Gain ratio" ou "Gain d'information")
     * @return Une chaîne formatée pour affichage
     */
    private static String formatAttributeScoresInfoWithLabel(Map<String, Double> attributeScores, String label) {
        StringBuilder sb = new StringBuilder();

        // Trier les entrées par valeur de gain décroissante
        List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(attributeScores.entrySet());
        sortedEntries.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        for (Map.Entry<String, Double> entry : sortedEntries) {
            sb.append(label).append(" pour l'attribut '").append(entry.getKey()).append("' : ")
              .append(dfGain.format(entry.getValue())).append("\n");
        }
        return sb.toString();
    }


    /**
     * Génère une description textuelle des concepts formels et leurs règles associées
     *
     * @param concepts Liste des concepts à décrire
     * @param context Contexte nominal utilisé
     * @param variant Variante de l'algorithme
     * @param pertinentAttribute Attribut le plus pertinent (peut être null)
     * @param closure Opérateur de fermeture
     * @param ruleExtractor Extracteur de règles à utiliser pour générer les règles
     * @param chunkId Identifiant du chunk courant (pour l'affichage)
     * @return Description formatée des concepts
     */
    public static String getConceptsDescription(
            List<FormalConcept> concepts,
            NominalContext context,
            Variant variant,
            String pertinentAttribute,
            ClosureOperator closure,
            com.lamsili.canc.rules.RuleExtractor ruleExtractor,
            int chunkId) {

        // Déléguer à la nouvelle méthode generateConceptsDescription qui est plus complète
        return generateConceptsDescription(concepts, context, pertinentAttribute, variant, chunkId, ruleExtractor);
    }

    /**
     * Génère une description détaillée des concepts et de leurs règles associées
     * @param concepts Liste des concepts formels
     * @param context Contexte nominal pour l'interprétation des attributs
     * @param pertinentAttribute L'attribut pertinent (pour les variantes CpNC)
     * @param currentVariant Variante actuelle de l'algorithme
     * @param chunkId Identifiant du chunk actuel
     * @param ruleExtractor Extracteur de règles à utiliser
     * @return Description formatée des concepts et des règles
     */
    public static String generateConceptsDescription(List<FormalConcept> concepts,
                                                  NominalContext context,
                                                  String pertinentAttribute,
                                                  Variant currentVariant,
                                                  int chunkId,
                                                  com.lamsili.canc.rules.RuleExtractor ruleExtractor) {
        if (!debugEnabled || concepts == null || concepts.isEmpty()) {
            return "";
        }

        StringBuilder conceptsDescription = new StringBuilder();

        // Afficher l'entête pour les concepts du chunk courant
        conceptsDescription.append("--- CONCEPTS ET RÈGLES  à l'instance ").append(chunkId).append(" ---\n");

        for (int i = 0; i < concepts.size(); i++) {
            FormalConcept concept = concepts.get(i);
            conceptsDescription.append("Concept #").append(i + 1).append(" :\n");
            conceptsDescription.append("  Intent : ").append(concept.getIntent()).append("\n");

            // Afficher les extents en ajoutant 1 à chaque indice pour commencer à 1 au lieu de 0
            Set<Integer> extent = concept.getExtent();
            StringBuilder extentDisplay = new StringBuilder("[");
            boolean first = true;
            for (Integer idx : extent) {
                if (!first) extentDisplay.append(", ");
                extentDisplay.append(idx + 1); // Ajouter 1 pour commencer à 1
                first = false;
            }
            extentDisplay.append("]");
            conceptsDescription.append("  Extent : ").append(extentDisplay).append("\n");

            // On détermine l'attribut pertinent et sa valeur selon la variante
            if (currentVariant == Variant.CpNC_COMV || currentVariant == Variant.CpNC_CORV) {
                conceptsDescription.append("  Attribut pertinent : ").append(pertinentAttribute).append("\n");

                // Pour les valeurs pertinentes selon la variante
                if (currentVariant == Variant.CpNC_COMV) {
                    // Pour CpNC_COMV, la valeur pertinente est celle qui est dans l'intention pour cet attribut
                    Set<Map.Entry<String, String>> intent = concept.getIntent();
                    for (Map.Entry<String, String> pair : intent) {
                        if (pair.getKey().equals(pertinentAttribute)) {
                            conceptsDescription.append("  Valeur pertinente : ").append(pair.getValue()).append("\n");
                            break;
                        }
                    }
                } else {
                    // Pour CpNC_CORV, utiliser le calcul de valeur pertinente
                    // Note: Cette partie nécessiterait d'exposer une méthode dans CANCLearnerMOA
                    // conceptsDescription.append("  Valeur pertinente : ").append(relevantValue).append("\n");
                }
            }

            // Générer les règles pour ce concept spécifique
            List<FormalConcept> singleConceptList = new ArrayList<>();
            singleConceptList.add(concept);
            List<Rule> conceptRules = ruleExtractor.extractRules(singleConceptList, context, useDisjointRules);

            // Si des règles sont générées pour ce concept, les afficher
            if (!conceptRules.isEmpty()) {
                for (Rule rule : conceptRules) {
                    conceptsDescription.append("    Règle : ")
                                     .append(rule.toString())
                                     .append("\n");
                }
            }

            conceptsDescription.append("\n");
        }

        return conceptsDescription.toString();
    }

    /**
     * Génère une description détaillée des règles avec leurs métriques
     * @param rules Liste des règles à décrire
     * @return Description formatée des règles
     */
    public static String generateRulesDescription(List<Rule> rules) {
        if (!debugEnabled || rules == null || rules.isEmpty()) {
            return "";
        }

        StringBuilder rulesDescription = new StringBuilder();
        rulesDescription.append("--- RÈGLES GÉNÉRÉES ---\n");

        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            rulesDescription.append("Règle ").append(i + 1).append(": ")
                           .append(rule.toString())
                           .append("\n");
        }

        return rulesDescription.toString();
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
     * Définit si les prédictions doivent être affichées
     * @param show True pour afficher les prédictions, false sinon
     */
    public static void setShowPredictions(boolean show) {
        showPredictions = show;
    }

    /**
     * Vérifie si l'affichage des prédictions est activé
     * @return True si l'affichage des prédictions est activé
     */
    public static boolean isShowPredictions() {
        return showPredictions;
    }

    /**
     * Affiche les résultats de prédiction sous forme de tableau
     * Ne s'affiche que si showPredictions est activé
     *
     * @param results Liste des résultats de prédiction
     * @param currentVariant Variante actuelle de l'algorithme
     */
    public static void displayPredictionResults(List<?> results, Variant currentVariant) {
        if (!showPredictions || results == null || results.isEmpty()) {
            return;
        }

        // Calcul des statistiques
        int total = results.size();
        int correct = 0;
        int rejected = 0;

        // Construire le tableau des résultats
        StringBuilder table = new StringBuilder();
        table.append("\n-------------------RÉSULTATS DES PRÉDICTIONS-------------------------------------------------------------------------------\n");
        table.append(String.format("Variant: %s | Instances: %d\n", currentVariant, total));
        table.append(String.format("%-10s %-15s %-15s %-10s %-12s %-12s %-25s\n",
                "Instance #", "Classe Réelle", "Classe Prédite", "Correct?", "Poids Before", "Poids After", "Règles Utilisées"));
        table.append("------------------------------------------------------------------------------------------------------------------------\n");

        int displayIndex = 1; // Compteur séquentiel pour l'affichage des instances

        for (Object resultObj : results) {
            try {
                // Utiliser la réflexion pour accéder aux champs de la classe PredictionResult
                int instanceNum = (int) resultObj.getClass().getDeclaredField("predictionId").get(resultObj);
                String trueClass = (String) resultObj.getClass().getDeclaredField("actualClass").get(resultObj);
                String predClass = (String) resultObj.getClass().getDeclaredField("predictedClass").get(resultObj);

                // Récupérer les poids avant et après (nouveaux champs à ajouter dans PredictionResult)
                double weightBefore = 0.0;
                double weightAfter = 0.0;
                try {
                    weightBefore = (double) resultObj.getClass().getDeclaredField("weightBefore").get(resultObj);
                    weightAfter = (double) resultObj.getClass().getDeclaredField("weightAfter").get(resultObj);
                } catch (Exception e) {
                    // Si les champs n'existent pas encore, utiliser des valeurs par défaut
                }

                // Vérifier si l'instance est rejetée en examinant les votes
                double[] votes = (double[]) resultObj.getClass().getDeclaredField("votes").get(resultObj);
                boolean isRejected = votes != null && votes.length > 0 && votes[0] < 0;

                // Déterminer si la prédiction est correcte (seulement si non rejetée)
                boolean isCorrect = !isRejected && trueClass.equals(predClass);

                // Si l'instance est rejetée, afficher "REJET" au lieu de la classe prédite
                if (isRejected) {
                    predClass = "REJET";
                    rejected++; // Incrémenter le compteur de rejets
                } else if (isCorrect) {
                    correct++; // Incrémenter le compteur de prédictions correctes
                }

                // Récupérer les règles applicables
                List<Rule> appliedRules = null;
                try {
                    appliedRules = (List<Rule>) resultObj.getClass().getDeclaredField("applicableRules").get(resultObj);
                } catch (Exception e) {
                    // Ignorer si le champ n'existe pas
                }

                // Formater les indices des règles utilisées
                String rulesUsed = isRejected ? "Aucune règle applicable" : "Aucune";
                if (!isRejected && appliedRules != null && !appliedRules.isEmpty()) {
                    StringBuilder ruleIndices = new StringBuilder();

                    // Récupérer le classifieur à partir de l'objet resultObj
                    Object classifierObj = null;
                    try {
                        classifierObj = resultObj.getClass().getDeclaredField("classifier").get(resultObj);
                    } catch (Exception e) {
                        // Si on ne peut pas récupérer le classifieur, utiliser une méthode de secours
                        for (int i = 0; i < appliedRules.size(); i++) {
                            if (i > 0) ruleIndices.append(", ");
                            ruleIndices.append("R").append(i + 1);
                        }
                        rulesUsed = ruleIndices.toString();
                        continue;
                    }

                    // Vérifier que le classifieur n'est pas null
                    if (classifierObj == null) {
                        for (int i = 0; i < appliedRules.size(); i++) {
                            if (i > 0) ruleIndices.append(", ");
                            ruleIndices.append("R").append(i + 1);
                        }
                        rulesUsed = ruleIndices.toString();
                        continue;
                    }

                    // Récupérer la liste des règles du classifieur
                    List<Rule> extractedRules = null;
                    try {
                        extractedRules = (List<Rule>) classifierObj.getClass().getMethod("getRules").invoke(classifierObj);
                    } catch (Exception e) {
                        // Méthode de secours si on ne peut pas récupérer les règles
                        for (int i = 0; i < appliedRules.size(); i++) {
                            if (i > 0) ruleIndices.append(", ");
                            ruleIndices.append("R").append(i + 1);
                        }
                        rulesUsed = ruleIndices.toString();
                        continue;
                    }

                    // Traiter chaque règle appliquée individuellement
                    for (int i = 0; i < appliedRules.size(); i++) {
                        Rule appliedRule = appliedRules.get(i);

                        // Ajouter une virgule si ce n'est pas le premier élément
                        if (i > 0) ruleIndices.append(", ");

                        // Pour chaque règle appliquée, trouver TOUTES les règles extraites correspondantes
                        String ruleSignature = appliedRule.getConditions().toString() + appliedRule.getPredictedClass();

                        // Parcourir toutes les règles extraites pour trouver celle qui correspond
                        boolean foundMatch = false;
                        for (int j = 0; j < extractedRules.size(); j++) {
                            Rule extractedRule = extractedRules.get(j);
                            String extractedSignature = extractedRule.getConditions().toString() + extractedRule.getPredictedClass();

                            // Si c'est une correspondance ET c'est la même instance (même référence d'objet)
                            if (extractedSignature.equals(ruleSignature) && appliedRule == extractedRule) {
                                ruleIndices.append("R").append(j + 1); // +1 pour commencer à 1 au lieu de 0
                                foundMatch = true;
                                break;
                            }
                        }

                        // Si aucune correspondance exacte n'est trouvée, utiliser la première règle correspondante
                        if (!foundMatch) {
                            for (int j = 0; j < extractedRules.size(); j++) {
                                Rule extractedRule = extractedRules.get(j);
                                String extractedSignature = extractedRule.getConditions().toString() + extractedRule.getPredictedClass();

                                if (extractedSignature.equals(ruleSignature)) {
                                    ruleIndices.append("R").append(j + 1); // +1 pour commencer à 1 au lieu de 0
                                    foundMatch = true;
                                    break;
                                }
                            }
                        }

                        // Si toujours aucune correspondance, utiliser R?
                        if (!foundMatch) {
                            ruleIndices.append("R?");
                        }
                    }
                    rulesUsed = ruleIndices.toString();
                }

                // Utiliser displayIndex au lieu de instanceNum et inclure les poids before/after
                table.append(String.format("%-10d %-15s %-15s %-10s %-12.4f %-12.4f %-25s\n",
                        displayIndex, trueClass, predClass,
                        isRejected ? "Non" : (isCorrect ? "Oui" : "Non"),
                        weightBefore, weightAfter,
                        rulesUsed));

                displayIndex++; // Incrémenter le compteur
            } catch (Exception e) {
                // En cas d'erreur, on continue simplement avec le prochain résultat
                continue;
            }
        }

        // Ajouter la précision et le taux de rejet en bas du tableau
        double accuracy = (double) correct / total * 100.0; // Les rejets sont considérés comme des erreurs
        double rejectionRate = (double) rejected / total * 100.0;
        
        table.append("------------------------------------------------------------------------------------------------------------------------\n");
        table.append(String.format("Précision: %.2f%% (%d/%d)\n", accuracy, correct, total));
        table.append(String.format("Taux de rejet: %.2f%% (%d/%d)\n", rejectionRate, rejected, total));

        // Afficher le tableau
        System.out.println(table.toString());
    }

    /**
     * Affiche toutes les instances actuellement traitées dans le contexte
     * @param context Le contexte nominal contenant les instances
     */
    public static void printProcessedInstances(NominalContext context) {
        if (!debugEnabled) return;

        if (context == null || context.getNumInstances() == 0) {
            System.out.println("Aucune instance traitée");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Instances traitées:\n");

        for (int i = 0; i < context.getNumInstances(); i++) {
            Instance instance = context.getInstance(i);
            if (instance == null) continue;

            // Construire une représentation de l'instance similaire au format ARFF
            StringBuilder instanceStr = new StringBuilder();
            for (int j = 0; j < instance.numAttributes(); j++) {
                if (j == instance.classIndex()) continue; // Ajouter la classe à la fin

                if (instanceStr.length() > 0) {
                    instanceStr.append(",");
                }

                // Obtenir la valeur de l'attribut
                if (instance.attribute(j).isNominal()) {
                    instanceStr.append(instance.attribute(j).value((int) instance.value(j)));
                } else {
                    instanceStr.append(instance.value(j));
                }
            }

            // Ajouter la classe à la fin - Utiliser une alternative à stringValue qui n'est pas implémentée
            if (instance.classAttribute().isNominal()) {
                instanceStr.append(",").append(instance.classAttribute().value((int) instance.classValue()));
            } else {
                instanceStr.append(",").append(instance.classValue());
            }

            sb.append(instanceStr.toString()).append("\n");
        }

        // Afficher les instances
        System.out.println(sb.toString());
    }

    /**
     * Génère une description détaillée des concepts et de leurs règles associées avec un offset
     * @param concepts Liste des concepts formels
     * @param context Contexte nominal pour l'interprétation des attributs
     * @param pertinentAttribute L'attribut pertinent (pour les variantes CpNC)
     * @param currentVariant Variante actuelle de l'algorithme
     * @param chunkId Identifiant du chunk actuel
     * @param ruleExtractor Extracteur de règles à utiliser
     * @param offset Décalage à appliquer aux indices de concept
     * @return Description formatée des concepts et des règles
     */
    public static String generateConceptsDescriptionWithOffset(List<FormalConcept> concepts,
                                                               NominalContext context,
                                                               String pertinentAttribute,
                                                               Variant currentVariant,
                                                               int chunkId,
                                                               com.lamsili.canc.rules.RuleExtractor ruleExtractor,
                                                               int offset) {
        if (!debugEnabled || concepts == null || concepts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("--- NOUVEAUX CONCEPTS ").append(") à l'instance ").append(chunkId).append(" ---\n");
        for (int i = 0; i < concepts.size(); i++) {
            FormalConcept concept = concepts.get(i);
            sb.append("Concept #").append(offset + i + 1).append(" :\n");
            sb.append("  Intent : ").append(concept.getIntent()).append("\n");
            // Extent déjà en indices originaux; afficher +1 (convention display)
            Set<Integer> extent = concept.getExtent();
            StringBuilder extentDisplay = new StringBuilder("[");
            boolean first = true;
            for (Integer idx : extent) {
                if (!first) extentDisplay.append(", ");
                extentDisplay.append(idx + 1);
                first = false;
            }
            extentDisplay.append("]");
            sb.append("  Extent : ").append(extentDisplay).append("\n");
            if (currentVariant == Variant.CpNC_COMV || currentVariant == Variant.CpNC_CORV) {
                sb.append("  Attribut pertinent : ").append(pertinentAttribute).append("\n");
                if (currentVariant == Variant.CpNC_COMV) {
                    for (Map.Entry<String,String> pair : concept.getIntent()) {
                        if (pair.getKey().equals(pertinentAttribute)) { sb.append("  Valeur pertinente : ").append(pair.getValue()).append("\n"); break; }
                    }
                }
            }
        }
        return sb.toString();
    }
}
