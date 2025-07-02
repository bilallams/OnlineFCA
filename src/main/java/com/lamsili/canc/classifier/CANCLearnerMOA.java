package com.lamsili.canc.classifier;

import com.lamsili.canc.fca.closure.ClosureOperator;
import com.lamsili.canc.fca.closure.ClosureOperator.AttributeEvalMethod;
import com.lamsili.canc.fca.closure.ClosureOperator.ValueEvalMethod;
import com.lamsili.canc.fca.concept.FormalConcept;
import com.lamsili.canc.fca.context.NominalContext;
import com.lamsili.canc.rules.Rule;
import com.lamsili.canc.rules.RuleExtractor;
import com.lamsili.canc.varriants.NCACoupleSelector;
import com.lamsili.canc.varriants.Variant;
import com.lamsili.canc.app.CANCDebugger;

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

    private static final long serialVersionUID = 1546L;

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

    // Nouvelles options migrées de TestCANCStream
    public FlagOption useDisjointRulesOption = new FlagOption("useDisjointRules", 'd',
            "Activer la génération de règles disjointes");

    public FlagOption debugOption = new FlagOption("debug", 'b',
            "Activer le mode débogage pour afficher des informations détaillées");

    public MultiChoiceOption attributeEvalOption = new MultiChoiceOption("attributeEvalMethod", 'a',
            "Méthode d'évaluation des attributs",
            new String[]{"INFORMATION_GAIN", "GAIN_RATIO"},
            new String[]{
                "Gain d'information: mesure la réduction d'entropie",
                "Gain ratio: gain normalisé pour éviter le biais vers les attributs à valeurs multiples"
            },
            0);

    public MultiChoiceOption valueEvalOption = new MultiChoiceOption("valueEvalMethod", 'e',
            "Méthode d'évaluation des valeurs d'attributs",
            new String[]{"ENTROPY", "SUPPORT"},
            new String[]{
                "Entropie: mesure le désordre/l'information",
                "Support: mesure la fréquence d'apparition"
            },
            0);

    // Variables de l'algorithme
    private NominalContext context;
    private ClosureOperator closureOperator;
    private NCACoupleSelector coupleSelector;
    private List<Rule> rules;
    private RuleExtractor ruleExtractor;
    private Variant currentVariant;

    // Variable pour stocker la description des concepts (évite les affichages redondants)
    private String conceptsDescription = "";

    // Liste pour conserver les 10 premiers concepts générés
    private List<FormalConcept> firstConcepts = new ArrayList<>();

    // Statistiques
    private int instancesSeen;
    private int lastModelBuildSize;
    private int conceptsGenerated;
    private int rulesGenerated;

    // Variable pour contrôler l'affichage des concepts (éviter les doublons)
    private boolean displayConcepts = true;

    // Liste pour conserver les concepts générés du chunk courant
    private List<FormalConcept> currentConcepts = new ArrayList<>();

    /**
     * Réinitialise le flag pour permettre l'affichage des détails de sélection
     * Cela permet d'afficher à nouveau les informations de gain pour chaque chunk
     */
    public void resetSelectionDetailsFlag() {
        // Déléguer à la méthode statique dans CANCDebugger
        com.lamsili.canc.app.CANCDebugger.resetSelectionDetailsFlag();
    }

    @Override
    public void resetLearningImpl() {
        // Configurer CANCDebugger avec les options de MOA
        com.lamsili.canc.app.CANCDebugger.setDebugEnabled(debugOption.isSet());
        com.lamsili.canc.app.CANCDebugger.setUseDisjointRules(useDisjointRulesOption.isSet());

        // Configurer la méthode d'évaluation des attributs
        if (attributeEvalOption.getChosenIndex() == 0) {
            com.lamsili.canc.app.CANCDebugger.setAttributeEvalMethod(AttributeEvalMethod.INFORMATION_GAIN);
        } else {
            com.lamsili.canc.app.CANCDebugger.setAttributeEvalMethod(AttributeEvalMethod.GAIN_RATIO);
        }

        // Configurer la méthode d'évaluation des valeurs
        if (valueEvalOption.getChosenIndex() == 0) {
            com.lamsili.canc.app.CANCDebugger.setValueEvalMethod(ValueEvalMethod.ENTROPY);
        } else {
            com.lamsili.canc.app.CANCDebugger.setValueEvalMethod(ValueEvalMethod.SUPPORT);
        }

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
        fcaTrain();
    }

    /**
     * Méthode de formation FCA qui génère les concepts formels et extrait les règles.
     * Cette méthode contient la logique principale de construction du modèle.
     */
    private void fcaTrain() {
        // Réinitialiser le cache de debug pour ce nouveau chunk
        com.lamsili.canc.app.CANCDebugger.resetDebugCache(instancesSeen);

        // Réinitialiser le flag des détails de sélection pour permettre leur affichage
        resetSelectionDetailsFlag();

        // Afficher les détails de sélection d'attributs et valeurs avant la génération des concepts
        if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
            com.lamsili.canc.app.CANCDebugger.handleSelectionDetails(this);
        }

        // Générer les concepts formels
        List<FormalConcept> concepts = generateConcepts();
        conceptsGenerated = concepts.size();
        // Stocker la liste complète des concepts du chunk courant
        currentConcepts = concepts;

        // Stocker les 10 premiers concepts s'ils n'ont pas déjà été sauvegardés
        if (firstConcepts.isEmpty() && !concepts.isEmpty()) {
            // On limite à 10 concepts maximum
            int numToAdd = Math.min(concepts.size(), 10);
            firstConcepts.addAll(concepts.subList(0, numToAdd));
        }

        // Préparation des données de concepts pour l'affichage (à la place de l'affichage direct)
        if (displayConcepts && com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
            // Construire une description de concepts au lieu d'afficher directement
            StringBuilder conceptsDescription = new StringBuilder();
            if (concepts != null && !concepts.isEmpty()) {
                // Afficher l'entête pour les concepts du chunk courant
                conceptsDescription.append("--- CONCEPTS ET RÈGLES DU CHUNK ").append(instancesSeen / gracePeriodOption.getValue()).append(" ---\n");

                for (int i = 0; i < concepts.size(); i++) {
                    FormalConcept concept = concepts.get(i);
                    conceptsDescription.append("Concept #").append(i + 1).append(" :\n");
                    conceptsDescription.append("  Intent : ").append(concept.getIntent()).append("\n");
                    conceptsDescription.append("  Extent : ").append(concept.getExtent()).append("\n");

                    // On détermine l'attribut pertinent et sa valeur selon la variante
                    if (currentVariant == Variant.CpNC_COMV || currentVariant == Variant.CpNC_CORV) {
                        String pertinentAttribute = getMostPertinentAttribute();
                        conceptsDescription.append("  Attribut pertinent : ").append(pertinentAttribute).append("\n");

                        // Pour les valeurs pertinentes
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
                            // Pour CpNC_CORV, la valeur pertinente est celle déterminée par l'opérateur de fermeture
                            conceptsDescription.append("  Valeur pertinente : ").append(getRelevantValue(pertinentAttribute)).append("\n");
                        }
                    }
                    conceptsDescription.append("\n");
                }
            }
            // Stockage de la description pour accès externe sans double affichage
            this.conceptsDescription = conceptsDescription.toString();

            // Afficher directement la description des concepts si le debug est activé
            if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
                System.out.println(conceptsDescription.toString());
            }
        }

        // Mettre à jour le dernier point de construction du modèle
        lastModelBuildSize = instancesSeen;

        // Utiliser RuleExtractor pour extraire les règles à partir des concepts
        // Transmettre le paramètre useDisjointRules depuis CANCDebugger
        this.rules = ruleExtractor.extractRules(concepts, context,
                    com.lamsili.canc.app.CANCDebugger.isUseDisjointRules());
        this.rulesGenerated = rules.size();

        // Calculer les métriques (support, confiance) pour les règles
        calculateRuleMetrics();

        // Debug: afficher les règles si le debug est activé
        if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled() && !rules.isEmpty()) {
            StringBuilder rulesDescription = new StringBuilder();
            for (int i = 0; i < rules.size(); i++) {
                Rule rule = rules.get(i);
                // Utiliser directement toString() qui inclut déjà les métriques complètes
                rulesDescription.append("Règle ").append(i + 1).append(": ")
                               .append(rule.toString())
                               .append("\n");

                // Les métriques (support, confidence, weight) sont déjà incluses dans rule.toString()
                // Ne pas les ajouter à nouveau pour éviter la duplication
            }

            // On ne fait pas d'affichage direct, on utilise le CANCDebugger pour éviter les duplications
            //com.lamsili.canc.app.CANCDebugger.printRules(rulesDescription.toString());
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

        // Utiliser la méthode handleSelectionDetails de CANCDebugger qui gère tout le processus
        // d'affichage des détails de sélection et la gestion du flag
        com.lamsili.canc.app.CANCDebugger.handleSelectionDetails(this);

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

            // Calculer la fermeture de Galois de l'extension
            Set<Integer> closedExtent = closureOperator.galoisClosure(extent);

            // Si l'extension n'est pas égale à sa fermeture, elle n'est pas fermée
            if (!extent.equals(closedExtent)) continue;

            // Éviter les duplications en utilisant TreeSet pour garantir une comparaison par contenu
            TreeSet<Integer> sortedExtent = new TreeSet<>(closedExtent);
            if (generatedExtents.contains(sortedExtent)) continue;
            generatedExtents.add(sortedExtent);

            // Créer le concept en utilisant l'extension fermée et son intention associée
            Set<Map.Entry<String, String>> intent = closureOperator.phi(closedExtent);
            concepts.add(new FormalConcept(closedExtent, intent));
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

        // Vérification supplémentaire contre les ensembles vides
        if (extent == null || extent.isEmpty()) return concepts;

        // Calculer la fermeture de Galois de l'extension
        Set<Integer> closedExtent = closureOperator.galoisClosure(extent);

        // Si l'extension n'est pas égale à sa fermeture, elle n'est pas fermée
        if (!extent.equals(closedExtent)) return concepts;

        // Obtenir l'intention en utilisant phi sur l'extension fermée
        Set<Map.Entry<String, String>> intent = closureOperator.phi(closedExtent);

        // Créer le concept en utilisant l'extension fermée et son intention associée
        concepts.add(new FormalConcept(closedExtent, intent));
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

                // Vérification supplémentaire contre les ensembles vides
                if (extent == null || extent.isEmpty()) continue;

                // Calculer la fermeture de Galois de l'extension
                Set<Integer> closedExtent = closureOperator.galoisClosure(extent);

                // Si l'extension n'est pas égale à sa fermeture, elle n'est pas fermée
                if (!extent.equals(closedExtent)) continue;

                // Éviter les duplications avec l'extension fermée
                if (generatedExtents.contains(closedExtent)) continue;
                generatedExtents.add(closedExtent);

                // Créer le concept en utilisant l'extension fermée et son intention associée
                Set<Map.Entry<String, String>> intent = closureOperator.phi(closedExtent);
                concepts.add(new FormalConcept(closedExtent, intent));
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

                // Vérification supplémentaire contre les ensembles vides
                if (extent == null || extent.isEmpty()) continue;

                // Calculer la fermeture de Galois de l'extension
                Set<Integer> closedExtent = closureOperator.galoisClosure(extent);

                // Étant donné que galoisClosure garantit déjà la validité de la fermeture,
                // cette vérification devient redondante mais permet de filtrer les extensions non fermées
                if (!extent.equals(closedExtent)) continue;

                // Éviter les duplications avec l'extension fermée
                if (generatedExtents.contains(closedExtent)) continue;
                generatedExtents.add(closedExtent);

                // Créer le concept en utilisant l'extension fermée et son intention associée
                Set<Map.Entry<String, String>> intent = closureOperator.phi(closedExtent);
                concepts.add(new FormalConcept(closedExtent, intent));
            }
        }

        return concepts;
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
     * Contrôle si les concepts formels doivent être affichés lors de la construction du modèle
     *
     * @param display Si vrai, les concepts seront affichés
     */
    public void setDisplayConcepts(boolean display) {
        this.displayConcepts = display;
    }
    /**
     * Retourne la liste des concepts générés pour le chunk courant.
     */
    public List<FormalConcept> getConcepts() {
        return currentConcepts;
    }
}

