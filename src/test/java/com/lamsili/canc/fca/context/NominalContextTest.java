package com.lamsili.canc.fca.context;

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
 * Tests unitaires pour la classe NominalContext
 */
public class NominalContextTest {

    private NominalContext context;
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

        } catch (Exception e) {
            fail("Erreur lors de la création des données de test: " + e.getMessage());
        }
    }

    @Test
    public void testAddInstance() {
        // Tester que le contexte est vide au départ
        assertEquals(0, context.getNumInstances());

        // Ajouter des instances et vérifier
        context.addInstance(instance1);
        assertEquals(1, context.getNumInstances());

        context.addInstance(instance2);
        assertEquals(2, context.getNumInstances());
    }

    @Test
    public void testDelta() {
        // Ajouter des instances au contexte
        context.addInstance(instance1);
        context.addInstance(instance2);
        context.addInstance(instance3);

        // Tester la méthode delta
        Set<Integer> ensoleille = context.delta("temperature", "ensoleillé");
        assertEquals(2, ensoleille.size());
        assertTrue(ensoleille.contains(0)); // instance1
        assertTrue(ensoleille.contains(2)); // instance3

        Set<Integer> normale = context.delta("humidite", "normale");
        assertEquals(2, normale.size());
        assertTrue(normale.contains(1)); // instance2
        assertTrue(normale.contains(2)); // instance3

        // Tester avec une valeur inexistante
        Set<Integer> inexistant = context.delta("temperature", "froid");
        assertTrue(inexistant.isEmpty());
    }

    @Test
    public void testGetInstanceClass() {
        context.addInstance(instance1);
        context.addInstance(instance2);

        assertEquals("ne_pas_jouer", context.getInstanceClass(0));
        assertEquals("jouer", context.getInstanceClass(1));

        // Test avec un index hors limites
        assertNull(context.getInstanceClass(10));
    }

    @Test
    public void testGetInstance() {
        context.addInstance(instance1);
        context.addInstance(instance2);

        Instance retrieved1 = context.getInstance(0);
        assertEquals(instance1.classValue(), retrieved1.classValue(), 0.001);

        Instance retrieved2 = context.getInstance(1);
        assertEquals(instance2.classValue(), retrieved2.classValue(), 0.001);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetInstanceOutOfBounds() {
        context.addInstance(instance1);
        // Ceci devrait lever une IndexOutOfBoundsException
        context.getInstance(1);
    }

    @Test
    public void testGetInstances() {
        context.addInstance(instance1);
        context.addInstance(instance2);

        List<Instance> allInstances = context.getInstances();
        assertEquals(2, allInstances.size());
        assertEquals(instance1.classValue(), allInstances.get(0).classValue(), 0.001);
        assertEquals(instance2.classValue(), allInstances.get(1).classValue(), 0.001);
    }

    @Test
    public void testClear() {
        context.addInstance(instance1);
        context.addInstance(instance2);

        assertEquals(2, context.getNumInstances());

        context.clear();

        assertEquals(0, context.getNumInstances());
        // Vérifier que la structure deltaIndex est aussi vidée
        assertEquals(0, context.getNumNominalAttributes());
    }

    @Test
    public void testGetNumNominalAttributes() {
        context.addInstance(instance1);

        // Le dataset a 3 attributs nominaux (hors classe)
        assertEquals(3, context.getNumNominalAttributes());
    }
}
