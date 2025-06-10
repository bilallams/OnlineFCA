package com.lamsili.canc.classifier;

import com.lamsili.canc.fca.closure.ClosureOperator;
import com.lamsili.canc.fca.concept.FormalConcept;
import com.lamsili.canc.fca.context.NominalContext;
import com.lamsili.canc.rules.Rule;
import com.lamsili.canc.rules.RuleExtractor;
import com.lamsili.canc.varriants.NCACoupleSelector;
import com.lamsili.canc.varriants.Variant;

import moa.classifiers.AbstractClassifier;
import moa.classifiers.MultiClassClassifier;
import moa.core.Measurement;
import moa.core.StringUtils;
import moa.core.Utils;
import com.github.javacliparser.IntOption;
import com.github.javacliparser.FlagOption;
import com.github.javacliparser.MultiChoiceOption;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.InstancesHeader;

import java.util.*;

/**
 * Implémentation d'un classifieur NCA (Nominal Concept Analysis) compatible avec MOA.
 * Ce classifieur utilise l'analyse de concepts formels pour générer des règles de classification
 * à partir d'un flux de données nominal.
 */
public class CANCLearnerMOA extends AbstractClassifier implements MultiClassClassifier {

    private static final long serialVersionUID = 1L;

    // Options MOA
    public IntOption gracePeriodOption = new IntOption("gracePeriod", 'g',
            "Nombre d'instances à accumuler avant de construire le modèle", 50, 0, Integer.MAX_VALUE);

    public MultiChoiceOption variantOption = new MultiChoiceOption("variant", 'v',
            "Variante de l'algorithme NCA à utiliser",
            new String[]{"CpNC_COMV", "CpNC_CORV", "CaNC_COMV", "CaNC_CORV"},
            new String[]{
                "CpNC_COMV: attribut pertinent, fermeture multi-valeurs",
                "CpNC_CORV: attribut pertinent, fermeture valeur pertinente",
                "CaNC_COMV: tous les attributs, fermeture multi-valeurs",
                "CaNC_CORV: tous les attributs, fermeture valeurs pertinentes"
            },
            0);

    public FlagOption resetOption = new FlagOption("reset", 'r',
            "Reset le modèle après chaque construction");

    // Nouvelles options pour le fenêtrage
    public IntOption windowSizeOption = new IntOption("windowSize", 'w',
            "Taille de la fenêtre d'instances (0 = pas de fenêtre)", 0, 0, Integer.MAX_VALUE);

    public FlagOption windowingOption = new FlagOption("useWindowing", 'u',
            "Activer le fenêtrage pour limiter la mémoire");

    // Variables de l'algorithme
    private NominalContext context;
    private ClosureOperator closureOperator;
    private NCACoupleSelector coupleSelector;
    private List<Rule> rules;
    private RuleExtractor ruleExtractor;
    private Variant currentVariant;

    // Liste pour conserver les 10 premiers concepts générés
    private List<FormalConcept> firstConcepts = new ArrayList<>();
    private boolean firstConceptsDisplayed = false;

    // Statistiques
    private int instancesSeen;
    private int lastModelBuildSize;
    private int conceptsGenerated;
    private int rulesGenerated;

    // Variable pour suivre si les détails de sélection ont déjà été affichés
    private boolean selectionDetailsDisplayed = false;

    @Override
    public void resetLearningImpl() {
        // Initialiser le contexte avec la taille de fenêtre si elle est spécifiée
        if (windowingOption.isSet() && windowSizeOption.getValue() > 0) {
            this.context = new NominalContext(windowSizeOption.getValue());
        } else {
            this.context = new NominalContext();
        }

        this.instancesSeen = 0;
        this.lastModelBuildSize = 0;
        this.conceptsGenerated = 0;
        this.rulesGenerated = 0;
        this.rules = new ArrayList<>();
        this.ruleExtractor = new RuleExtractor();

        // Déterminer la variante à utiliser
        switch (variantOption.getChosenIndex()) {
            case 0:
                this.currentVariant = Variant.CpNC_COMV;
                break;
            case 1:
                this.currentVariant = Variant.CpNC_CORV;
                break;
            case 2:
                this.currentVariant = Variant.CaNC_COMV;
                break;
            case 3:
                this.currentVariant = Variant.CaNC_CORV;
                break;
            default:
                this.currentVariant = Variant.CpNC_COMV;
        }
    }

