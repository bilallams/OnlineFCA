package com.lamsili.canc.app;

import com.lamsili.canc.classifier.CANCLearnerMOA;
import com.lamsili.canc.varriants.Variant;
import moa.core.Example;
import moa.core.TimingUtils;
import moa.evaluation.BasicClassificationPerformanceEvaluator;
import moa.streams.InstanceStream;
import moa.streams.ArffFileStream;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;

import java.text.DecimalFormat;

/**
 * TestCANCStream - Classe pour tester le classifieur CANC en apprentissage
 * sur un flux de données provenant d'un fichier ARFF.
 */
public class TestCANCStream {

    // Configuration par défaut
    private static final String DEFAULT_DATASET_PATH = "C:\\Users\\lamsilibil\\Downloads\\airlines.arff\\airlines.arff";
    private static final int DEFAULT_CLASS_INDEX = -1;  // -1 signifie dernier attribut
    private static final int DEFAULT_INSTANCES = 100000;         // Limité à 100k instances pour des performances raisonnables
    private static final int DEFAULT_GRACE_PERIOD = 2000;        // Période de grâce augmentée pour les grands datasets
    private static final boolean DEFAULT_ENABLE_RESET = false;    // Activer le reset périodique pour gérer la mémoire
    private static final int DEFAULT_RESET_FREQUENCY = 10;     // Reset tous les 10% des instances traitées
    private static final Variant DEFAULT_VARIANT = Variant.CpNC_COMV; // Variante par défaut
    private static final int DISPLAY_FREQUENCY = 5000;           // Affichage moins fréquent pour de grands jeux de données

    // Options supplémentaires pour l'optimisation des grands jeux de données
    private static final boolean ENABLE_WINDOWING = true;        // Activer le fenêtrage pour limiter la mémoire utilisée
    private static final int WINDOW_SIZE = 10000;                // Taille de la fenêtre (nombre d'instances récentes à conserver)

    private static final DecimalFormat df = new DecimalFormat("0.0000");

    public static void main(String[] args) {
        // Activer le débogage
        CANCDebugger.setDebugEnabled(false);

        // Paramètres du test - à modifier selon vos besoins
        int numInstances = DEFAULT_INSTANCES;
        int gracePeriod = DEFAULT_GRACE_PERIOD;
        boolean enableReset = DEFAULT_ENABLE_RESET;
        Variant variant = DEFAULT_VARIANT;

        // Charger le flux de données à partir du fichier ARFF
        ArffFileStream dataStream = new ArffFileStream(DEFAULT_DATASET_PATH, DEFAULT_CLASS_INDEX);
        dataStream.prepareForUse();

        // Vérifier que le jeu de données contient bien des données nominales
        checkNominalAttributes(dataStream);

        // Lancer le test en batch
        runBatchEvaluation(dataStream, variant, numInstances, gracePeriod, enableReset);
    }

    /**
     * Vérifie que le jeu de données contient des attributs nominaux
     * et affiche des informations sur ces attributs
     * @param stream Le flux de données à vérifier
     */
    private static void checkNominalAttributes(ArffFileStream stream) {
        Instances header = stream.getHeader();
        int numAttributes = header.numAttributes();
        int numNominal = 0;
        int numNumeric = 0;

        System.out.println("\n=== INFORMATIONS SUR LE JEU DE DONNÉES ===");
        System.out.println("Fichier: " + DEFAULT_DATASET_PATH);
        System.out.println("Nombre total d'attributs: " + numAttributes);
        System.out.println("Attribut de classe: " + header.classAttribute().name() +
                           " (index " + header.classIndex() + ")");

        System.out.println("\n=== ATTRIBUTS DISPONIBLES ===");
        for (int i = 0; i < numAttributes; i++) {
            if (i == header.classIndex()) {
                System.out.println("[Classe] " + header.attribute(i).name() +
                                  " - Type: " + (header.attribute(i).isNominal() ? "Nominal" : "Numérique") +
                                  (header.attribute(i).isNominal() ?
                                  " - Valeurs possibles: " + header.attribute(i).numValues() : ""));

                if (header.attribute(i).isNominal()) {
                    System.out.print("   Valeurs de classes: ");
                    for (int j = 0; j < header.attribute(i).numValues(); j++) {
                        System.out.print(header.attribute(i).value(j));
                        if (j < header.attribute(i).numValues() - 1) System.out.print(", ");
                    }
                    System.out.println();
                }
            } else {
                System.out.println("Attribut " + i + ": " + header.attribute(i).name() +
                                  " - Type: " + (header.attribute(i).isNominal() ? "Nominal" : "Numérique") +
                                  (header.attribute(i).isNominal() ?
                                  " - Valeurs possibles: " + header.attribute(i).numValues() : ""));

                if (header.attribute(i).isNominal()) numNominal++;
                else numNumeric++;
            }
        }

        System.out.println("\nAttributs nominaux: " + numNominal);
        System.out.println("Attributs numériques: " + numNumeric);

        if (numNominal == 0) {
            System.out.println("\n⚠️ AVERTISSEMENT: Ce jeu de données ne contient pas d'attributs nominaux.");
            System.out.println("Le classifieur CANC est conçu pour traiter des attributs nominaux.");
            System.out.println("Les performances risquent d'être dégradées.");
        }
        System.out.println("==============================================\n");
    }

