package com.lamsili.canc.app;

import com.lamsili.canc.classifier.CANCLearnerMOA;
import com.lamsili.canc.rules.Rule;
import com.lamsili.canc.varriants.Variant;
import moa.streams.ArffFileStream;
import moa.streams.InstanceStream;
import moa.streams.generators.RandomRBFGenerator;
import moa.streams.generators.RandomTreeGenerator;
import moa.streams.generators.SEAGenerator;
import moa.core.TimingUtils;
import moa.evaluation.BasicClassificationPerformanceEvaluator;
import moa.core.Example;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;
import com.yahoo.labs.samoa.instances.InstancesHeader;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * Classe de test pour vérifier le fonctionnement d'une variante CaNC sur
 * le jeu de données météo nominal ou sur des flux générés.
 */
public class TestCANCVariants {

    // ========== CHOISIR LA VARIANTE À TESTER ICI ==========
    // Options: CpNC_COMV, CpNC_CORV, CaNC_COMV, CaNC_CORV
    private static final Variant VARIANT_TO_TEST = Variant.CpNC_COMV;
    // ====================================================

    // Format pour les valeurs décimales
    private static final DecimalFormat df = new DecimalFormat("0.0000");

    public static void main(String[] args) {
        // Initialiser le mode débogage - utiliser uniquement CANCDebugger
        CANCDebugger.setDebugEnabled(true);

        Scanner scanner = new Scanner(System.in);
        System.out.println("=== TEST DES VARIANTES CANC ===");

        // Menu de sélection du flux de données
        System.out.println("Choisissez le flux de données :");
        System.out.println("1. Jeu de données météo (weather.nominal.arff)");
        System.out.println("2. RandomRBF Generator");
        System.out.println("3. RandomTree Generator");
        System.out.println("4. SEA Generator");
        System.out.print("Votre choix (1-4) : ");

        int choixFlux = getIntInput(scanner, 1); // 1 par défaut (weather)

        // Menu de sélection de la variante
        System.out.println("\nChoisissez la variante à tester :");
        System.out.println("1. CpNC_COMV (Attribut Pertinent - Toutes Valeurs)");
        System.out.println("2. CpNC_CORV (Attribut Pertinent - Valeur Pertinente)");
        System.out.println("3. CaNC_COMV (Tous Attributs - Toutes Valeurs)");
        System.out.println("4. CaNC_CORV (Tous Attributs - Valeur Pertinente)");
        System.out.print("Votre choix (1-4) : ");

        int choixVariante = getIntInput(scanner, 1); // 1 par défaut (CpNC_COMV)
        Variant varianteChoisie = getVariantFromChoice(choixVariante);

        // Message de démarrage avec horodatage
        CANCDebugger.printTimestampedMessage("Démarrage du test de la variante " + varianteChoisie);

        // Affichage détaillé du mode de fonctionnement de la variante
        afficherDescriptionVariante(varianteChoisie);

        // Exécution du test selon le choix de flux
        switch (choixFlux) {
            case 1:
                testWeatherDataset(varianteChoisie);
                break;
            case 2:
                testRandomRBF(scanner, varianteChoisie);
                break;
            case 3:
                testRandomTree(scanner, varianteChoisie);
                break;
            case 4:
                testSEA(scanner, varianteChoisie);
                break;
            default:
                System.out.println("Choix invalide. Test sur weather.nominal.arff par défaut.");
                testWeatherDataset(varianteChoisie);
                break;
        }

        scanner.close();
    }

    /**
     * Affiche une description détaillée de la variante à tester
     */
    private static void afficherDescriptionVariante(Variant variant) {
        CANCDebugger.printDebugHeader("DESCRIPTION DE LA VARIANTE " + variant);

        String modeSelection = variant.toString().startsWith("CpNC") ?
                "Pertinent Attribute (Sélection d'un seul attribut le plus pertinent)" :
                "All Attributes (Utilisation de tous les attributs)";

        String modeValeur = variant.toString().endsWith("CORV") ?
                "Relevant Value (Sélection d'une seule valeur pertinente)" :
                "Multiple Values (Utilisation de toutes les valeurs)";

        System.out.println("\nMode de sélection d'attribut: " + modeSelection);
        System.out.println("Mode de sélection de valeur: " + modeValeur);

        // Afficher le processus seulement si le mode débogage est activé
        if (CANCDebugger.isDebugEnabled()) {
            System.out.println("\nProcessus de l'algorithme:");
            System.out.println("1. Chargement du jeu de données");
            System.out.println("2. Calcul de l'entropie pour chaque attribut");
            System.out.println("3. Sélection de l'attribut avec l'entropie minimale (attribut pertinent)");

            if (variant.toString().endsWith("CORV")) {
                System.out.println("4. Pour chaque attribut sélectionné, calcul du score de pertinence de chaque valeur");
                System.out.println("5. Sélection de la valeur avec le score le plus élevé pour chaque attribut");
                System.out.println("   (Score = fréquence * pureté de la classe)");
            } else {
                System.out.println("4. Utilisation de toutes les valeurs des attributs sélectionnés");
            }

            System.out.println("6. Calcul des fermetures de Galois pour générer les concepts formels");
            System.out.println("7. Extraction des règles à partir des concepts formels");
            System.out.println("8. Classification de nouvelles instances en utilisant les règles générées");
        }

        CANCDebugger.printDebugFooter();
    }