    @Override
    public void setModelContext(InstancesHeader context) {
        super.setModelContext(context);

        // Initialisation si nécessaire
        if (this.context == null) {
            resetLearningImpl();
        }
    }

    @Override
    public void trainOnInstanceImpl(Instance inst) {
        // Incrémenter le compteur d'instances
        instancesSeen++;

        // Ajouter l'instance directement au contexte
        context.addInstance(inst);

        // Initialiser le closure operator si nécessaire
        if (closureOperator == null) {
            closureOperator = new ClosureOperator(context);
        }

        // Initialiser le couple selector si nécessaire
        if (coupleSelector == null) {
            coupleSelector = new NCACoupleSelector(context);
        }

        // Reconstruire le modèle si la période de grâce est atteinte
        if (instancesSeen - lastModelBuildSize >= gracePeriodOption.getValue()) {
            buildModel();
        }
    }

    /**
     * Construit le modèle en générant les concepts formels et en extrayant les règles.
     */
    private void buildModel() {
        // Générer les concepts formels
        List<FormalConcept> concepts = generateConcepts();
        conceptsGenerated = concepts.size();

        // Stocker les 10 premiers concepts s'ils n'ont pas déjà été sauvegardés
        if (firstConcepts.isEmpty() && !concepts.isEmpty()) {
            // On limite à 10 concepts maximum
            int numToAdd = Math.min(concepts.size(), 10);
            firstConcepts.addAll(concepts.subList(0, numToAdd));

            // Afficher ces concepts
            printFirstConcepts();
        }

        // Extraire les règles
        rules = ruleExtractor.extractRules(concepts, context);
        rulesGenerated = rules.size();

        // Calculer le support et la confiance pour chaque règle
        calculateRuleMetrics();

        // Mettre à jour le compteur de modèles
        lastModelBuildSize = instancesSeen;

        // Réinitialiser si nécessaire
        if (resetOption.isSet()) {
            context.clear();
            closureOperator = new ClosureOperator(context);
            coupleSelector = new NCACoupleSelector(context);
        }
    }

    /**
     * Calcule les métriques de support et confiance pour toutes les règles
     */
    private void calculateRuleMetrics() {
        if (rules == null || rules.isEmpty()) return;

        int totalInstances = context.getNumInstances();

        for (Rule rule : rules) {
            // Compter combien d'instances satisfont les conditions de la règle
            int matchingInstances = 0;
            int correctPredictions = 0;

            for (int i = 0; i < totalInstances; i++) {
                Instance instance = context.getInstance(i);
                if (rule.appliesTo(instance)) {
                    matchingInstances++;

                    // Vérifier si la prédiction est correcte
                    String actualClass = context.getInstanceClass(i);
                    if (actualClass != null && actualClass.equals(rule.getPredictedClass())) {
                        correctPredictions++;
                    }
                }
            }

            // Mettre à jour le support
            rule.setSupport(matchingInstances);

            // Calculer et mettre à jour la confiance
            if (matchingInstances > 0) {
                double confidence = (double) correctPredictions / matchingInstances;
                rule.setConfidence(confidence);

                // Ajuster le poids en fonction de la confiance
                rule.setWeight(confidence * matchingInstances / totalInstances);
            } else {
                rule.setConfidence(0.0);
                rule.setWeight(0.0);
            }
        }
    }

    /**
     * Génère les concepts formels à partir du contexte actuel en respectant les contraintes de la variante.
     *
     * @return Liste des concepts formels générés
     */
    private List<FormalConcept> generateConcepts() {
        // Initialiser l'operateur de fermeture si nécessaire
        if (closureOperator == null) {
            closureOperator = new ClosureOperator(context);
        }

        // Initialiser le couple selector si nécessaire
        if (coupleSelector == null) {
            coupleSelector = new NCACoupleSelector(context);
        }

        // Précalculer et stocker les attributs et valeurs pertinents avant de générer les concepts
        // Cela garantit que ce calcul est fait une seule fois avant la génération
        if (!selectionDetailsDisplayed) {
            // Afficher les détails de la sélection d'attributs et valeurs selon la variante
            printSelectionDetails();
        }

        // Générer les concepts selon la variante
        switch (currentVariant) {
            case CpNC_COMV:
                return generateCpNC_COMV();
            case CpNC_CORV:
                return generateCpNC_CORV();
            case CaNC_COMV:
                return generateCaNC_COMV();
            case CaNC_CORV:
                return generateCaNC_CORV();
            default:
                return new ArrayList<>();
        }
    }

