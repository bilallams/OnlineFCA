package com.lamsili.canc.app;

import com.lamsili.canc.classifier.CANCLearnerMOA;
import com.lamsili.canc.varriants.Variant;

import moa.classifiers.Classifier;
import moa.core.TimingUtils;
import moa.core.Example;
import moa.core.Measurement;
import moa.evaluation.BasicClassificationPerformanceEvaluator;
import moa.streams.ArffFileStream;
import moa.streams.InstanceStream;
import moa.streams.generators.RandomRBFGenerator;
import moa.streams.generators.RandomTreeGenerator;
import moa.streams.generators.SEAGenerator;
import com.yahoo.labs.samoa.instances.Instance;

import java.io.File;
import java.util.Scanner;

/**
 * Classe principale pour tester le classifieur CANCLearnerMOA sur différents types de flux de données.
 */
public class Main {

    public static void main(String[] args) {
        // Activer ou désactiver le débogage
        System.out.println("Voulez-vous activer le mode débogage détaillé ? (o/n)");
        Scanner scanner = new Scanner(System.in);
        String debugChoice = scanner.nextLine().toLowerCase();
        boolean debugEnabled = debugChoice.equals("o") || debugChoice.equals("oui");
        CANCDebugger.setDebugEnabled(debugEnabled);

        // Menu principal
        while (true) {
            System.out.println("\n=== SYSTÈME DE TEST CANC ===");
            System.out.println("1. Tester sur le jeu de données météo (weather.nominal.arff)");
            System.out.println("2. Tester sur un générateur de flux aléatoire RandomRBF");
            System.out.println("3. Tester sur un générateur de flux aléatoire RandomTree");
            System.out.println("4. Tester sur un générateur de flux SEA");
            System.out.println("5. Changer le statut du débogage (actuellement: " + (debugEnabled ? "ACTIVÉ" : "DÉSACTIVÉ") + ")");
            System.out.println("6. Quitter");
            System.out.print("Choix: ");

            int choice;
            try {
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) {
                    System.out.println("Aucune entrée détectée. Veuillez choisir une option.");
                    continue;
                }
                choice = Integer.parseInt(input);
                if (choice < 1 || choice > 6) {
                    System.out.println("Option invalide. Veuillez entrer un nombre entre 1 et 6.");
                    continue;
                }
            } catch (NumberFormatException e) {
                System.out.println("Erreur: Veuillez entrer un nombre valide.");
                continue;
            }

            try {
                switch (choice) {
                    case 1:
                        testWeatherDataset();
                        break;
                    case 2:
                        testRandomRBF(scanner);
                        break;
                    case 3:
                        testRandomTree(scanner);
                        break;
                    case 4:
                        testSEA(scanner);
                        break;
                    case 5:
                        debugEnabled = !debugEnabled;
                        CANCDebugger.setDebugEnabled(debugEnabled);
                        System.out.println("Mode débogage " + (debugEnabled ? "ACTIVÉ" : "DÉSACTIVÉ"));
                        break;
                    case 6:
                        System.out.println("Au revoir!");
                        scanner.close();
                        return;
                }
            } catch (Exception e) {
                System.out.println("Une erreur s'est produite: " + e.getMessage());
                e.printStackTrace();
                System.out.println("Appuyez sur Entrée pour continuer...");
                scanner.nextLine();
            }
        }
    }

    /**
     * Teste le classifieur sur le jeu de données météo
     */
    private static void testWeatherDataset() {
        String datasetPath = "src/main/resources/weather.nominal.arff";
        File dataFile = new File(datasetPath);
        if (!dataFile.exists()) {
            System.out.println("ERREUR: Fichier non trouvé: " + datasetPath);
            System.out.println("Veuillez placer un fichier ARFF valide à l'emplacement spécifié.");
            return;
        }

        // Définir le flux de données
        ArffFileStream stream = new ArffFileStream(datasetPath, -1);
        runTest(stream, "Weather Dataset", 0, Variant.CpNC_COMV, 100);
    }

    /**
     * Teste le classifieur sur un générateur RandomRBF
     */
    private static void testRandomRBF(Scanner scanner) {
        System.out.print("Nombre d'attributs (par défaut 10): ");
        int numAtts = getIntInput(scanner, 10);

        System.out.print("Nombre de classes (par défaut 2): ");
        int numClasses = getIntInput(scanner, 2);

        System.out.print("Nombre d'instances à générer (par défaut 1000): ");
        int numInstances = getIntInput(scanner, 1000);

        System.out.println("Choisissez la variante CANC:");
        printVariantOptions();
        int variantChoice = getIntInput(scanner, 0);
        Variant variant = getVariantFromChoice(variantChoice);

        // Créer le générateur
        RandomRBFGenerator generator = new RandomRBFGenerator();
        generator.numAttsOption.setValue(numAtts);
        generator.numClassesOption.setValue(numClasses);

        runTest(generator, "RandomRBF Generator", 10, variant, numInstances);
    }

    /**
     * Teste le classifieur sur un générateur RandomTree
     */
    private static void testRandomTree(Scanner scanner) {
        System.out.print("Nombre d'attributs (par défaut 10): ");
        int numAtts = getIntInput(scanner, 10);

        System.out.print("Nombre de classes (par défaut 2): ");
        int numClasses = getIntInput(scanner, 2);

        System.out.print("Nombre d'instances à générer (par défaut 1000): ");
        int numInstances = getIntInput(scanner, 1000);

        System.out.println("Choisissez la variante CANC:");
        printVariantOptions();
        int variantChoice = getIntInput(scanner, 0);
        Variant variant = getVariantFromChoice(variantChoice);

        // Créer le générateur
        RandomTreeGenerator generator = new RandomTreeGenerator();
        // Utiliser les méthodes getOptions() et setValueViaCLIString() en remplacement des attributs directs
        try {
            generator.getOptions().setViaCLIString("numAtts " + String.valueOf(numAtts));
            generator.getOptions().setViaCLIString("numClasses " + String.valueOf(numClasses));
        } catch (Exception e) {
            System.out.println("Erreur lors de la configuration du générateur: " + e.getMessage());
        }

        runTest(generator, "RandomTree Generator", 10, variant, numInstances);
    }

    /**
     * Teste le classifieur sur un générateur SEA
     */
    private static void testSEA(Scanner scanner) {
        System.out.print("Nombre d'instances à générer (par défaut 1000): ");
        int numInstances = getIntInput(scanner, 1000);

        System.out.println("Choisissez la variante CANC:");
        printVariantOptions();
        int variantChoice = getIntInput(scanner, 0);
        Variant variant = getVariantFromChoice(variantChoice);

        // Créer le générateur (SEA a des valeurs fixes: 3 attributs et 2 classes)
        SEAGenerator generator = new SEAGenerator();

        runTest(generator, "SEA Generator", 10, variant, numInstances);
    }

    /**
     * Fonction principale qui exécute un test sur un flux donné
     */
    private static void runTest(InstanceStream stream, String streamName,
                               int gracePeriod, Variant variant, int maxInstances) {
        // Créer le classifieur
        CANCLearnerMOA classifier = new CANCLearnerMOA();

        // Configurer les options du classifieur
        classifier.gracePeriodOption.setValue(gracePeriod);
        classifier.variantOption.setChosenIndex(variant.ordinal());

        // Créer l'évaluateur
        BasicClassificationPerformanceEvaluator evaluator = new BasicClassificationPerformanceEvaluator();

        // Variables pour le suivi du temps
        int instancesProcessed = 0;
        int monitorFrequency = Math.min(100, maxInstances / 10);
        long evaluateStartTime = TimingUtils.getNanoCPUTimeOfCurrentThread();

        // Initialiser le stream et le classifieur
        stream.restart();
        classifier.setModelContext(stream.getHeader());
        classifier.prepareForUse();

        CANCDebugger.printTimestampedMessage("Démarrage du test sur " + streamName);
        System.out.println("--- Test du classifieur CANCLearnerMOA ---");
        System.out.println("Dataset: " + streamName);
        System.out.println("Variante: " + variant);
        System.out.println("Période de grâce: " + gracePeriod);
        System.out.println("Mode débogage: " + (CANCDebugger.isDebugEnabled() ? "ACTIVÉ" : "DÉSACTIVÉ"));
        System.out.println();
        System.out.println("Instance\tPrécision\tTemps (s)");

        // Boucle principale d'évaluation
        while (stream.hasMoreInstances() && instancesProcessed < maxInstances) {
            Example<Instance> example = stream.nextInstance();
            Instance inst = example.getData();

            // Tester avant d'entraîner
            double[] prediction = classifier.getVotesForInstance(inst);
            evaluator.addResult(example, prediction);

            // Entraîner le classifieur
            classifier.trainOnInstance(inst);
            instancesProcessed++;

            // Mesurer la performance périodiquement
            if (instancesProcessed % monitorFrequency == 0) {
                double accuracy = evaluator.getFractionCorrectlyClassified();
                double time = TimingUtils.nanoTimeToSeconds(
                        TimingUtils.getNanoCPUTimeOfCurrentThread() - evaluateStartTime);

                System.out.printf("%d\t\t%.4f\t\t%.2f%n",
                        instancesProcessed, accuracy, time);
            }
        }

        System.out.println();
        System.out.println("--- Résultats finaux ---");
        System.out.println("Instances traitées: " + instancesProcessed);
        System.out.println("Précision finale: " + evaluator.getFractionCorrectlyClassified());

        // Si le débogage est activé, afficher les informations détaillées du modèle
        if (CANCDebugger.isDebugEnabled()) {
            CANCDebugger.printSectionSeparator("DESCRIPTION DU MODÈLE");
            StringBuilder modelDescription = new StringBuilder();
            classifier.getModelDescription(modelDescription, 0);
            System.out.println(modelDescription.toString());

            // Afficher des statistiques supplémentaires via CANCDebugger
            CANCDebugger.printSectionSeparator("STATISTIQUES DU MODÈLE");
            System.out.println("Nombre de concepts générés: " + classifier.getNumberOfConcepts());
            System.out.println("Nombre de règles: " + classifier.getRules().size());
        } else {
            // Afficher juste un résumé du modèle
            System.out.println();
            System.out.println("--- Résumé du modèle ---");
            System.out.println("Nombre de règles: " + classifier.getRules().size());
        }
    }

    /**
     * Utilitaire pour afficher les options de variantes
     */
    private static void printVariantOptions() {
        System.out.println("0. CpNC_COMV (Pertinent Attribute - All Values) [par défaut]");
        System.out.println("1. CpNC_CORV (Pertinent Attribute - Relevant Value)");
        System.out.println("2. CaNC_COMV (All Attributes - All Values)");
        System.out.println("3. CaNC_CORV (All Attributes - Relevant Value)");
    }

    /**
     * Utilitaire pour obtenir la variante correspondant au choix
     */
    private static Variant getVariantFromChoice(int choice) {
        switch (choice) {
            case 0: return Variant.CpNC_COMV;
            case 1: return Variant.CpNC_CORV;
            case 2: return Variant.CaNC_COMV;
            case 3: return Variant.CaNC_CORV;
            default: return Variant.CpNC_COMV; // par défaut
        }
    }

    /**
     * Utilitaire pour récupérer une entrée entière avec valeur par défaut
     */
    private static int getIntInput(Scanner scanner, int defaultValue) {
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            System.out.println("Entrée invalide, utilisation de la valeur par défaut: " + defaultValue);
            return defaultValue;
        }
    }
}