    /**
     * Teste la variante sélectionnée sur le jeu de données météo
     */
    private static void testWeatherDataset(Variant variant) {
        // Vérifier si le fichier ARFF existe
        String datasetPath = "src/main/resources/weather.nominal.arff";
        File dataFile = new File(datasetPath);
        if (!dataFile.exists()) {
            System.out.println("ERREUR: Fichier non trouvé: " + datasetPath);
            System.out.println("Veuillez placer un fichier ARFF valide à l'emplacement spécifié.");
            return;
        }

        // Charger le jeu de données météo
        ArffFileStream stream = new ArffFileStream(datasetPath, -1);
        stream.prepareForUse();

        System.out.println("Test de la variante " + variant + " sur le jeu de données météo");
        System.out.println("==================================================\n");

        // Collecter toutes les instances dans une liste pour faciliter le test
        Instances dataset = new Instances(stream.getHeader());
        int loadedInstances = 0;
        while (stream.hasMoreInstances()) {
            Instance inst = stream.nextInstance().getData();
            dataset.add(inst);
            loadedInstances++;
        }

        System.out.println("Jeu de données chargé: " + dataset.numInstances() + " instances, " +
                          (dataset.numAttributes() - 1) + " attributs");

        // Vérification supplémentaire du nombre d'instances
        if (dataset.numInstances() != 14) {
            System.out.println("ATTENTION: Le nombre d'instances chargées (" + dataset.numInstances() +
                               ") ne correspond pas au nombre attendu (14).");
            System.out.println("Instances comptées pendant le chargement: " + loadedInstances);
        }

        // Tester la variante sélectionnée avec LOO
        testVariantLOO(dataset, variant);
    }

    /**
     * Teste la variante sélectionnée avec la validation croisée LOO
     */
    private static void testVariantLOO(Instances dataset, Variant variant) {
        // Pour un jeu de données aussi petit que weather.nominal, nous utilisons
        // une validation croisée LOO (Leave-One-Out)
        int nbInstances = dataset.numInstances();

        System.out.println("Utilisation de la validation LOO (Leave-One-Out) pour évaluation");
        System.out.println("-------------------------------------------------------------");

        System.out.println("\nTest de la variante: " + variant);
        System.out.println("-------------------------");

        // Initialisation du débogage avancé
        CANCDebugger.printRuleExtractorInit(variant);

        int correct = 0;
        CANCLearnerMOA finalClassifier = null;

        // Pour chaque instance
        for (int testIndex = 0; testIndex < nbInstances; testIndex++) {
            // Créer et configurer le classifieur
            CANCLearnerMOA classifier = new CANCLearnerMOA();

            // Définir la variante à utiliser selon la constante définie
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

            // Configurer la période de grâce à 0 pour ce petit jeu de données
            classifier.gracePeriodOption.setValue(0);

            // Initialiser le classifieur
            classifier.setModelContext(new InstancesHeader(dataset));
            classifier.prepareForUse();

            // Séparer l'ensemble d'entraînement et de test
            Instance testInstance = dataset.instance(testIndex);

            // Entrainement sur toutes les instances sauf celle de test
            for (int i = 0; i < nbInstances; i++) {
                if (i != testIndex) {
                    classifier.trainOnInstance(dataset.instance(i));
                }
            }

            // Test sur l'instance laissée de côté
            double trueClass = testInstance.classValue();
            double[] votes = classifier.getVotesForInstance(testInstance);
            double predictedClass = getIndexOfMax(votes);

            // Vérifier si la prédiction est correcte
            if (predictedClass == trueClass) {
                correct++;
            }

            // Conserver le dernier classifieur pour l'analyse finale
            if (testIndex == nbInstances - 1) {
                finalClassifier = classifier;
            }
        }

        // Afficher les résultats
        double accuracy = (double) correct / nbInstances * 100;
        System.out.println("Précision: " + String.format("%.2f%%", accuracy) +
                          " (" + correct + " / " + nbInstances + ")");

        // Si nous avons un classifieur final, analyser en détail
        if (finalClassifier != null) {
            analyzeClassifierInDetail(finalClassifier, dataset);
        }
    }