    /**
     * Génère les concepts pour la variante CpNC_COMV.
     * Utilise uniquement l'attribut le plus pertinent et toutes ses valeurs.
     *
     * @return Liste des concepts formels générés
     */
    private List<FormalConcept> generateCpNC_COMV() {
        List<FormalConcept> concepts = new ArrayList<>();
        Set<Set<Integer>> generatedExtents = new HashSet<>();

        // Obtenir l'attribut le plus pertinent
        String pertinentAttribute = coupleSelector.getMostPertinentAttribute();
        if (pertinentAttribute == null) return concepts;

        // Obtenir toutes les valeurs possibles pour cet attribut
        Map<String, Set<Integer>> valuesMap = context.getDeltaIndex().get(pertinentAttribute);
        if (valuesMap == null || valuesMap.isEmpty()) return concepts;

        // Pour chaque valeur de l'attribut pertinent
        for (Map.Entry<String, Set<Integer>> valueEntry : valuesMap.entrySet()) {
            String value = valueEntry.getKey();

            // Protection contre null
            if (value == null) continue;

            // Calculer l'extension delta(attr, val)
            Set<Integer> extent = closureOperator.delta(pertinentAttribute, value);

            // Vérification supplémentaire contre les ensembles vides
            if (extent == null || extent.isEmpty()) continue;

            // Vérifier la fermeture correcte
            if (!isExtentClosed(extent)) continue;

            // Éviter les duplications en utilisant TreeSet pour garantir une comparaison par contenu
            TreeSet<Integer> sortedExtent = new TreeSet<>(extent);
            if (generatedExtents.contains(sortedExtent)) continue;
            generatedExtents.add(sortedExtent);

            // Créer le concept et l'ajouter à la liste
            concepts.add(new FormalConcept(extent, closureOperator.phi(extent)));
        }

        return concepts;
    }

    /**
     * Génère les concepts pour la variante CpNC_CORV.
     * Utilise uniquement l'attribut le plus pertinent avec sa valeur la plus pertinente.
     *
     * @return Liste des concepts formels générés
     */
    private List<FormalConcept> generateCpNC_CORV() {
        List<FormalConcept> concepts = new ArrayList<>();

        // Obtenir l'attribut le plus pertinent
        String pertinentAttribute = coupleSelector.getMostPertinentAttribute();
        if (pertinentAttribute == null) return concepts;

        // Obtenir la valeur la plus pertinente pour cet attribut
        String relevantValue = closureOperator.getMostRelevantValue(pertinentAttribute);
        if (relevantValue == null) return concepts;

        // Calculer l'extension delta(attr, val)
        Set<Integer> extent = closureOperator.delta(pertinentAttribute, relevantValue);

        // Vérifier la fermeture correcte
        if (!isExtentClosed(extent)) return concepts;

        // Créer le concept et l'ajouter à la liste
        concepts.add(new FormalConcept(extent, closureOperator.phi(extent)));
        return concepts;
    }

    /**
     * Génère les concepts pour la variante CaNC_COMV.
     * Utilise tous les attributs avec toutes leurs valeurs.
     *
     * @return Liste des concepts formels générés
     */
    private List<FormalConcept> generateCaNC_COMV() {
        List<FormalConcept> concepts = new ArrayList<>();
        Set<Set<Integer>> generatedExtents = new HashSet<>();

        for (int i = 0; i < context.getNumInstances(); i++) {
            Instance instance = context.getInstance(i);
            Set<Map.Entry<String, String>> selectedPairs = coupleSelector.selectCouples(instance, Variant.CaNC_COMV);

            if (selectedPairs.isEmpty()) continue;

            for (Map.Entry<String, String> pair : selectedPairs) {
                // Calculer l'extension delta(attr, val)
                Set<Integer> extent = closureOperator.delta(pair.getKey(), pair.getValue());

                // Vérifier la fermeture correcte
                if (!isExtentClosed(extent)) continue;

                // Éviter les duplications
                if (generatedExtents.contains(extent)) continue;
                generatedExtents.add(extent);

                // Créer le concept et l'ajouter à la liste
                concepts.add(new FormalConcept(extent, closureOperator.phi(extent)));
            }
        }

        return concepts;
    }

