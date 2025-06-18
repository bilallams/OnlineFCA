package com.lamsili.canc.app;

import com.lamsili.canc.classifier.CANCLearnerMOA;
import com.lamsili.canc.varriants.Variant;
import moa.core.Example;
import moa.core.TimingUtils;
import moa.evaluation.BasicClassificationPerformanceEvaluator;
import moa.streams.InstanceStream;
import moa.streams.ArffFileStream;
import com.yahoo.labs.samoa.instances.*;

import java.text.DecimalFormat;
import java.util.ArrayList;

// Imports pour Weka et le filtre de discrétisation
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Discretize;
import weka.core.Instances;

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
    private static final Variant DEFAULT_VARIANT = Variant.CpNC_CORV; // Variante par défaut
    private static final int DISPLAY_FREQUENCY = 5000;           // Affichage moins fréquent pour de grands jeux de données

    // Filtre de discrétisation par défaut
    protected static Filter m_Filter = new weka.filters.unsupervised.attribute.Discretize();

    // Contrôle de l'activation de la discrétisation
    private static boolean m_DiscreteEnabled = true;

    private static final DecimalFormat df = new DecimalFormat("0.0000");

    /**
     * Définit le filtre à utiliser pour la discrétisation
     * @param filter Le filtre à utiliser
     */
    public static void setFilter(Filter filter) {
        m_Filter = filter;
    }

    /**
     * Récupère le filtre actuel
     * @return Le filtre configuré
     */
    public static Filter getFilter() {
        return m_Filter;
    }

    /**
     * Active ou désactive la discrétisation
     * @param enabled true pour activer, false pour désactiver
     */
    public static void setDiscretizeEnabled(boolean enabled) {
        m_DiscreteEnabled = enabled;
    }

    /**
     * Vérifie si la discrétisation est activée
     * @return true si la discrétisation est activée, false sinon
     */
    public static boolean isDiscretizeEnabled() {
        return m_DiscreteEnabled;
    }

    /**
     * Récupère la spécification du filtre sous forme de chaîne
     * @return La spécification du filtre
     */
    protected static String getFilterSpec() {
        Filter c = getFilter();
        if (c instanceof weka.core.OptionHandler) {
            return c.getClass().getName() + " "
                    + weka.core.Utils.joinOptions(((weka.core.OptionHandler)c).getOptions());
        }
        return c.getClass().getName();
    }

    public static void main(String[] args) {
        // Activer le débogage
        CANCDebugger.setDebugEnabled(false);

        // Utiliser les paramètres par défaut
        int numInstances = DEFAULT_INSTANCES;

        System.out.println("\n=== PRÉPARATION DES DONNÉES ===");
        System.out.println("Chargement du fichier: " + DEFAULT_DATASET_PATH);

        // Charger le flux de données à partir du fichier ARFF
        ArffFileStream dataStream = new ArffFileStream(DEFAULT_DATASET_PATH, DEFAULT_CLASS_INDEX);
        dataStream.prepareForUse();

        // Vérifier les attributs du jeu de données original
        checkNominalAttributes(dataStream.getHeader(), "ORIGINAL");

        // Appliquer le filtre de discrétisation pour transformer les attributs numériques en nominaux
        System.out.println("\n=== DISCRÉTISATION DES DONNÉES ===");
        System.out.println("Application du filtre de discrétisation...");
        ArffFileStream discretizedStream = applyDiscretizationFilter(dataStream);

        // Vérifier les attributs après discrétisation
        checkNominalAttributes(discretizedStream.getHeader(), "APRÈS DISCRÉTISATION");

        // Vérifier qu'on a bien des données discrétisées (tous les attributs numériques convertis en nominaux)
        boolean allNominal = checkAllAttributesNominal(discretizedStream.getHeader());
        if (allNominal) {
            System.out.println("\n✅ SUCCÈS: Tous les attributs sont maintenant nominaux.");
        } else {
            System.out.println("\n⚠️ ATTENTION: Certains attributs sont encore numériques. La discrétisation n'a pas été complète.");
        }

        // Lancer le test en batch avec les données discrétisées
        System.out.println("\n=== EXÉCUTION DE L'APPRENTISSAGE SUR LES DONNÉES DISCRÉTISÉES ===");
        runBatchEvaluation(discretizedStream, DEFAULT_VARIANT, numInstances, DEFAULT_GRACE_PERIOD, DEFAULT_ENABLE_RESET);
    }

    /**
     * Vérifie que tous les attributs (sauf peut-être la classe) sont nominaux
     * @param header Les en-têtes des attributs à vérifier
     * @return true si tous les attributs (sauf peut-être la classe) sont nominaux
     */
    private static boolean checkAllAttributesNominal(Object header) {
        if (header instanceof weka.core.Instances) {
            weka.core.Instances wekaHeader = (weka.core.Instances) header;
            int numAttributes = wekaHeader.numAttributes();
            int classIndex = wekaHeader.classIndex();

            for (int i = 0; i < numAttributes; i++) {
                if (i != classIndex && !wekaHeader.attribute(i).isNominal()) {
                    return false;
                }
            }
            return true;
        }
        else if (header instanceof com.yahoo.labs.samoa.instances.InstancesHeader) {
            com.yahoo.labs.samoa.instances.InstancesHeader samoaHeader = (com.yahoo.labs.samoa.instances.InstancesHeader) header;
            int numAttributes = samoaHeader.numAttributes();
            int classIndex = samoaHeader.classIndex();

            for (int i = 0; i < numAttributes; i++) {
                if (i != classIndex && !samoaHeader.attribute(i).isNominal()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Vérifie que le jeu de données contient des attributs nominaux
     * et affiche des informations sur ces attributs
     * @param header Les en-têtes des attributs à vérifier
     * @param stage Étape du traitement (ex: "ORIGINAL", "APRÈS DISCRÉTISATION")
     */
    private static void checkNominalAttributes(Object header, String stage) {
        int numAttributes = 0;
        int numNominal = 0;
        int numNumeric = 0;
        int classIndex = -1;
        String className = "";

        // Traiter différemment selon le type d'instance
        if (header instanceof weka.core.Instances) {
            weka.core.Instances wekaHeader = (weka.core.Instances) header;
            numAttributes = wekaHeader.numAttributes();
            classIndex = wekaHeader.classIndex();
            if (classIndex >= 0) {
                className = wekaHeader.classAttribute().name();
            }

            System.out.println("\n=== INFORMATIONS SUR LE JEU DE DONNÉES (" + stage + ") ===");
            if (stage.equals("ORIGINAL")) {
                System.out.println("Fichier: " + DEFAULT_DATASET_PATH);
            }
            System.out.println("Nombre total d'attributs: " + numAttributes);
            System.out.println("Attribut de classe: " + className +
                            " (index " + classIndex + ")");

            System.out.println("\n=== ATTRIBUTS DISPONIBLES ===");
            for (int i = 0; i < numAttributes; i++) {
                if (i == classIndex) {
                    System.out.println("[Classe] " + wekaHeader.attribute(i).name() +
                                      " - Type: " + (wekaHeader.attribute(i).isNominal() ? "Nominal" : "Numérique") +
                                      (wekaHeader.attribute(i).isNominal() ?
                                      " - Valeurs possibles: " + wekaHeader.attribute(i).numValues() : ""));

                    if (wekaHeader.attribute(i).isNominal()) {
                        System.out.print("   Valeurs de classes: ");
                        for (int j = 0; j < wekaHeader.attribute(i).numValues(); j++) {
                            System.out.print(wekaHeader.attribute(i).value(j));
                            if (j < wekaHeader.attribute(i).numValues() - 1) System.out.print(", ");
                        }
                        System.out.println();
                    }
                } else {
                    System.out.println("Attribut " + i + ": " + wekaHeader.attribute(i).name() +
                                      " - Type: " + (wekaHeader.attribute(i).isNominal() ? "Nominal" : "Numérique") +
                                      (wekaHeader.attribute(i).isNominal() ?
                                      " - Valeurs possibles: " + wekaHeader.attribute(i).numValues() : ""));

                    // Afficher les valeurs possibles pour les attributs nominaux, surtout après discrétisation
                    if (wekaHeader.attribute(i).isNominal() && stage.equals("APRÈS DISCRÉTISATION")) {
                        System.out.print("   Valeurs: ");
                        int maxValuesToShow = Math.min(10, wekaHeader.attribute(i).numValues()); // Limiter à 10 valeurs max
                        for (int j = 0; j < maxValuesToShow; j++) {
                            System.out.print(wekaHeader.attribute(i).value(j));
                            if (j < maxValuesToShow - 1) System.out.print(", ");
                        }
                        if (wekaHeader.attribute(i).numValues() > maxValuesToShow) {
                            System.out.print(", ... (+" + (wekaHeader.attribute(i).numValues() - maxValuesToShow) + " autres)");
                        }
                        System.out.println();
                    }

                    if (wekaHeader.attribute(i).isNominal()) numNominal++;
                    else numNumeric++;
                }
            }
        }
        else if (header instanceof com.yahoo.labs.samoa.instances.InstancesHeader) {
            com.yahoo.labs.samoa.instances.InstancesHeader samoaHeader = (com.yahoo.labs.samoa.instances.InstancesHeader) header;
            numAttributes = samoaHeader.numAttributes();
            classIndex = samoaHeader.classIndex();
            if (classIndex >= 0) {
                className = samoaHeader.classAttribute().name();
            }

            System.out.println("\n=== INFORMATIONS SUR LE JEU DE DONNÉES (" + stage + ") ===");
            if (stage.equals("ORIGINAL")) {
                System.out.println("Fichier: " + DEFAULT_DATASET_PATH);
            }
            System.out.println("Nombre total d'attributs: " + numAttributes);
            System.out.println("Attribut de classe: " + className +
                            " (index " + classIndex + ")");

            System.out.println("\n=== ATTRIBUTS DISPONIBLES ===");
            for (int i = 0; i < numAttributes; i++) {
                if (i == classIndex) {
                    System.out.println("[Classe] " + samoaHeader.attribute(i).name() +
                                      " - Type: " + (samoaHeader.attribute(i).isNominal() ? "Nominal" : "Numérique") +
                                      (samoaHeader.attribute(i).isNominal() ?
                                      " - Valeurs possibles: " + samoaHeader.attribute(i).numValues() : ""));

                    if (samoaHeader.attribute(i).isNominal()) {
                        System.out.print("   Valeurs de classes: ");
                        for (int j = 0; j < samoaHeader.attribute(i).numValues(); j++) {
                            System.out.print(samoaHeader.attribute(i).value(j));
                            if (j < samoaHeader.attribute(i).numValues() - 1) System.out.print(", ");
                        }
                        System.out.println();
                    }
                } else {
                    System.out.println("Attribut " + i + ": " + samoaHeader.attribute(i).name() +
                                      " - Type: " + (samoaHeader.attribute(i).isNominal() ? "Nominal" : "Numérique") +
                                      (samoaHeader.attribute(i).isNominal() ?
                                      " - Valeurs possibles: " + samoaHeader.attribute(i).numValues() : ""));

                    // Afficher les valeurs possibles pour les attributs nominaux, surtout après discrétisation
                    if (samoaHeader.attribute(i).isNominal() && stage.equals("APRÈS DISCRÉTISATION")) {
                        System.out.print("   Valeurs: ");
                        int maxValuesToShow = Math.min(10, samoaHeader.attribute(i).numValues()); // Limiter à 10 valeurs max
                        for (int j = 0; j < maxValuesToShow; j++) {
                            System.out.print(samoaHeader.attribute(i).value(j));
                            if (j < maxValuesToShow - 1) System.out.print(", ");
                        }
                        if (samoaHeader.attribute(i).numValues() > maxValuesToShow) {
                            System.out.print(", ... (+" + (samoaHeader.attribute(i).numValues() - maxValuesToShow) + " autres)");
                        }
                        System.out.println();
                    }

                    if (samoaHeader.attribute(i).isNominal()) numNominal++;
                    else numNumeric++;
                }
            }
        }
        else {
            System.err.println("Type d'en-tête non pris en charge: " + header.getClass().getName());
            return;
        }

        System.out.println("\nAttributs nominaux: " + numNominal);
        System.out.println("Attributs numériques: " + numNumeric);

        // Ajouter un résumé des intervalles pour les données discrétisées
        if (stage.equals("APRÈS DISCRÉTISATION")) {
            System.out.println("\n=== RÉSUMÉ DE LA DISCRÉTISATION ===");
            System.out.println("Attributs numériques convertis en nominaux: " + numNominal);
            System.out.println("Nombre moyen de valeurs par attribut nominal: " +
                            calculateAverageValuesPerNominalAttribute(header));
        }

        if (numNominal == 0) {
            System.out.println("\n⚠️ AVERTISSEMENT: Ce jeu de données ne contient pas d'attributs nominaux.");
            System.out.println("Le classifieur CANC est conçu pour traiter des attributs nominaux.");
            System.out.println("Les performances risquent d'être dégradées.");
        }
        System.out.println("==============================================\n");
    }

    /**
     * Calcule le nombre moyen de valeurs possibles par attribut nominal
     * @param header En-tête des attributs
     * @return Moyenne des valeurs par attribut nominal
     */
    private static double calculateAverageValuesPerNominalAttribute(Object header) {
        int totalValues = 0;
        int nominalCount = 0;

        if (header instanceof weka.core.Instances) {
            weka.core.Instances wekaHeader = (weka.core.Instances) header;
            int numAttributes = wekaHeader.numAttributes();
            int classIndex = wekaHeader.classIndex();

            for (int i = 0; i < numAttributes; i++) {
                if (i != classIndex && wekaHeader.attribute(i).isNominal()) {
                    totalValues += wekaHeader.attribute(i).numValues();
                    nominalCount++;
                }
            }
        }
        else if (header instanceof com.yahoo.labs.samoa.instances.InstancesHeader) {
            com.yahoo.labs.samoa.instances.InstancesHeader samoaHeader = (com.yahoo.labs.samoa.instances.InstancesHeader) header;
            int numAttributes = samoaHeader.numAttributes();
            int classIndex = samoaHeader.classIndex();

            for (int i = 0; i < numAttributes; i++) {
                if (i != classIndex && samoaHeader.attribute(i).isNominal()) {
                    totalValues += samoaHeader.attribute(i).numValues();
                    nominalCount++;
                }
            }
        }

        return nominalCount > 0 ? (double) totalValues / nominalCount : 0;
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

    /**
     * Applique un filtre de discrétisation sur les attributs numériques du flux
     * et retourne un nouveau flux avec les attributs transformés en nominal
     *
     * @param stream Flux de données original
     * @return Flux avec attributs numériques transformés en attributs nominaux
     */
    private static ArffFileStream applyDiscretizationFilter(ArffFileStream stream) {
        // Si la discrétisation est désactivée, retourner le flux original sans modification
        if (!m_DiscreteEnabled) {
            System.out.println("\n=== DISCRÉTISATION DÉSACTIVÉE ===");
            System.out.println("Le flux de données est utilisé tel quel, sans transformation.");
            return stream;
        }

        System.out.println("\n=== APPLICATION DU FILTRE DE DISCRÉTISATION ===");
        try {
            // Récupérer les données d'en-tête du flux
            Object header = stream.getHeader();
            Instances originalData;

            // Convertir le header MOA en Instances Weka si nécessaire
            if (header instanceof com.yahoo.labs.samoa.instances.InstancesHeader) {
                // Au lieu d'utiliser getFileDescription() qui n'existe pas, nous allons utiliser
                // directement le fichier source du flux en utilisant un nouveau flux à partir du fichier original
                String sourceFile = DEFAULT_DATASET_PATH; // Utiliser le chemin par défaut

                // Créer une nouvelle instance Weka à partir du fichier
                weka.core.converters.ArffLoader loader = new weka.core.converters.ArffLoader();
                loader.setFile(new java.io.File(sourceFile));
                originalData = loader.getDataSet();

                // Définir l'index de la classe
                int classIndex = stream.getHeader().classIndex();
                if (classIndex >= 0) {
                    originalData.setClassIndex(classIndex);
                } else {
                    originalData.setClassIndex(originalData.numAttributes() - 1);
                }

                System.out.println("Données chargées à partir de: " + sourceFile);
            } else if (header instanceof weka.core.Instances) {
                originalData = (weka.core.Instances) header;
            } else {
                throw new IllegalArgumentException("Type d'en-tête non pris en charge: " + header.getClass().getName());
            }

            // S'assurer que la classe cible n'est jamais discrétisée, même si elle est numérique
            if (m_Filter instanceof Discretize) {
                Discretize discretize = (Discretize) m_Filter;

                // Forcer l'ignorance de la classe (qu'elle soit nominale ou numérique)
                discretize.setIgnoreClass(true);

                // Configurer le nombre de bins et le type de discrétisation
                discretize.setBins(10); // Nombre d'intervalles
                discretize.setUseEqualFrequency(true); // Utiliser des intervalles de fréquence égale

                System.out.println("Type de filtre: " + m_Filter.getClass().getSimpleName());
                System.out.println("Spécification: " + getFilterSpec());
                System.out.println("Nombre de bins: " + discretize.getBins());
                System.out.println("Mode fréquence égale: " + discretize.getUseEqualFrequency());
                System.out.println("Classe préservée (non discrétisée): Oui");
            } else {
                System.out.println("Type de filtre: " + m_Filter.getClass().getSimpleName());
                System.out.println("Spécification: " + getFilterSpec());
                System.out.println("Classe préservée: Non spécifié");
            }

            // Initialiser le filtre avec les données
            m_Filter.setInputFormat(originalData);

            // Appliquer le filtre aux données
            System.out.println("Application du filtre de discrétisation...");

            // Transformer les données initiales (header) avec le filtre
            Instances discretizedData = Filter.useFilter(originalData, m_Filter);

            // Vérifier que la classe est bien du type attendu après filtrage
            if (discretizedData.classIndex() >= 0) {
                System.out.println("Type de l'attribut classe après discrétisation: " +
                        (discretizedData.classAttribute().isNominal() ? "Nominal" : "Numérique"));
            }

            // Créer un nouveau fichier ARFF temporaire avec les données discrétisées
            String tempFilePath = System.getProperty("java.io.tmpdir") + "/discretized_data.arff";
            weka.core.converters.ArffSaver saver = new weka.core.converters.ArffSaver();
            saver.setInstances(discretizedData);
            saver.setFile(new java.io.File(tempFilePath));
            saver.writeBatch();

            System.out.println("Données discrétisées enregistrées temporairement dans: " + tempFilePath);

            // Créer un nouveau flux à partir du fichier ARFF temporaire
            ArffFileStream discretizedStream = new ArffFileStream(tempFilePath, originalData.classIndex());
            discretizedStream.prepareForUse();

            return discretizedStream;

        } catch (Exception e) {
            System.err.println("ERREUR lors de la discrétisation: " + e.getMessage());
            e.printStackTrace();
            // En cas d'erreur, retourner le flux original
            return stream;
        }
    }
}