    /**
     * Analyse en détail le comportement du classifieur sur le jeu de données
     */
    private static void analyzeClassifierInDetail(CANCLearnerMOA classifier, Instances dataset) {
        // Récupérer les scores d'information des attributs
        Map<String, Double> attributeScores = classifier.getAttributeScores();

        // Les scores des attributs sont maintenant gérés par CANCDebugger
        // et nous n'affichons plus cette section ici

        // Afficher les concepts formels générés de manière concise
        System.out.println("\n=== CONCEPTS FORMELS ===");
        System.out.println("Nombre de concepts: " + classifier.getNumberOfConcepts());
        System.out.println(classifier.getConceptsDescription());

        // Afficher les règles extraites
        System.out.println("\n=== RÈGLES EXTRAITES ===");
        StringBuilder modelInfo = new StringBuilder();
        classifier.getModelDescription(modelInfo, 1);
        System.out.println(modelInfo.toString());

        // Ne plus afficher d'informations de précision ici pour éviter les contradictions
        // La précision LOO est déjà affichée dans la méthode testVariant
    }

    /**
     * Teste le classifieur sur un générateur RandomRBF
     */
    private static void testRandomRBF(Scanner scanner, Variant variant) {
        System.out.print("Nombre d'attributs (par défaut 10): ");
        int numAtts = getIntInput(scanner, 10);

        System.out.print("Nombre de classes (par défaut 2): ");
        int numClasses = getIntInput(scanner, 2);

        System.out.print("Nombre d'instances à générer (par défaut 1000): ");
        int numInstances = getIntInput(scanner, 1000);

        // Créer le générateur
        RandomRBFGenerator generator = new RandomRBFGenerator();
        generator.numAttsOption.setValue(numAtts);
        generator.numClassesOption.setValue(numClasses);

        runTest(generator, "RandomRBF Generator", 10, variant, numInstances);
    }

    /**
     * Teste le classifieur sur un générateur RandomTree
     */
    private static void testRandomTree(Scanner scanner, Variant variant) {
        System.out.print("Nombre d'attributs (par défaut 10): ");
        int numAtts = getIntInput(scanner, 10);

        System.out.print("Nombre de classes (par défaut 2): ");
        int numClasses = getIntInput(scanner, 2);

        System.out.print("Nombre d'instances à générer (par défaut 1000): ");
        int numInstances = getIntInput(scanner, 1000);

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
    private static void testSEA(Scanner scanner, Variant variant) {
        System.out.print("Nombre d'instances à générer (par défaut 1000): ");
        int numInstances = getIntInput(scanner, 1000);

        // Créer le générateur (SEA a des valeurs fixes: 3 attributs et 2 classes)
        SEAGenerator generator = new SEAGenerator();

        runTest(generator, "SEA Generator", 10, variant, numInstances);
    }

    /**
     * Fonction principale qui exécute un test sur un flux généré
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
     * Utilitaire pour obtenir une entrée entière avec valeur par défaut
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

    /**
     * Utilitaire pour obtenir la variante correspondant au choix
     */
    private static Variant getVariantFromChoice(int choice) {
        switch (choice) {
            case 1: return Variant.CpNC_COMV;
            case 2: return Variant.CpNC_CORV;
            case 3: return Variant.CaNC_COMV;
            case 4: return Variant.CaNC_CORV;
            default: return Variant.CpNC_COMV; // par défaut
        }
    }

    /**
     * Extraction des informations sur les règles depuis la description du modèle
     */
    private static List<Map<String, Object>> extractRulesFromModelDescription(String modelDescription) {
        List<Map<String, Object>> rules = new ArrayList<>();

        // Analyse de la description du modèle pour extraire les règles
        // Format attendu: "IF [conditions] THEN [classe] Class Index: [index] Weight (coverage): [valeur]"
        String[] lines = modelDescription.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("Rule #") || line.startsWith("IF ")) {
                Map<String, Object> ruleInfo = new HashMap<>();

                // Extraire le numéro de règle s'il est présent
                if (line.startsWith("Rule #")) {
                    String ruleNumberStr = line.substring(6, line.indexOf(":"));
                    ruleInfo.put("number", Integer.parseInt(ruleNumberStr));
                    // La règle elle-même est sur la ligne suivante, alors on continue
                    continue;
                }

                // Extraire la règle complète
                ruleInfo.put("fullRule", line);

                // Extraire la condition
                int thenIndex = line.indexOf(" THEN ");
                String condition = line.substring(3, thenIndex);
                ruleInfo.put("condition", condition);

                // Extraire la classe prédite
                int classIndexStart = line.indexOf("Class Index:");
                String classPrediction = line.substring(thenIndex + 6, line.indexOf("\t", thenIndex)).trim();
                ruleInfo.put("prediction", classPrediction);

                // Extraire l'index de classe
                String classIndexStr = line.substring(classIndexStart + 12, line.indexOf("\t", classIndexStart)).trim();
                ruleInfo.put("classIndex", Double.parseDouble(classIndexStr));

                // Extraire le poids/couverture
                int weightStart = line.indexOf("Weight (coverage):");
                String weightStr = line.substring(weightStart + 18).trim();
                ruleInfo.put("weight", Double.parseDouble(weightStr));

                rules.add(ruleInfo);
            }
        }

        return rules;
    }

    /**
     * Trouve l'indice du maximum dans un tableau
     */
    private static int getIndexOfMax(double[] array) {
        int maxIndex = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] > array[maxIndex]) {
                maxIndex = i;
            }
        }
        return maxIndex;
    }
}