    /**
     * Génère les concepts pour la variante CaNC_CORV.
     * Pour chaque attribut, utilise sa valeur la plus pertinente.
     *
     * @return Liste des concepts formels générés
     */
    private List<FormalConcept> generateCaNC_CORV() {
        List<FormalConcept> concepts = new ArrayList<>();
        Set<Set<Integer>> generatedExtents = new HashSet<>();
        Set<String> processedAttributes = new HashSet<>();

        for (int i = 0; i < context.getNumInstances(); i++) {
            Instance instance = context.getInstance(i);
            Set<Map.Entry<String, String>> selectedPairs = coupleSelector.selectCouples(instance, Variant.CaNC_CORV);

            if (selectedPairs.isEmpty()) continue;

            for (Map.Entry<String, String> pair : selectedPairs) {
                String attribute = pair.getKey();

                // Éviter de traiter plusieurs fois le même attribut
                if (processedAttributes.contains(attribute)) continue;
                processedAttributes.add(attribute);

                // Obtenir la valeur pertinente pour cet attribut
                String relevantValue = closureOperator.getMostRelevantValue(attribute);
                if (relevantValue == null) continue;

                // Calculer l'extension delta(attr, val)
                Set<Integer> extent = closureOperator.delta(attribute, relevantValue);

                // Vérifier la fermeture correcte
                if (!isExtentClosed(extent)) continue;

                // Éviter les duplications
                if (generatedExtents.contains(extent)) continue;
                generatedExtents.add(extent);

                // Créer le concept et l'ajouter à la liste
                concepts.add(new FormalConcept(extent, closureOperator.phi(extent)));
            }
        }

        return concepts;
    }

    /**
     * Vérifie si un ensemble d'instances est fermé (extent = δ(φ(extent)))
     * en utilisant uniquement delta et phi.
     *
     * @param extent L'ensemble d'instances à vérifier
     * @return true si l'ensemble est fermé, false sinon
     */
    private boolean isExtentClosed(Set<Integer> extent) {
        // Calculer les attributs-valeurs communs à toutes les instances de extent
        Set<Map.Entry<String, String>> intent = closureOperator.phi(extent);

        // Calculer l'extension de ces attributs-valeurs sans utiliser psi
        // On commence avec toutes les instances
        Set<Integer> closedExtent = new HashSet<>();
        for (int i = 0; i < context.getNumInstances(); i++) {
            closedExtent.add(i);
        }

        // Pour chaque paire attribut-valeur dans l'intent, intersecter avec delta
        for (Map.Entry<String, String> pair : intent) {
            Set<Integer> pairExtent = closureOperator.delta(pair.getKey(), pair.getValue());

            // Garder uniquement les instances présentes dans les deux ensembles
            closedExtent.retainAll(pairExtent);

            // Optimisation: si le résultat est vide ou égal à extent, on peut s'arrêter
            if (closedExtent.isEmpty() || closedExtent.equals(extent)) {
                break;
            }
        }

        // Vérifier si l'extent initial est égal à l'extent fermé
        return extent.equals(closedExtent);
    }

    @Override
    public double[] getVotesForInstance(Instance instance) {
        // Si aucune règle n'est disponible, retourner une distribution uniforme
        if (rules == null || rules.isEmpty()) {
            return new double[instance.numClasses()];
        }

        // Distribution des votes par classe
        double[] votes = new double[instance.numClasses()];

        // Compter les votes de chaque règle applicable, pondérés par la confiance
        for (Rule rule : rules) {
            if (rule.appliesTo(instance)) {
                // Trouver l'index de la classe prédite
                int predictedClassIndex = -1;
                for (int i = 0; i < instance.numClasses(); i++) {
                    if (instance.attribute(instance.classIndex()).value(i).equals(rule.getPredictedClass())) {
                        predictedClassIndex = i;
                        break;
                    }
                }

                if (predictedClassIndex >= 0) {
                    // Utiliser la confiance comme poids pour le vote
                    votes[predictedClassIndex] += rule.getConfidence() * rule.getSupport();
                }
            }
        }

        // Normaliser les votes (si nécessaire)
        if (Utils.sum(votes) > 0.0) {
            Utils.normalize(votes);
        }

        return votes;
    }

