package com.lamsili.canc.app;

import com.lamsili.canc.classifier.CANCLearnerMOA;
import com.lamsili.canc.fca.closure.ClosureOperator;
import com.lamsili.canc.fca.concept.FormalConcept;
import com.lamsili.canc.fca.context.NominalContext;

import com.lamsili.canc.rules.Rule;
import com.lamsili.canc.varriants.Variant;
import moa.core.Example;
import moa.core.TimingUtils;
import moa.evaluation.BasicClassificationPerformanceEvaluator;
import moa.streams.InstanceStream;
import moa.streams.ArffFileStream;
import com.yahoo.labs.samoa.instances.*;

import java.text.DecimalFormat;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TestCANCStream - Classe pour tester le classifieur CANC en apprentissage
 * sur un flux de données provenant d'un fichier ARFF.
 */
public class TestCANCStream {

    // Configuration par défaut
    private static final String DEFAULT_DATASET_PATH = "C:\\Users\\lamsilibil\\IdeaProjects\\Canc_MOA\\src\\main\\resources\\weather.nominal.arff";
    private static final int DEFAULT_CLASS_INDEX = -1;  // -1 signifie dernier attribut
    private static final int DEFAULT_INSTANCES = 14;
    private static final boolean DEFAULT_ENABLE_RESET = false;
    private static final Variant DEFAULT_VARIANT = Variant.CpNC_CORV;

    // Taille du chunk représente un "mini-dataset" sur lequel le modèle est appliqué
    private static final int DEFAULT_CHUNK_SIZE = 14;

    // Règles disjointes: si true, génère autant de règles que d'attributs pour chaque concept
    private static final boolean USE_DISJOINT_RULES = false;

    // Options de contrôle d'affichage
    private static boolean SHOW_FINAL_RESULTS = false;

    // Structure pour stocker les concepts par chunk
    private static Map<Integer, String> conceptDescriptionsByChunk = new HashMap<>();
    // Structure pour stocker les règles par chunk
    private static Map<Integer, List<Rule>> rulesByChunk = new HashMap<>();

    // Méthode d'évaluation à utiliser (INFORMATION_GAIN ou GAIN_RATIO)
    private static final com.lamsili.canc.fca.closure.ClosureOperator.AttributeEvalMethod EVAL_METHOD =
        ClosureOperator.AttributeEvalMethod.INFORMATION_GAIN;
        // Pour passer au gain ratio, changez simplement la ligne ci-dessus en:
        // com.lamsili.canc.fca.closure.ClosureOperator.AttributeEvalMethod.GAIN_RATIO;

    // Méthode d'évaluation des valeurs à utiliser (ENTROPY ou SUPPORT)
    private static final com.lamsili.canc.fca.closure.ClosureOperator.ValueEvalMethod VALUE_EVAL_METHOD =
        ClosureOperator.ValueEvalMethod.SUPPORT;
        // Pour utiliser le support (nombre d'occurrences), changez la ligne ci-dessus en:
        // ClosureOperator.ValueEvalMethod.SUPPORT;

    // Format des nombres décimaux avec 4 décimales après la virgule
    private static final DecimalFormat df = new DecimalFormat("0.0000");

    static {
        // Forcer l'utilisation du point comme séparateur décimal
        df.setDecimalFormatSymbols(new java.text.DecimalFormatSymbols(java.util.Locale.US));
    }

