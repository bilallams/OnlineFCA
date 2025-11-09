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
    // La période de grâce a été supprimée, on traite les instances individuellement

    public IntOption gracePeriodOption = new IntOption("gracePeriod", 'g',
            "Nombre d'instances à attendre avant de construire le modèle (période de grâce)", 1750, 1, Integer.MAX_VALUE);

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


    public FlagOption useDisjointRulesOption = new FlagOption("useDisjointRules", 'd',
            "Activer la génération de règles disjointes");

    public FlagOption debugOption = new FlagOption("debug", 'b',
            "Activer le mode débogage pour afficher des informations détaillées");

    public FlagOption showPredictionsOption = new FlagOption("showPredictions", 'p',
            "Afficher les détails de prédiction pour chaque instance");

    public MultiChoiceOption attributeEvalOption = new MultiChoiceOption("attributeEvalMethod", 'a',
            "Méthode d'évaluation des attributs",
            new String[]{"INFORMATION_GAIN", "GAIN_RATIO"},
            new String[]{
                "Gain d'information: mesure la réduction d'entropie",
                "Gain ratio: gain normalisé pour éviter le biais vers les attributs à valeurs multiples"
            },
            1);

    public MultiChoiceOption valueEvalOption = new MultiChoiceOption("valueEvalMethod", 'e',
            "Méthode d'évaluation des valeurs d'attributs",
            new String[]{"ENTROPY", "SUPPORT"},
            new String[]{
                "Entropie: mesure le désordre/l'information",
                "Support: mesure la fréquence d'apparition"
            },
            1);

    // Nouveau: option pour contrôler la taille de l'échantillon après rejet
    public IntOption fixedSampleSizeOption = new IntOption("fixedSampleSize", 's',
            "Taille fixe d'échantillon après rejet (0 = aléatoire)", 50, 0, Integer.MAX_VALUE);

    // Variables de l'algorithme
    private NominalContext context;
    private ClosureOperator closureOperator;
    private NCACoupleSelector coupleSelector;
    private List<Rule> rules;
    private RuleExtractor ruleExtractor;
    private Variant currentVariant;
    private String cachedPertinentAttribute; // Stockage de l'attribut pertinent pour éviter les recalculs
    // Nouveau: mode restreint après rejet
    private boolean restrictPertinentAttributeToRejectedValue = false;
    private Instance lastRejectedInstance = null;
    private String lastRejectedValue = null; // valeur de l'attribut pertinent dans l'instance rejetée

    // Variable pour stocker la description des concepts (évite les affichages redondants)
    private String conceptsDescription = "";

    // Statistiques
    private int instancesSeen;
    private int lastModelBuildSize;
    private int conceptsGenerated;
    private int rulesGenerated;

    // Variable pour contrôler l'affichage des concepts (éviter les doublons)
    private boolean displayConcepts = true;

    // Liste pour conserver les concepts générés du chunk courant
    private List<FormalConcept> currentConcepts = new ArrayList<>();

    // Liste pour conserver tous les concepts générés entre les reconstructions du modèle
    private List<FormalConcept> allConcepts = new ArrayList<>();

    // Variable pour suivre le nombre de prédictions effectuées (pour l'affichage)
    private int predictionCounter = 0;

    // Liste pour stocker les résultats des prédictions (pour le débogage)
    private List<PredictionResult> predictionResults = new ArrayList<>();

    // Ajout d'une variable pour suivre si le premier modèle a été construit
    private boolean firstModelBuilt = false;

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
        com.lamsili.canc.app.CANCDebugger.setShowPredictions(showPredictionsOption.isSet());

        // Afficher la date au début du débogage (une seule fois au lancement du modèle)
        if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
            java.util.Date currentDate = new java.util.Date();
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String dateStr = dateFormat.format(currentDate);

            com.lamsili.canc.app.CANCDebugger.printDebugSection("DATE" , dateStr);
        }

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

        // Initialiser le contexte
        this.context = new NominalContext();

        this.instancesSeen = 0;
        this.lastModelBuildSize = 0;
        this.conceptsGenerated = 0;
        this.rulesGenerated = 0;
        // Initialiser la liste des règles une seule fois au démarrage
        if (this.rules == null) {
            this.rules = new ArrayList<>();
        }
        // Le reste reste inchangé
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

        // Définir l'ordre des attributs pour l'affichage des règles
        Rule.setAttributeOrder(context);

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

        // Si nous sommes avant ou à la période de grâce
        if (instancesSeen <= gracePeriodOption.getValue()) {
            // Pendant la période de grâce, le poids est toujours 1/GP (pas 1/n)
            double fixedWeight = 1.0 / gracePeriodOption.getValue();

            // Appliquer le poids fixe 1/GP à toutes les instances
            for (int i = 0; i < context.getNumInstances(); i++) {
                context.setInstanceWeight(i, fixedWeight);
            }

            // Affichage pour suivi des poids pendant la période de grâce
            if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
                System.out.println("[POIDS] Instance #" + instancesSeen + " (période de grâce) - Poids fixe: " + fixedWeight);
            }
        } else {
            // Après la période de grâce

            // Donner un poids initial de 1.0 à la nouvelle instance
            int newInstanceIndex = context.getNumInstances() - 1;
            context.setInstanceWeight(newInstanceIndex, 1.0);

            // La normalisation locale des poids a été supprimée
            // Les nouvelles instances reçoivent simplement un poids de 1.0
            // La normalisation globale sera effectuée plus tard dans buildModel

            // Affichage pour suivi des poids après la période de grâce
            if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
                System.out.println("[POIDS] Nouvelle instance #" + instancesSeen + " - Poids initial: 1.0");
            }
        }

        // Initialiser le closure operator si nécessaire
        if (closureOperator == null) {
            closureOperator = new ClosureOperator(context);
        }

        // Initialiser le couple selector si nécessaire
        if (coupleSelector == null) {
            coupleSelector = new NCACoupleSelector(context);
        }

        // Vérifier si on doit reconstruire le modèle en fonction de la période de grâce
        if (!firstModelBuilt) {
            // Pour le premier modèle, respecter la période de grâce
            if (instancesSeen >= gracePeriodOption.getValue()) {
                // On a atteint la période de grâce, construire le premier modèle
                buildModel();
                firstModelBuilt = true;  // Marquer que le premier modèle a été construit
            }
            // Sinon, ne rien faire pour toutes les instances avant la période de grâce
        } else {
            // Après le premier modèle, nous devons d'abord vérifier si l'instance est correctement classifiée
            // avant de l'ajouter au concept et de mettre à jour les règles
            
            // On sauvegarde l'instance actuelle pour la vérification
            Instance currentInstance = inst;
            
            // Obtenir la classe réelle de l'instance
            String actualClass = currentInstance.classAttribute().value((int) currentInstance.classValue());
            
            // Faire une prédiction pour cette instance
            double[] votes = getVotesForInstance(currentInstance);

            // Déterminer la classe prédite (index du vote le plus élevé)
            int predictedClassIndex = Utils.maxIndex(votes);
            String predictedClass = currentInstance.classAttribute().value(predictedClassIndex);
            
            // Vérifier si la prédiction est correcte
            boolean isCorrectPrediction = actualClass.equals(predictedClass);
            
            // S'assurer que nous avons au moins un résultat de prédiction
            if (predictionResults.isEmpty()) {
                if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
                    System.out.println("[ALERTE] Liste de prédictions vide pour l'instance #" + instancesSeen + " - Réexécution de la prédiction");
                }
            }

            // Récupérer le dernier résultat de prédiction qui contient déjà les règles applicables
            PredictionResult result = predictionResults.get(predictionResults.size() - 1);
            List<Rule> applicableRules = result.applicableRules;

            // Vérifier d'abord si c'est un cas de rejet (aucune règle applicable)
            boolean isRejected = applicableRules.isEmpty();

            if (isRejected) {
                // Cas où l'instance est rejetée (aucune règle applicable)
                if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
                    System.out.println("\u001B[36m [INSTANCE REJETÉE] Instance #" + instancesSeen + " rejetée - Aucune règle applicable - Échantillonnage et reconstruction du modèle CANC\u001B[0m");
                }

                // Activer le mode restreint pour la prochaine génération de concepts (CpNC_COMV uniquement)
                this.restrictPertinentAttributeToRejectedValue = true;
                this.lastRejectedInstance = currentInstance; // conserver l'instance rejetée
                this.lastRejectedValue = null; // sera calculée lors de generateCpNC_COMV

                // Donner un poids de 1.0 à l'instance actuelle (rejetée)
                int currentInstanceIndex = context.getNumInstances() - 1;
                context.setInstanceWeight(currentInstanceIndex, 1.0);

                if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
                    System.out.println("\u001B[36m [POIDS REJET] Poids de l'instance rejetée #" + instancesSeen + " fixé à 1.0 \u001B[0m");
                }

                // Échantillonnage avant la reconstruction du modèle
                sampleInstancesAndBuildModel();
            } else {
                // Cas où des règles sont applicables (prédiction correcte ou incorrecte)
                if (isCorrectPrediction) {
                    if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
                        System.out.println("\u001B[35m[AJOUT INSTANCE] Instance #" + instancesSeen + " correctement classifiée - Optimisation\u001B[0m");
                    }
                    // Réduction du poids de 50% (passer de 1.0 à 0.5) pour une instance bien classée
                    int currentInstanceIndex = context.getNumInstances() - 1;
                    double oldW = context.getInstanceWeight(currentInstanceIndex);
                    context.setInstanceWeight(currentInstanceIndex, oldW * 0.5);
                    if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
                        System.out.println("[POIDS] Instance #" + instancesSeen + " bien classifiée - poids réduit de " + oldW + " à " + (oldW * 0.5));
                    }
                } else {
                    // Cas où l'instance est mal classifiée mais des règles s'appliquent
                    if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
                        System.out.println("\u001B[33m [INSTANCE mal classifiée] Instance #" + instancesSeen + " mal classifiée - " + applicableRules.size() + " règles applicables mais prédiction incorrecte\u001B[0m");
                    }
                }

                // Traitement commun pour les cas correct et incorrect: mise à jour des concepts
                // Ajouter l'instance aux concepts associés aux règles applicables
                // et mettre à jour les classes prédites et les métriques des règles
                List<FormalConcept> modifiedConcepts = new ArrayList<>();
                List<Rule> modifiedRules = new ArrayList<>();
                int updatedConcepts = ruleExtractor.updateConceptsWithNewInstance(
                    allConcepts,
                    currentInstance,
                    context.getNumInstances() - 1,
                    this.rules, // utiliser toutes les règles pour la mise à jour généralisée
                    context,
                    modifiedConcepts,
                    modifiedRules
                );

                if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
                    String prefix = isCorrectPrediction ? "[OPTIMISATION] " : "[OPTIMISATION - INSTANCE MAL CLASSIFIÉE] ";
                    System.out.println(prefix + applicableRules.size() + " règles applicables, " +
                                      updatedConcepts + " concepts modifiés sans reconstruction");
                    if (updatedConcepts > 0) {
                        // Construire une description concise uniquement des concepts modifiés
                        StringBuilder sbConcepts = new StringBuilder();
                        sbConcepts.append("--- CONCEPTS MODIFIÉS (" + updatedConcepts + ") ---\n");
                        for (int ci = 0; ci < modifiedConcepts.size(); ci++) {
                            FormalConcept c = modifiedConcepts.get(ci);
                            int globalIdx = allConcepts.indexOf(c) + 1; // numéro réel dans la table complète (1-based)
                            sbConcepts.append("Concept modifié #").append(globalIdx).append(" :\n");
                            sbConcepts.append("  Intent : ").append(c.getIntent()).append("\n");
                            // Extent affiché avec indices +1
                            sbConcepts.append("  Extent : [");
                            boolean firstIdx = true;
                            for (Integer idx : c.getExtent()) {
                                if (!firstIdx) sbConcepts.append(", ");
                                sbConcepts.append(idx + 1);
                                firstIdx = false;
                            }
                            sbConcepts.append("]\n\n");
                        }
                        com.lamsili.canc.app.CANCDebugger.printDebugSection(
                            isCorrectPrediction ? "CONCEPTS MODIFIÉS (INSTANCE BIEN CLASSIFIÉE)" : "CONCEPTS MODIFIÉS (INSTANCE MAL CLASSIFIÉE)",
                            sbConcepts.toString()
                        );
                    }
                    if (!modifiedRules.isEmpty()) {
                        String rulesDescription = com.lamsili.canc.app.CANCDebugger.generateRulesDescription(modifiedRules);
                        com.lamsili.canc.app.CANCDebugger.printDebugSection(
                            isCorrectPrediction ? "RÈGLES MODIFIÉES (INSTANCE BIEN CLASSIFIÉE)" : "RÈGLES MODIFIÉES (INSTANCE MAL CLASSIFIÉE)",
                            rulesDescription
                        );
                    }
                }
            }
        }
    }

    /**
     * Construit le modèle en générant les concepts formels et en extrayant les règles.
     */
    private void buildModel() {
        // Initialisation et préparation du débogage
        com.lamsili.canc.app.CANCDebugger.resetDebugCache(instancesSeen);
        resetSelectionDetailsFlag();

        // Affichage de débogage (date et instances)
        if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
            // Afficher la date de recalcul
            java.util.Date currentDate = new java.util.Date();
            String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(currentDate);
            System.out.println("\u001B[31m[RECALCUL CANC] Date: " + dateStr + "\u001B[0m");

            // Afficher les instances traitées
            com.lamsili.canc.app.CANCDebugger.printProcessedInstances(context);
        }

        // Réinitialiser la liste des résultats de prédiction
        predictionResults.clear();

        // Construction du modèle principal
        fcaTrain();
        lastModelBuildSize = instancesSeen;

        // Affichage des concepts générés
        if (displayConcepts && com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
            String description = com.lamsili.canc.app.CANCDebugger.getConceptsDescription(
                currentConcepts, context, currentVariant, cachedPertinentAttribute,
                closureOperator, ruleExtractor, instancesSeen
            );
            this.conceptsDescription = description;
            com.lamsili.canc.app.CANCDebugger.printDebugSection("CONCEPTS GÉNÉRÉS", description);
        }

        // Affichage des règles extraites
        if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled() && rules != null && !rules.isEmpty()) {
            String rulesDescription = com.lamsili.canc.app.CANCDebugger.generateRulesDescription(rules);
            com.lamsili.canc.app.CANCDebugger.printDebugSection("RÈGLES EXTRAITES", rulesDescription);
        }

        // Gestion des poids et affichage des prédictions
        boolean showPredictions = com.lamsili.canc.app.CANCDebugger.isShowPredictions() &&
                                 rules != null && !rules.isEmpty();

        // Faire une prédiction pour chaque instance du contexte actuel
        if (showPredictions) {
            // Vider la liste des résultats de prédiction pour éviter les doublons
            predictionResults.clear();

            // Créer un tableau pour stocker les résultats dans l'ordre des instances
            PredictionResult[] orderedResults = new PredictionResult[context.getNumInstances()];

            // Faire les prédictions pour toutes les instances
            for (int i = 0; i < context.getNumInstances(); i++) {
                Instance instance = context.getInstance(i);

                // Obtenir la classe réelle
                String actualClassName = context.getInstanceClass(i);

                // Obtenir les votes pour cette instance
                double[] votes = fcPredict(instance);

                // Déterminer la classe prédite
                int predictedClassIndex = Utils.maxIndex(votes);
                String predictedClassName = instance.attribute(instance.classIndex()).value(predictedClassIndex);

                // Créer un nouvel objet PredictionResult pour cette instance
                predictionCounter++;
                PredictionResult result = new PredictionResult(predictionCounter, actualClassName, predictedClassName);
                result.classifier = this;

                // Stocker les votes
                result.votes = Arrays.copyOf(votes, votes.length);

                // Stocker le poids actuel (avant ajustement)
                result.weightBefore = context.getInstanceWeight(i);

                // Identifier les règles applicables
                result.applicableRules = new ArrayList<>();
                for (Rule rule : rules) {
                    if (rule.appliesTo(instance)) {
                        result.applicableRules.add(rule);
                    }
                }

                // Stocker le résultat dans le tableau ordonné
                orderedResults[i] = result;
                
                // Déterminer si l'instance est correctement classifiée
                boolean isCorrectlyClassified = actualClassName.equals(predictedClassName);
                
                // Ajuster le poids directement (remplace adjustInstanceWeight)
                double currentWeight = context.getInstanceWeight(i);
                double newWeight = isCorrectlyClassified ? currentWeight * 0.5 : currentWeight * 1.5;
                context.setInstanceWeight(i, newWeight);
            }

            // Maintenant, stocker les poids après ajustement dans les résultats
            for (int i = 0; i < context.getNumInstances(); i++) {
                // Mettre à jour le poids after avec le poids actuel après ajustement
                orderedResults[i].weightAfter = context.getInstanceWeight(i);
                
                // Ajouter à la liste des résultats
                predictionResults.add(orderedResults[i]);
            }

            // Affichage des résultats de prédiction avec les poids ajustés mais avant normalisation
            com.lamsili.canc.app.CANCDebugger.displayPredictionResults(predictionResults, currentVariant);
        } else {
            // Ajuster les poids sans afficher les prédictions (remplace adjustWeightsBasedOnPredictions)
            for (int i = 0; i < context.getNumInstances(); i++) {
                Instance instance = context.getInstance(i);
                String actualClassName = context.getInstanceClass(i);
                double[] votes = fcPredict(instance);
                int predictedClassIndex = Utils.maxIndex(votes);
                String predictedClassName = instance.attribute(instance.classIndex()).value(predictedClassIndex);
                boolean isCorrectlyClassified = actualClassName.equals(predictedClassName);
                double currentWeight = context.getInstanceWeight(i);
                double newWeight = isCorrectlyClassified ? currentWeight * 0.5 : currentWeight * 1.5;
                context.setInstanceWeight(i, newWeight);
            }
        }

        // Normalisation des poids après l'ajustement et l'affichage des résultats
        context.normalizeWeights();

        // Affichage des poids normalisés
        if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
            System.out.println("\u001B[32m [POIDS] Normalisation effectuée - Somme des poids: " +
                              context.getTotalWeight() + " \u001B[0m");
        }
    }

    /**
     * Méthode de formation FCA qui génère les concepts formels et extrait les règles.
     * Cette méthode contient la logique principale de construction du modèle.
     */
    private void fcaTrain() {
        // Générer les concepts formels
        List<FormalConcept> concepts = generateConcepts();
        conceptsGenerated = concepts.size();
        // Stocker la liste complète des concepts
        currentConcepts = concepts;

        // Ajouter les nouveaux concepts à la liste de tous les concepts
        allConcepts.addAll(concepts);

        // Mettre à jour le compteur de concepts pour inclure tous les concepts générés
        conceptsGenerated = allConcepts.size();

        // Mettre à jour le dernier point de construction du modèle
        lastModelBuildSize = instancesSeen;

        // Utiliser RuleExtractor pour extraire les règles à partir des concepts
        // Transmettre le paramètre useDisjointRules depuis CANCDebugger
        List<Rule> newRules = ruleExtractor.extractRules(concepts, context,
                    com.lamsili.canc.app.CANCDebugger.isUseDisjointRules());

        // Calculer les métriques (support, confiance) uniquement pour les nouvelles règles
        if (newRules != null && !newRules.isEmpty()) {
            ruleExtractor.calculateRuleMetrics(newRules, context);

            // Ajouter toutes les règles sans vérifier les duplications
            this.rules.addAll(newRules);
            this.rulesGenerated = rules.size();
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
        String pertinentAttribute = coupleSelector.getMostPertinentAttribute();
        this.cachedPertinentAttribute = pertinentAttribute;
        if (pertinentAttribute == null) return concepts;
        Map<String, Set<Integer>> valuesMap = context.getDeltaIndex().get(pertinentAttribute);
        if (valuesMap == null || valuesMap.isEmpty()) return concepts;

        // Mode restreint: ne générer qu'un seul concept pour la valeur de l'instance rejetée
        if (restrictPertinentAttributeToRejectedValue) {
            String targetValue = null;
            if (lastRejectedValue != null) {
                targetValue = lastRejectedValue;
            } else if (lastRejectedInstance != null) {
                for (int i = 0; i < lastRejectedInstance.numAttributes(); i++) {
                    if (i == lastRejectedInstance.classIndex()) continue;
                    if (lastRejectedInstance.attribute(i).name().equals(pertinentAttribute)) {
                        targetValue = lastRejectedInstance.attribute(i).isNominal() ?
                                lastRejectedInstance.attribute(i).value((int) lastRejectedInstance.value(i)) :
                                Double.toString(lastRejectedInstance.value(i));
                        break;
                    }
                }
                lastRejectedValue = targetValue;
            }
            if (targetValue != null) {
                Set<Integer> extent = closureOperator.delta(pertinentAttribute, targetValue);
                if (extent != null && !extent.isEmpty()) {
                    Set<Integer> closedExtent = closureOperator.galoisClosure(extent);
                    if (closedExtent != null && !closedExtent.isEmpty()) {
                        Set<Map.Entry<String, String>> intent = closureOperator.phi(closedExtent);
                        concepts.add(new FormalConcept(closedExtent, intent));
                        if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
                            System.out.println("[MODE RESTREINT] Concept (unique) généré pour " + pertinentAttribute + "=" + targetValue);
                        }
                    }
                } else if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
                    System.out.println("[MODE RESTREINT] Aucune occurrence pour " + pertinentAttribute + "=" + targetValue + " (aucun concept)");
                }
            } else if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
                System.out.println("[MODE RESTREINT] Valeur cible introuvable pour l'attribut pertinent '" + pertinentAttribute + "'");
            }
            // Reset et retour immédiat (comportement single-value seulement ici)
            restrictPertinentAttributeToRejectedValue = false;
            lastRejectedInstance = null;
            return concepts;
        }

        // Comportement normal CpNC_COMV: générer un concept par valeur de l'attribut pertinent
        for (Map.Entry<String, Set<Integer>> valueEntry : valuesMap.entrySet()) {
            String value = valueEntry.getKey();
            if (value == null) continue;
            Set<Integer> extent = closureOperator.delta(pertinentAttribute, value);
            if (extent == null || extent.isEmpty()) continue;
            Set<Integer> closedExtent = closureOperator.galoisClosure(extent);
            if (closedExtent == null || closedExtent.isEmpty()) continue;
            if (!extent.equals(closedExtent)) continue; // on ne conserve que les fermés
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
        // Stocker l'attribut pertinent pour éviter de le recalculer
        this.cachedPertinentAttribute = pertinentAttribute;
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

        // Appeler fcPredict pour obtenir les votes (logique de prédiction pure)
        double[] votes = fcPredict(instance);

        // Déterminer la classe prédite (index du vote le plus élevé)
        int predictedClassIndex = Utils.maxIndex(votes);
        String predictedClassName = instance.attribute(instance.classIndex()).value(predictedClassIndex);

        // Obtenir la vraie classe de l'instance
        int actualClassIndex = (int) instance.classValue();
        String actualClassName = instance.attribute(instance.classIndex()).value(actualClassIndex);

        // Incrémenter le compteur de prédictions
        predictionCounter++;

        // Stocker les informations de prédiction pour le débogage
        PredictionResult result = new PredictionResult(predictionCounter, actualClassName, predictedClassName);
        result.classifier = this; // Ajout de la référence vers le classifieur courant


        // Ajouter le résultat à la liste des prédictions
        predictionResults.add(result);

        // Initialiser directement la liste des règles applicables dans l'objet result
        // Éviter la création d'une liste intermédiaire inutile
        result.applicableRules = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule.appliesTo(instance)) {
                result.applicableRules.add(rule);
            }
        }

        // Stocker une copie des votes
        result.votes = Arrays.copyOf(votes, votes.length);

        return votes;
    }

    /**
     * Prédit la classe pour une instance donnée en utilisant les règles générées.
     * Cette méthode contient uniquement la logique de prédiction pure.
     *
     * @param instance L'instance à classifier
     * @return Un tableau de votes pour chaque classe ou tableau avec valeurs spéciales pour indiquer un rejet
     */
    private double[] fcPredict(Instance instance) {
        // Distribution des votes par classe
        double[] votes = new double[instance.numClasses()];
        boolean ruleApplied = false;

        // Compter les votes de chaque règle applicable, pondérés par la confiance
        for (Rule rule : rules) {
            if (rule.appliesTo(instance)) {
                ruleApplied = true;
                // Trouver l'index de la classe prédite
                int predictedClassIndex = -1;
                for (int i = 0; i < instance.numClasses(); i++) {
                    if (instance.attribute(instance.classIndex()).value(i).equals(rule.getPredictedClass())) {
                        predictedClassIndex = i;
                        break;
                    }
                }

                if (predictedClassIndex >= 0) {
                    // Utiliser uniquement le poids de la règle pour le vote
                    // Le poids est déjà calculé comme support * confiance dans RuleExtractor
                    votes[predictedClassIndex] += rule.getWeight();
                }
            }
        }

        // Si aucune règle ne s'applique, vérifier si l'instance correspond à un concept existant
        if (!ruleApplied) {
            // Tous les votes sont à -1.0 pour indiquer le rejet
            for (int i = 0; i < votes.length; i++) {
                votes[i] = -1.0; // Valeur spéciale pour indiquer un rejet
            }

            // Pas besoin de normaliser car ce sont des valeurs spéciales
        } else {
            // Normaliser les votes (si nécessaire)
            if (Utils.sum(votes) > 0.0) {
                Utils.normalize(votes);
            }
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
     * @return Liste des régles
     */
    public List<Rule> getRules() {
        return rules;
    }

    /**
     * Obtenir la variante actuelle de l'algorithme
     * @return La variante actuelle utilisée
     */
    public Variant getCurrentVariant() {
        return this.currentVariant;
    }

    // Getter pour le mode restreint après rejet (utilisé par le debugger)
    public String getRejectedPertinentAttributeValueIfRestricted() {
        if (!restrictPertinentAttributeToRejectedValue) return null;
        if (lastRejectedInstance == null) return null;
         // Recalculer systématiquement l'attribut pertinent (ne pas utiliser le cache potentiellement obsolète)
        String attr = getMostPertinentAttribute();
        if (attr == null) return null;
        for (int i = 0; i < lastRejectedInstance.numAttributes(); i++) {
            if (i == lastRejectedInstance.classIndex()) continue;
            if (lastRejectedInstance.attribute(i).name().equals(attr)) {
                return lastRejectedInstance.attribute(i).isNominal() ?
                        lastRejectedInstance.attribute(i).value((int) lastRejectedInstance.value(i)) :
                        Double.toString(lastRejectedInstance.value(i));
            }
        }
        return null;
    }
    /**
     * Classe interne pour stocker les résultats de prédiction (pour le débogage)
     */
    public static class PredictionResult {
        public int predictionId;
        public String actualClass;
        public String predictedClass;
        public List<Rule> applicableRules;
        public double[] votes;
        public CANCLearnerMOA classifier; // Référence au classifieur qui a fait la prédiction
        public double weightBefore; // Poids de l'instance avant ajustement
        public double weightAfter; // Poids de l'instance après ajustement

        public PredictionResult(int predictionId, String actualClass, String predictedClass) {
            this.predictionId = predictionId;
            this.actualClass = actualClass;
            this.predictedClass = predictedClass;
        }
    }



    /**
     * Échantillonne les instances selon leurs poids et construit le modèle avec cet échantillon.
     * Les instances sont triées par poids décroissant, puis un pourcentage aléatoire est sélectionné.
     */
    private void sampleInstancesAndBuildModel() {
        // Nombre total d'instances
        int numInstances = context.getNumInstances();
        if (numInstances == 0) return;
        int previousConceptCount = allConcepts.size(); // sauvegarde pour numérotation continue

        // 1. Créer une liste de paires (index d'instance, poids)
        List<Map.Entry<Integer, Double>> weightedInstances = new ArrayList<>();
        for (int i = 0; i < numInstances; i++) {
            double weight = context.getInstanceWeight(i);
            weightedInstances.add(new AbstractMap.SimpleEntry<>(i, weight));
        }

        // 2. Trier les instances par poids décroissant
        weightedInstances.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        int samplesToKeep;
        double sampleRatio = -1.0; // indicateur non utilisé pour mode fixe
        if (fixedSampleSizeOption.getValue() > 0) {
            // Mode taille fixe
            samplesToKeep = Math.min(fixedSampleSizeOption.getValue(), numInstances);
            if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
                System.out.println("\u001B[36m[ÉCHANTILLONNAGE] Mode taille fixe: sélection des " + samplesToKeep +
                        " meilleures instances (poids max) sur " + numInstances + "\u001B[0m");
            }
        } else {
            // 3. Générer un pourcentage aléatoire entre 0% et 100% des instances (mode legacy)
            sampleRatio = Math.random();
            // 4. Calculer le nombre d'instances à sélectionner
            samplesToKeep = Math.max(1, (int) (numInstances * sampleRatio));
            if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
                System.out.println("\u001B[36m[ÉCHANTILLONNAGE] Mode aléatoire: sélection de " + samplesToKeep +
                        " instances sur " + numInstances + " (" + String.format("%.2f", sampleRatio * 100) + "%) pour reconstruire le modèle\u001B[0m");
            }
        }

        // 5. Créer un contexte temporaire avec les instances sélectionnées
        NominalContext sampledContext = new NominalContext();
        List<Integer> selectedOriginalIndices = new ArrayList<>(); // mapping index échantillon -> index original

        // 6. Conserver les instances sélectionnées pour la construction du modèle
        for (int i = 0; i < samplesToKeep && i < weightedInstances.size(); i++) {
            int instanceIndex = weightedInstances.get(i).getKey();
            Instance instance = context.getInstance(instanceIndex);
            selectedOriginalIndices.add(instanceIndex);
            sampledContext.addInstance(instance);
            sampledContext.setInstanceWeight(i, context.getInstanceWeight(instanceIndex));
        }

        if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
            System.out.println("\n=== INSTANCES SÉLECTIONNÉES (ÉCHANTILLON) ===");
            StringBuilder sbSample = new StringBuilder();
            sbSample.append("Instances traitées:\n");
            for (int i = 0; i < sampledContext.getNumInstances(); i++) {
                Instance instSel = sampledContext.getInstance(i);
                if (instSel == null) continue;
                StringBuilder line = new StringBuilder();
                for (int a = 0; a < instSel.numAttributes(); a++) {
                    if (a == instSel.classIndex()) continue;
                    if (line.length() > 0) line.append(",");
                    if (instSel.attribute(a).isNominal()) {
                        line.append(instSel.attribute(a).value((int) instSel.value(a)));
                    } else {
                        line.append(instSel.value(a));
                    }
                }
                // classe
                if (instSel.classAttribute().isNominal()) {
                    line.append(",").append(instSel.classAttribute().value((int) instSel.classValue()));
                } else {
                    line.append(",").append(instSel.classValue());
                }
                double w = sampledContext.getInstanceWeight(i);
                line.append(" | poids=").append(String.format(java.util.Locale.US, "%.4f", w));
                sbSample.append(line).append("\n");
            }
            System.out.print(sbSample.toString());
        }

        // 7. Sauvegarder temporairement le contexte actuel
        NominalContext originalContext = this.context;
        try {
            this.context = sampledContext;
            this.closureOperator = new ClosureOperator(sampledContext);
            this.coupleSelector = new NCACoupleSelector(sampledContext);
            buildModelStructureOnly();
            if (allConcepts.size() > previousConceptCount) {
                List<FormalConcept> newlyAdded = allConcepts.subList(previousConceptCount, allConcepts.size());
                for (FormalConcept fc : newlyAdded) {
                    fc.remapExtentIndices(selectedOriginalIndices);
                }
                if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
                    // Afficher maintenant TOUS les concepts (1..N) plutôt que seulement les nouveaux
                    String descAll = com.lamsili.canc.app.CANCDebugger.getConceptsDescription(
                            allConcepts,
                            originalContext,
                            currentVariant,
                            cachedPertinentAttribute,
                            this.closureOperator, // non utilisé dans génération détaillée
                            ruleExtractor,
                            instancesSeen
                    );
                    com.lamsili.canc.app.CANCDebugger.printDebugSection("CONCEPTS (APRÈS REJET)", descAll);
                }
                currentConcepts = newlyAdded; // conserve la liste des derniers ajoutés si nécessaire ailleurs
            }
            for (int i = 0; i < selectedOriginalIndices.size(); i++) {
                int originalIdx = selectedOriginalIndices.get(i);
                double propagatedWeight = sampledContext.getInstanceWeight(i);
                originalContext.setInstanceWeight(originalIdx, propagatedWeight);
            }
        } finally {
            this.context = originalContext;
            this.closureOperator = new ClosureOperator(originalContext);
            this.coupleSelector = new NCACoupleSelector(originalContext);
        }

        if (ruleExtractor != null && rules != null && !rules.isEmpty()) {
            ruleExtractor.calculateRuleMetrics(rules, context);
        }
        evaluateAllInstancesPredictions();
    }

    // Nouvelle méthode: construction du modèle (concepts + règles) sans phase de prédiction/ajustement
    private void buildModelStructureOnly() {
        com.lamsili.canc.app.CANCDebugger.resetDebugCache(instancesSeen);
        resetSelectionDetailsFlag();

        // Génération concepts + règles
        fcaTrain();
        lastModelBuildSize = instancesSeen;

        // Debug (concepts & règles) comme dans buildModel
        if (displayConcepts && com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
            String description = com.lamsili.canc.app.CANCDebugger.getConceptsDescription(
                currentConcepts, context, currentVariant, cachedPertinentAttribute,
                closureOperator, ruleExtractor, instancesSeen
            );
            this.conceptsDescription = description;
            com.lamsili.canc.app.CANCDebugger.printDebugSection("CONCEPTS GÉNÉRÉS Before", description);
        }
        if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled() && rules != null && !rules.isEmpty()) {
            String rulesDescription = com.lamsili.canc.app.CANCDebugger.generateRulesDescription(rules);
            com.lamsili.canc.app.CANCDebugger.printDebugSection("RÈGLES EXTRAITES After ", rulesDescription);
        }
    }

    // Nouvelle méthode: évaluer toutes les instances du contexte courant (après sampling) et ajuster les poids
    private void evaluateAllInstancesPredictions() {
        predictionResults.clear();
        if (rules == null || rules.isEmpty()) return;

        PredictionResult[] orderedResults = new PredictionResult[context.getNumInstances()];
        for (int i = 0; i < context.getNumInstances(); i++) {
            Instance instance = context.getInstance(i);
            String actualClassName = context.getInstanceClass(i);
            double[] votes = fcPredict(instance);
            int predictedClassIndex = Utils.maxIndex(votes);
            String predictedClassName = instance.attribute(instance.classIndex()).value(predictedClassIndex);
            predictionCounter++;
            PredictionResult result = new PredictionResult(predictionCounter, actualClassName, predictedClassName);
            result.classifier = this;
            result.votes = Arrays.copyOf(votes, votes.length);
            result.weightBefore = context.getInstanceWeight(i);
            result.applicableRules = new ArrayList<>();
            for (Rule rule : rules) {
                if (rule.appliesTo(instance)) {
                    result.applicableRules.add(rule);
                }
            }
            boolean isCorrectlyClassified = actualClassName.equals(predictedClassName);
            double currentWeight = context.getInstanceWeight(i);
            double newWeight = isCorrectlyClassified ? currentWeight * 0.5 : currentWeight * 1.5;
            context.setInstanceWeight(i, newWeight);
            orderedResults[i] = result;
        }
        for (int i = 0; i < context.getNumInstances(); i++) {
            orderedResults[i].weightAfter = context.getInstanceWeight(i);
            predictionResults.add(orderedResults[i]);
        }
        context.normalizeWeights();
        if (com.lamsili.canc.app.CANCDebugger.isShowPredictions()) {
            com.lamsili.canc.app.CANCDebugger.displayPredictionResults(predictionResults, currentVariant);
            if (com.lamsili.canc.app.CANCDebugger.isDebugEnabled()) {
                System.out.println("\u001B[32m [POIDS] Normalisation effectuée (post-évaluation complète) - Somme des poids: " + context.getTotalWeight() + " \u001B[0m");
            }
        }
    }
}