    @Override
    public boolean isRandomizable() {
        return false;
    }

    @Override
    public void getModelDescription(StringBuilder out, int indent) {
        StringUtils.appendIndented(out, indent, "CANCLearnerMOA (Variant: " + currentVariant + ")\n");
        StringUtils.appendIndented(out, indent, "Règles extraites (" + (rules != null ? rules.size() : 0) + "):\n");

        if (rules != null) {
            for (int i = 0; i < rules.size(); i++) {
                StringUtils.appendIndented(out, indent + 1, (i + 1) + ". " + rules.get(i).toString() + "\n");
            }
        }
    }

    @Override
    protected Measurement[] getModelMeasurementsImpl() {
        return new Measurement[]{
            new Measurement("instances seen", instancesSeen),
            new Measurement("concepts generated", conceptsGenerated),
            new Measurement("rules generated", rulesGenerated)
        };
    }

    @Override
    public String getPurposeString() {
        return "Classifieur basé sur l'analyse de concepts formels nominaux (NCA) avec 4 variantes";
    }

    /**
     * Obtenir la référence au contexte nominal utilisé par le classifieur
     *
     * @return Le contexte nominal
     */
    public NominalContext getNominalContext() {
        return this.context;
    }

    /**
     * Obtenir l'attribut le plus pertinent pour la classification
     *
     * @return Nom de l'attribut le plus pertinent
     */
    public String getMostPertinentAttribute() {
        if (coupleSelector == null) {
            coupleSelector = new NCACoupleSelector(context);
        }
        return coupleSelector.getMostPertinentAttribute();
    }

    /**
     * Obtenir la valeur pertinente pour un attribut donné
     *
     * @param attribute Nom de l'attribut
     * @return La valeur pertinente pour cet attribut
     */
    public String getRelevantValue(String attribute) {
        if (closureOperator == null) {
            closureOperator = new ClosureOperator(context);
        }
        return closureOperator.getMostRelevantValue(attribute);
    }

    /**
     * Obtenir les scores d'information pour tous les attributs
     *
     * @return Map contenant les scores d'information pour chaque attribut
     */
    public Map<String, Double> getAttributeScores() {
        if (coupleSelector == null) {
            coupleSelector = new NCACoupleSelector(context);
        }
        return coupleSelector.getAttributeScores();
    }

    /**
     * Obtenir le nombre de concepts générés
     *
     * @return Nombre de concepts formels
     */
    public int getNumberOfConcepts() {
        return conceptsGenerated;
    }

    /**
     * Obtenir la liste des règles générées
     *
     * @return Liste des règles
     */
    public List<Rule> getRules() {
        return rules;
    }