    public static void main(String[] args) {
        // Activer le débogage - mettre à true pour voir les détails complets
        CANCDebugger.setDebugEnabled(true);

        // Définir la méthode d'évaluation d'attribut à utiliser (gain d'information ou gain ratio)
        CANCDebugger.setAttributeEvalMethod(EVAL_METHOD);

        // Définir la méthode d'évaluation des valeurs à utiliser (entropy ou support)
        if (VALUE_EVAL_METHOD != null) {
            CANCDebugger.setValueEvalMethod(VALUE_EVAL_METHOD);
        }

        // Définir le mode de génération des règles (disjointes ou non)
        CANCDebugger.setUseDisjointRules(USE_DISJOINT_RULES);

        // Utiliser les paramètres par défaut
        int numInstances = DEFAULT_INSTANCES;
        int chunkSize = DEFAULT_CHUNK_SIZE;

        // Informer le debugger que nous utilisons le mode chunk
        CANCDebugger.setChunksMode(true);

        // Afficher des informations sur la configuration de débogage
        if (CANCDebugger.isDebugEnabled()) {
            System.out.println("\n=== CONFIGURATION DU DÉBOGAGE ===");
            System.out.println("Mode debug: ACTIVÉ");
            System.out.println("Méthode d'évaluation d'attributs: " + EVAL_METHOD);
            System.out.println("Méthode d'évaluation des valeurs: " + VALUE_EVAL_METHOD);
            System.out.println("Règles disjointes: " + USE_DISJOINT_RULES);
            System.out.println("Variante par défaut: " + DEFAULT_VARIANT);
            System.out.println("Mode chunks activé: OUI");
        }

        System.out.println("\n=== PRÉPARATION DES DONNÉES ===");
        System.out.println("Chargement du fichier: " + DEFAULT_DATASET_PATH);
        System.out.println("Mode de traitement: CHUNK");

        // Charger le flux de données à partir du fichier ARFF
        ArffFileStream dataStream = new ArffFileStream(DEFAULT_DATASET_PATH, DEFAULT_CLASS_INDEX);
        dataStream.prepareForUse();

        System.out.println("\n=== EXÉCUTION DE L'APPRENTISSAGE ===");

        // Réinitialiser les collections pour les concepts et les règles
        conceptDescriptionsByChunk.clear();
        rulesByChunk.clear();

        // Exécuter l'apprentissage en mode chunk
        runChunkEvaluation(dataStream, DEFAULT_VARIANT, numInstances,
                        DEFAULT_ENABLE_RESET, chunkSize);
    }

