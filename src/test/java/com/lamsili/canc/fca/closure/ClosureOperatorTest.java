package com.lamsili.canc.fca.closure;

import com.lamsili.canc.fca.context.NominalContext;
import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.DenseInstance;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;
import com.yahoo.labs.samoa.instances.InstancesHeader;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests unitaires pour la classe ClosureOperator
 */
public class ClosureOperatorTest {

    private NominalContext context;
    private ClosureOperator operator;
    private Instance instance1, instance2, instance3;
    private InstancesHeader header;

    @Before
    public void setUp() {
        // Créer un contexte nominal pour les tests
        context = new NominalContext();

        try {
            // Création des attributs
            ArrayList<Attribute> attributes = new ArrayList<>();

            List<String> tempValues = new ArrayList<>();
            tempValues.add("ensoleillé");
            tempValues.add("nuageux");
            tempValues.add("pluvieux");
            attributes.add(new Attribute("temperature", tempValues));

            List<String> humValues = new ArrayList<>();
            humValues.add("élevée");
            humValues.add("normale");
            humValues.add("basse");
            attributes.add(new Attribute("humidite", humValues));

            List<String> ventValues = new ArrayList<>();
            ventValues.add("oui");
            ventValues.add("non");
            attributes.add(new Attribute("vent", ventValues));

            List<String> classValues = new ArrayList<>();
            classValues.add("jouer");
            classValues.add("ne_pas_jouer");
            attributes.add(new Attribute("decision", classValues));

            // Création du header
            header = new InstancesHeader(new Instances("TestData", attributes, 0));
            header.setClassIndex(3);

            // Création des instances
            instance1 = new DenseInstance(4);
            instance1.setValue(0, 0); // temperature = ensoleillé
            instance1.setValue(1, 0); // humidite = élevée
            instance1.setValue(2, 1); // vent = non
            instance1.setValue(3, 1); // decision = ne_pas_jouer
            instance1.setDataset(header);

            instance2 = new DenseInstance(4);
            instance2.setValue(0, 1); // temperature = nuageux
            instance2.setValue(1, 1); // humidite = normale
            instance2.setValue(2, 1); // vent = non
            instance2.setValue(3, 0); // decision = jouer
            instance2.setDataset(header);

            instance3 = new DenseInstance(4);
            instance3.setValue(0, 0); // temperature = ensoleillé
            instance3.setValue(1, 1); // humidite = normale
            instance3.setValue(2, 0); // vent = oui
            instance3.setValue(3, 0); // decision = jouer
            instance3.setDataset(header);

            // Ajouter les instances au contexte
            context.addInstance(instance1);
            context.addInstance(instance2);
            context.addInstance(instance3);

            // Initialiser l'opérateur de fermeture avec le contexte
            operator = new ClosureOperator(context);

        } catch (Exception e) {
            fail("Erreur lors de la création des données de test: " + e.getMessage());
        }
    }

    @Test
    public void testDelta() {
        // Tester la méthode delta pour récupérer les instances avec un attribut-valeur spécifique
        Set<Integer> ensoleille = operator.delta("temperature", "ensoleillé");
        assertEquals(2, ensoleille.size());
        assertTrue(ensoleille.contains(0)); // instance1
        assertTrue(ensoleille.contains(2)); // instance3

        Set<Integer> normale = operator.delta("humidite", "normale");
        assertEquals(2, normale.size());
        assertTrue(normale.contains(1)); // instance2
        assertTrue(normale.contains(2)); // instance3

        // Tester avec une valeur inexistante
        Set<Integer> inexistant = operator.delta("temperature", "froid");
        assertTrue(inexistant.isEmpty());
    }

    @Test
    public void testPhi() {
        // Tester phi pour un ensemble d'instances
        Set<Integer> instanceIndices = new HashSet<>(Arrays.asList(0, 2)); // instances 1 et 3
        Set<Map.Entry<String, String>> commonAttrs = operator.phi(instanceIndices);

        // Les instances 1 et 3 partagent l'attribut temperature=ensoleillé
        boolean found = false;
        for (Map.Entry<String, String> entry : commonAttrs) {
            if (entry.getKey().equals("temperature") && entry.getValue().equals("ensoleillé")) {
                found = true;
                break;
            }
        }
        assertTrue("Les instances devraient partager l'attribut temperature=ensoleillé", found);

        // Tester avec un ensemble vide
        Set<Map.Entry<String, String>> emptyResult = operator.phi(Collections.emptySet());
        assertTrue(emptyResult.isEmpty());
    }

    @Test
    public void testCalculateRelevanceScore() {
        // Calculer le score de pertinence pour temperature=ensoleillé
        double ensoleilleTempScore = operator.calculateRelevanceScore("temperature", "ensoleillé");
        // 2/3 des instances ont temperature=ensoleillé, avec une pureté de 1/2 (une instance jouer, une ne_pas_jouer)
        assertEquals(2.0/3.0 * 0.5, ensoleilleTempScore, 0.001);

        // Calculer le score de pertinence pour humidite=normale
        double normaleHumScore = operator.calculateRelevanceScore("humidite", "normale");
        // 2/3 des instances ont humidite=normale, toutes les deux avec classe=jouer (pureté de 1)
        assertEquals(2.0/3.0 * 1.0, normaleHumScore, 0.001);

        // Tester avec une valeur inexistante
        double inexistantScore = operator.calculateRelevanceScore("temperature", "froid");
        assertEquals(0.0, inexistantScore, 0.001);
    }

    @Test
    public void testGetMostRelevantValue() {
        // Pour l'attribut temperature, les valeurs sont:
        // - ensoleillé: 2/3 instances, pureté de 0.5 => score = 2/3 * 0.5 = 0.333
        // - nuageux: 1/3 instances, pureté de 1.0 => score = 1/3 * 1.0 = 0.333
        // Les scores sont égaux, le premier rencontré sera choisi (probablement ensoleillé)
        String mostRelevantTemp = operator.getMostRelevantValue("temperature");
        assertTrue(mostRelevantTemp.equals("ensoleillé") || mostRelevantTemp.equals("nuageux"));

        // Pour l'attribut humidite, les valeurs sont:
        // - élevée: 1/3 instances, pureté de 1.0 => score = 1/3 * 1.0 = 0.333
        // - normale: 2/3 instances, pureté de 1.0 => score = 2/3 * 1.0 = 0.666
        // => normale a un meilleur score
        String mostRelevantHum = operator.getMostRelevantValue("humidite");
        assertEquals("normale", mostRelevantHum);
    }

    @Test
    public void testGetMostInformativeAttribute() {
        // L'attribut le plus informatif devrait être humidite, car:
        // - humidite=élevée => ne_pas_jouer (100%)
        // - humidite=normale => jouer (100%)
        String mostInformative = operator.getMostInformativeAttribute();
        assertEquals("humidite", mostInformative);
    }
}