    /**
     * Obtenir une description textuelle des concepts générés
     *
     * @return Description des concepts
     */
    public String getConceptsDescription() {
        StringBuilder sb = new StringBuilder();

        // Si pas de règles, retourner un message approprié
        if (rules == null || rules.isEmpty()) {
            sb.append("Aucun concept généré.");
            return sb.toString();
        }

        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            sb.append("Concept #").append(i+1).append(":\n");
            sb.append("  Intent: ");

            Map<String, String> conditions = rule.getConditions();
            int count = 0;
            for (Map.Entry<String, String> entry : conditions.entrySet()) {
                if (count > 0) sb.append(", ");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                count++;
            }

            sb.append("\n  Class: ").append(rule.getPredictedClass());
            sb.append("\n  Support: ").append(rule.getSupport());
            sb.append("\n  Confidence: ").append(String.format("%.4f", rule.getConfidence()));
            sb.append("\n  Weight: ").append(String.format("%.4f", rule.getWeight()));
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Affiche les détails des 10 premiers concepts formels générés.
     * Cette méthode affiche l'extension, l'intention, l'attribut pertinent et
     * la valeur pertinente pour chaque concept.
     */
    private void printFirstConcepts() {
        if (firstConcepts.isEmpty() || firstConceptsDisplayed) {
            return;
        }

        System.out.println("\n=== PREMIERS CONCEPTS FORMELS GÉNÉRÉS ===");
        int count = 0;
        for (FormalConcept concept : firstConcepts) {
            if (count >= 10) break;  // Limiter à 10 concepts maximum

            // Obtenir l'extension (ensemble d'instances)
            Set<Integer> extent = concept.getExtent();

            // Obtenir l'intention (attributs-valeurs)
            Set<Map.Entry<String, String>> intent = concept.getIntent();

            // Identifier l'attribut pertinent et sa valeur selon la variante
            String pertinentAttribute = "";
            String pertinentValue = "";

            if (currentVariant == Variant.CpNC_COMV || currentVariant == Variant.CpNC_CORV) {
                pertinentAttribute = getMostPertinentAttribute();
                if (currentVariant == Variant.CpNC_COMV) {
                    // Pour CpNC_COMV, la valeur pertinente est celle qui est dans l'intention pour cet attribut
                    for (Map.Entry<String, String> pair : intent) {
                        if (pair.getKey().equals(pertinentAttribute)) {
                            pertinentValue = pair.getValue();
                            break;
                        }
                    }
                } else {
                    // Pour CpNC_CORV, la valeur pertinente est celle déterminée par l'opérateur de fermeture
                    pertinentValue = getRelevantValue(pertinentAttribute);
                }
            } else if (currentVariant == Variant.CaNC_COMV || currentVariant == Variant.CaNC_CORV) {
                // Pour CaNC, chaque attribut peut être pertinent
                // On peut marquer "multiple" ou choisir le premier de l'intention
                if (!intent.isEmpty()) {
                    Map.Entry<String, String> firstPair = intent.iterator().next();
                    pertinentAttribute = firstPair.getKey();
                    pertinentValue = firstPair.getValue();

                    if (currentVariant == Variant.CaNC_CORV) {
                        pertinentValue = getRelevantValue(pertinentAttribute);
                    }
                }
            }

            // Afficher les informations du concept
            System.out.println("Concept #" + (count + 1));
            System.out.println("Extension (instances) : " + extent);

            System.out.print("Intention (attributs-valeurs) : [");
            int i = 0;
            for (Map.Entry<String, String> pair : intent) {
                if (i > 0) System.out.print(", ");
                System.out.print(pair.getKey() + "=" + pair.getValue());
                i++;
            }
            System.out.println("]");

            System.out.println("Attribut pertinent : " + pertinentAttribute);
            System.out.println("Valeur pertinente : " + pertinentValue);
            System.out.println();

            count++;
        }

        System.out.println("========================================\n");
        firstConceptsDisplayed = true;
    }

    /**
     * Affiche les détails de la sélection d'attributs et valeurs selon la variante choisie.
     * Pour les variantes CpNC_COMV et CaNC_COMV, affiche simplement les attributs et leurs valeurs.
     * Pour les variantes CpNC_CORV et CaNC_CORV, affiche les attributs et leurs valeurs avec leur gain d'information.
     * Ces informations ne sont affichées qu'une seule fois.
     */
    private void printSelectionDetails() {
        // Ne pas afficher les informations si elles ont déjà été affichées
        if (selectionDetailsDisplayed) {
            return;
        }

        // Traitement spécifique selon la variante
        switch (currentVariant) {
            case CpNC_COMV:
                System.out.println("\n=== Calcul du gain d'information ===");

                // Afficher les scores d'information pour tous les attributs
                Map<String, Double> attributeScores = getAttributeScores();
                for (Map.Entry<String, Double> entry : attributeScores.entrySet()) {
                    System.out.println("Gain d'information pour l'attribut '" + entry.getKey() + "' : "
                            + String.format("%.2f", entry.getValue()));
                }

                System.out.println();
                printAttributesWithAllValues();
                break;
            case CaNC_COMV:
                // Pour CaNC_COMV, on n'affiche pas les gains d'information
                printAttributesWithAllValues();
                break;
            case CpNC_CORV:
                System.out.println("\n=== Calcul du gain d'information ===");

                // Afficher les scores d'information pour tous les attributs
                Map<String, Double> attributeScoresCpNC = getAttributeScores();
                for (Map.Entry<String, Double> entry : attributeScoresCpNC.entrySet()) {
                    System.out.println("Gain d'information pour l'attribut '" + entry.getKey() + "' : "
                            + String.format("%.2f", entry.getValue()));
                }

                System.out.println();

                // Pour CpNC_CORV, on affiche l'attribut sélectionné avec son score
                String pertinentAttribute = getMostPertinentAttribute();
                Double score = attributeScoresCpNC.get(pertinentAttribute);
                System.out.println("Attribut sélectionné : " + pertinentAttribute +
                        " (Gain d'information : " + String.format("%.2f", score) + ")");

                System.out.println("\n=== Calcul du gain d'information pour chaque valeur pertinente ===");
                printValueDetails(pertinentAttribute);
                break;
            case CaNC_CORV:
                // Pour CaNC_CORV, on n'affiche pas les gains d'information pour les attributs
                // mais on affiche les valeurs pertinentes pour chaque attribut
                System.out.println("\n=== Calcul du gain d'information pour chaque valeur pertinente ===");
                for (String attribute : getAttributeScores().keySet()) {
                    printValueDetails(attribute);
                }
                break;
        }

        System.out.println("\n=== Affichage des concepts ===");

        // Marquer que les détails ont été affichés
        selectionDetailsDisplayed = true;
    }

    /**
     * Affiche les attributs avec toutes leurs valeurs possibles pour les variantes CpNC_COMV et CaNC_COMV.
     */
    private void printAttributesWithAllValues() {
        if (currentVariant == Variant.CpNC_COMV) {
            // Pour CpNC_COMV, on affiche seulement l'attribut pertinent et ses valeurs
            String pertinentAttribute = getMostPertinentAttribute();
            Map<String, Set<Integer>> valuesMap = context.getDeltaIndex().get(pertinentAttribute);

            if (valuesMap != null && !valuesMap.isEmpty()) {
                System.out.println("Attribut pertinent sélectionné : " + pertinentAttribute);
                System.out.print("Valeurs pertinentes pour " + pertinentAttribute + " : [");

                int i = 0;
                for (String value : valuesMap.keySet()) {
                    if (i > 0) System.out.print(", ");
                    System.out.print(value);
                    i++;
                }
                System.out.println("] <-- selected");
            }
        }
        // Pour CaNC_COMV, on n'affiche plus la liste complète des attributs et valeurs
        // comme demandé dans la modification
    }

    /**
     * Affiche les détails des valeurs pertinentes pour un attribut donné avec leur gain d'information.
     *
     * @param attribute L'attribut pour lequel afficher les valeurs pertinentes
     */
    private void printValueDetails(String attribute) {
        // Obtenir les valeurs possibles pour cet attribut
        Map<String, Set<Integer>> valuesMap = context.getDeltaIndex().get(attribute);
        if (valuesMap == null || valuesMap.isEmpty()) return;

        // Calculer le gain d'information pour chaque valeur
        Map<String, Double> valueScores = new HashMap<>();
        for (String value : valuesMap.keySet()) {
            // Pour simplifier, on utilise la taille relative de l'extension comme mesure du gain
            // Dans une implémentation réelle, un calcul plus sophistiqué serait utilisé
            Set<Integer> extent = closureOperator.delta(attribute, value);
            if (extent != null && !extent.isEmpty()) {
                // Normaliser par rapport au nombre total d'instances
                double score = (double) extent.size() / context.getNumInstances();
                valueScores.put(value, score);
            }
        }

        // Afficher le gain d'information pour chaque valeur
        for (Map.Entry<String, Double> entry : valueScores.entrySet()) {
            System.out.println("Valeur '" + entry.getKey() + "' : " +
                    String.format("%.2f", entry.getValue()));
        }

        // Si on est dans une variante qui utilise la valeur pertinente
        if (currentVariant == Variant.CpNC_CORV || currentVariant == Variant.CaNC_CORV) {
            String relevantValue = getRelevantValue(attribute);
            Double score = valueScores.get(relevantValue);
            if (relevantValue != null && score != null) {
                System.out.println("Valeur pertinente sélectionnée pour " + attribute + " : " +
                        relevantValue + " (Gain d'information : " + String.format("%.2f", score) + ")");
            }
        }
    }
}