    /**
     * Exécute une évaluation par chunks (mini-batches) sur un flux de données
     * Cette version traite chaque chunk de manière complètement indépendante
     */
    private static void runChunkEvaluation(InstanceStream stream, Variant variant, int maxInstances, boolean enableReset, int chunkSize) {

        boolean isDebugEnabled = CANCDebugger.isDebugEnabled();

        if (isDebugEnabled) {
            System.out.println("\n=== DÉMARRAGE DU TEST EN MODE CHUNK (INDÉPENDANT) ===");
            System.out.println("Source: Fichier ARFF");
            System.out.println("Variante CANC: " + variant);
            System.out.println("Instances à traiter: " + maxInstances);
            System.out.println("Taille de chunk: " + chunkSize + " instances par chunk");
            System.out.println("Méthode d'évaluation: " + CANCDebugger.getAttributeEvalMethod());
            System.out.println("=========================================\n");
        } else {
            System.out.println("\n=== APPRENTISSAGE EN MODE CHUNK ===");
            System.out.println("Variante: " + variant + ", Instances: " + maxInstances + ", Taille de chunk: " + chunkSize);
        }

        // Variables de suivi
        int instancesProcessed = 0;
        long startTime = TimingUtils.getNanoCPUTimeOfCurrentThread();
        int chunkNumber = 0;
        int totalConcepts = 0;
        int totalRules = 0;

        // Structure pour stocker les instances du chunk courant
        HashMap<Integer, Example<Instance>> currentChunk = new HashMap<>();

        // Évaluateur global (pour mesurer la précision sur tous les chunks)
        BasicClassificationPerformanceEvaluator globalEvaluator = new BasicClassificationPerformanceEvaluator();

        // Initialisation du flux
        stream.restart();

        // En-tête pour les résultats intermédiaires (uniquement en mode debug)
        if (isDebugEnabled) {
            System.out.println("Chunk\tInstances\tPrécision\tTemps(s)\tNbConc.\tNbRègles");
            System.out.println("-----\t---------\t---------\t--------\t-------\t--------");
        }

        // Boucle principale d'apprentissage - traitement par chunks
        while (stream.hasMoreInstances() && instancesProcessed < maxInstances) {
            // Collecter les instances pour le chunk courant (mini-dataset)
            currentChunk.clear();
            int chunkInstanceCount = 0;

            while (chunkInstanceCount < chunkSize && stream.hasMoreInstances() && instancesProcessed < maxInstances) {
                Example<Instance> example = stream.nextInstance();
                currentChunk.put(chunkInstanceCount, example);
                chunkInstanceCount++;
                instancesProcessed++;
            }

            chunkNumber++;

            if (isDebugEnabled) {
                System.out.println("\n=== TRAITEMENT DU CHUNK " + chunkNumber + " ===");
                System.out.println("Nombre d'instances dans ce chunk: " + chunkInstanceCount);
            }

            // Créer un NOUVEAU classifieur pour chaque chunk
            // Cela garantit que chaque chunk est traité de manière totalement indépendante
            CANCLearnerMOA classifier = new CANCLearnerMOA();
            classifier.resetLearningImpl();

            // Configurer la variante
            switch (variant) {
                case CpNC_COMV:
                    classifier.variantOption.setChosenIndex(0);
                    break;
                case CpNC_CORV:
                    classifier.variantOption.setChosenIndex(1);
                    break;
                case CaNC_COMV:
                    classifier.variantOption.setChosenIndex(2);
                    break;
                case CaNC_CORV:
                    classifier.variantOption.setChosenIndex(3);
                    break;
            }

            // Activer l'affichage des concepts
            classifier.setDisplayConcepts(isDebugEnabled);

            // Réinitialiser le modèle pour ce chunk
            classifier.setModelContext(stream.getHeader());
            classifier.prepareForUse();

            // Réinitialiser le flag pour afficher le gain d'information pour ce chunk
            classifier.resetSelectionDetailsFlag();

            // PHASE 1: Entrainement sur toutes les instances du chunk

           /* // Afficher les instances de ce chunk avant le traitement
            System.out.println("\n=== INSTANCES DU CHUNK " + chunkNumber + " ===");
            for (Map.Entry<Integer, Example<Instance>> entry : currentChunk.entrySet()) {
                Instance instance = entry.getValue().getData();
                System.out.println("Instance " + entry.getKey() + ": " + instance.toString());
            }*/

            for (Map.Entry<Integer, Example<Instance>> entry : currentChunk.entrySet()) {
                Instance instance = entry.getValue().getData();
                classifier.trainOnInstance(instance);
            }

            // Forcer la génération du modèle
            try {
                java.lang.reflect.Method buildModelMethod =
                    CANCLearnerMOA.class.getDeclaredMethod("buildModel");
                buildModelMethod.setAccessible(true);
                buildModelMethod.invoke(classifier);

                // Afficher les gains d'information après la génération du modèle
                // comme demandé, seulement si le debug est activé
                if (isDebugEnabled) {
                    // Utiliser CANCDebugger au lieu de l'appel direct à la méthode
                    CANCDebugger.printInformationGainDetails(classifier);

                    // Afficher explicitement les détails de sélection des attributs et valeurs selon la variante
                    System.out.println("\n=== DÉTAILS DE SÉLECTION D'ATTRIBUTS ET VALEURS POUR " + variant + " ===");
                    CANCDebugger.printSelectionDetails(classifier);

                    // Réinitialiser le flag après l'affichage pour permettre d'afficher les détails pour le prochain chunk
                    CANCDebugger.resetSelectionDetailsFlag();
                }
            } catch (Exception e) {
                System.err.println("Erreur lors de la construction du modèle: " + e.getMessage());
            }

            // PHASE 2: Test sur les mêmes instances du chunk
            for (Map.Entry<Integer, Example<Instance>> entry : currentChunk.entrySet()) {
                Example<Instance> example = entry.getValue();
                double[] votes = classifier.getVotesForInstance(example.getData());
                globalEvaluator.addResult(example, votes);
            }

            // Récupérer la description des concepts
            String conceptsDescription = CANCDebugger.getConceptsDescription(
                classifier.getConcepts(),
                classifier.getNominalContext(),
                variant,
                classifier.getMostPertinentAttribute(),
                new ClosureOperator(classifier.getNominalContext())
            );
            List<Rule> currentRules = new ArrayList<>(classifier.getRules());

            // Obtenir une description des concepts
            int numConcepts = classifier.getNumberOfConcepts();

            // Stocker la description des concepts et les règles du chunk courant
            conceptDescriptionsByChunk.put(chunkNumber, conceptsDescription);
            rulesByChunk.put(chunkNumber, currentRules);
            totalConcepts += numConcepts;
            totalRules += currentRules.size();

            // Afficher les détails du chunk (uniquement en mode debug)
            if (isDebugEnabled) {
                // Afficher chaque concept du chunk courant avec ses règles
                System.out.println("\n--- CONCEPTS ET RÈGLES DU CHUNK " + chunkNumber + " ---");
                List<FormalConcept> concepts = classifier.getConcepts();
                NominalContext context = classifier.getNominalContext();
                com.lamsili.canc.rules.RuleExtractor extractor = new com.lamsili.canc.rules.RuleExtractor();
                Map<FormalConcept, List<Rule>> conceptRules = extractor.extractRulesByConcept(concepts, context, CANCDebugger.isUseDisjointRules());
                int idx = 1;
                for (Map.Entry<FormalConcept, List<Rule>> entry : conceptRules.entrySet()) {
                    FormalConcept concept = entry.getKey();
                    List<Rule> rules = entry.getValue();
                    System.out.println("Concept #" + idx + " :");
                    System.out.println("  Intent : " + concept.getIntent());
                    System.out.println("  Extent : " + concept.getExtent());
                    for (Rule rule : rules) {
                        System.out.println("    Règle : " + rule);
                    }
                    idx++;
                }

                // Afficher les statistiques intermédiaires par chunk
                double accuracy = globalEvaluator.getFractionCorrectlyClassified();
                double elapsedTime = TimingUtils.nanoTimeToSeconds(TimingUtils.getNanoCPUTimeOfCurrentThread() - startTime);

                System.out.println(chunkNumber + "\t" + instancesProcessed + "\t" + df.format(accuracy) + "\t" +
                                 df.format(elapsedTime) + "\t" + numConcepts + "\t" + currentRules.size());
            } else if (chunkNumber % 5 == 0 || chunkNumber == 1) {
                // Afficher uniquement les informations de progression en mode non-debug
            }
        }

        // Afficher les résultats finaux
        // Toujours afficher les résultats, indépendamment de SHOW_FINAL_RESULTS
        System.out.println("\n=== RÉSULTATS FINAUX (MODE CHUNK) ===");
        System.out.println("Chunks traités: " + chunkNumber);
        System.out.println("Instances traitées: " + instancesProcessed);
        System.out.println("Précision globale: " + df.format(globalEvaluator.getFractionCorrectlyClassified()));
        System.out.println("Nombre total de concepts générés: " + totalConcepts);
        System.out.println("Nombre total de règles générées: " + totalRules);

        double elapsedTimeSeconds = TimingUtils.nanoTimeToSeconds(TimingUtils.getNanoCPUTimeOfCurrentThread() - startTime);
        System.out.println("Temps total d'exécution: " + df.format(elapsedTimeSeconds) + " secondes");
        System.out.println("Vitesse moyenne: " + df.format(instancesProcessed / elapsedTimeSeconds) + " instances/seconde");

        // Affichage détaillé des concepts et règles uniquement en mode debug
        if (isDebugEnabled) {
            // Afficher tous les concepts générés par chunk
            System.out.println("\n=== TOUS LES CONCEPTS PAR CHUNK ===");
            for (int i = 1; i <= chunkNumber; i++) {
                String concepts = conceptDescriptionsByChunk.get(i);
                System.out.println("\n--- CONCEPTS DU CHUNK " + i + " ---");
                System.out.println(concepts);
            }
        }

        // Toujours afficher les règles générées, car c'est le résultat principal
        System.out.println("\n=== TOUTES LES RÈGLES GÉNÉRÉES PAR CHUNK ===");
        for (int i = 1; i <= chunkNumber; i++) {
            List<Rule> rules = rulesByChunk.get(i);
            System.out.println("\n--- RÈGLES DU CHUNK " + i + " (" + rules.size() + " règles) ---");
            displayRules(rules);
        }
    }

    /**
     * Affiche les détails des règles d'association
     * @param rules Liste des règles à afficher
     */
    private static void displayRules(List<Rule> rules) {
        if (rules.isEmpty()) {
            System.out.println("Aucune règle générée.");
            return;
        }

        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            // Afficher uniquement la règle formatée (toString() inclut déjà support, confidence et weight)
            System.out.println("Règle " + (i+1) + ": " + rule.toString());
            // La ligne redondante avec support et confiance a été supprimée
        }
    }
}