    /**
     * Méthode principale pour exécuter le test avec traitement batch instance par instance
     *
     * @param stream Flux de données à traiter
     * @param variant Variante du classifieur CANC
     * @param maxInstances Nombre maximum d'instances à traiter
     * @param gracePeriod Période de grâce avant l'apprentissage
     * @param enableReset Si vrai, le modèle sera réinitialisé périodiquement
     */
    private static void runBatchEvaluation(InstanceStream stream, Variant variant,
                                          int maxInstances, int gracePeriod, boolean enableReset) {

        System.out.println("\n=== DÉMARRAGE DU TEST EN MODE BATCH ===");
        System.out.println("Source: Fichier ARFF");
        System.out.println("Variante CANC: " + variant);
        System.out.println("Instances à traiter: " + maxInstances);
        System.out.println("Période de grâce: " + gracePeriod);
        System.out.println("Reset activé: " + (enableReset ? "Oui" : "Non"));
        System.out.println("=========================================\n");

        // Créer et configurer le classifieur
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

        // Configurer la période de grâce
        classifier.gracePeriodOption.setValue(gracePeriod);

        // Évaluateur global (sur toutes les instances)
        BasicClassificationPerformanceEvaluator globalEvaluator = new BasicClassificationPerformanceEvaluator();

        // Variables de suivi
        int instancesProcessed = 0;
        long startTime = TimingUtils.getNanoCPUTimeOfCurrentThread();

        // Message de démarrage
        CANCDebugger.printTimestampedMessage("Démarrage de l'apprentissage avec " + variant);

        // Initialisation du flux et du classifieur
        stream.restart();
        classifier.setModelContext(stream.getHeader());
        classifier.prepareForUse();

        // En-tête pour les résultats intermédiaires
        System.out.println("Instances\tPrécision\tTemps(s)\tNbRègles");

        // Boucle principale d'apprentissage - traitement instance par instance
        while (stream.hasMoreInstances() && instancesProcessed < maxInstances) {
            // Reset périodique si activé
            if (instancesProcessed > 0 && enableReset &&
                instancesProcessed % (maxInstances / DEFAULT_RESET_FREQUENCY) == 0) {
                System.out.println("\n--- RESET du modèle à l'instance " + instancesProcessed + " ---");
                classifier.resetLearning();
                classifier.setModelContext(stream.getHeader());
                classifier.prepareForUse();
            }

            // Récupérer la prochaine instance
            Example<Instance> example = stream.nextInstance();
            Instance instance = example.getData();

            // Test-then-train : prédiction avant apprentissage
            double[] votes = classifier.getVotesForInstance(instance);

            // Évaluation de la performance
            globalEvaluator.addResult(example, votes);

            // Entraînement sur l'instance
            classifier.trainOnInstance(instance);

            // Incrémenter le compteur
            instancesProcessed++;

            // Forcer la préparation du modèle (génération de règles) tous les gracePeriod instances
            if (instancesProcessed % gracePeriod == 0) {
                try {
                    // Accéder à buildModel via réflexion puisque c'est une méthode privée
                    java.lang.reflect.Method buildModelMethod =
                        CANCLearnerMOA.class.getDeclaredMethod("buildModel");
                    buildModelMethod.setAccessible(true);
                    buildModelMethod.invoke(classifier);
                } catch (Exception e) {
                    System.err.println("Erreur lors de la construction forcée du modèle: " + e.getMessage());
                }
            }

            // Afficher les statistiques intermédiaires à intervalle régulier
            if (instancesProcessed % DISPLAY_FREQUENCY == 0) {
                double accuracy = globalEvaluator.getFractionCorrectlyClassified();
                int ruleCount = classifier.getRules().size();
                double timeTaken = TimingUtils.nanoTimeToSeconds(
                    TimingUtils.getNanoCPUTimeOfCurrentThread() - startTime);

                System.out.printf("%d\t\t%.4f\t\t%.2f\t\t%d%n",
                               instancesProcessed, accuracy, timeTaken, ruleCount);
            }
        }

        // Afficher les résultats finaux
        double totalTime = TimingUtils.nanoTimeToSeconds(TimingUtils.getNanoCPUTimeOfCurrentThread() - startTime);
        double globalAccuracy = globalEvaluator.getFractionCorrectlyClassified();
        int finalRuleCount = classifier.getRules().size();

        System.out.println("\n=== RÉSULTATS FINAUX ===");
        System.out.println("Instances traitées: " + instancesProcessed);
        System.out.println("Précision globale: " + df.format(globalAccuracy) + " (" +
                         String.format("%.2f%%", globalAccuracy * 100) + ")");
        System.out.println("Temps total: " + df.format(totalTime) + " secondes");
        System.out.println("Vitesse: " + df.format(instancesProcessed / totalTime) + " instances/seconde");
        System.out.println("Nombre de règles générées: " + finalRuleCount);

        // Afficher un résumé du modèle final
        CANCDebugger.printDebugHeader("MODÈLE FINAL");

        // Afficher les règles si moins de 20
        if (finalRuleCount < 20) {
            StringBuilder modelInfo = new StringBuilder();
            classifier.getModelDescription(modelInfo, 1);
            System.out.println("Règles:");
            System.out.println(modelInfo.toString());
        } else {
            System.out.println("(Trop de règles pour affichage)");
        }

        CANCDebugger.printDebugFooter();
    }
}
