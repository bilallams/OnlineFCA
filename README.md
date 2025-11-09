# OnlineFCA - Apprentissage Adaptatif en Ligne pour Classification Interprétable

## Description

**OnlineFCA** est un classificateur de flux de données fondé sur l'Analyse de Concepts Formels (ACF/FCA - Formal Concept Analysis) qui maintient une couverture compacte de concepts et génère des règles SI-ALORS lisibles et traçables. Cette adaptation en ligne des CNC (Classifier Nominal Concepts) est conçue pour traiter des flux massifs de données sous contraintes de mémoire et de latence, tout en garantissant l'interprétabilité et l'explicabilité des décisions.

Compatible avec le framework MOA (Massive Online Analysis), OnlineFCA répond aux exigences des secteurs critiques (finance, santé, sécurité publique, infrastructures) où la transparence des modèles est essentielle pour la confiance et la conformité.

## Contexte

La croissance des données issues de l'IoT, des réseaux sociaux et des journaux d'infrastructure impose des solutions d'apprentissage capables de :
- Traiter des flux massifs non bornés
- Gérer les dérives de concept (évolutions de P(X, y))
- Fonctionner sous contraintes de mémoire O(M) et temps O(t) par instance
- Fournir des modèles interprétables et explicables

OnlineFCA comble la lacune entre performance et transparence en conciliant adaptation en temps réel et explicabilité.

## Caractéristiques Principales

### Architecture et Fonctionnement

- **Phase de grâce (Warm-Up)** : Initialisation avec GP instances pour créer l'ensemble de concepts initial (CNC₀)
- **Phase d'adaptation en ligne** : Traitement séquentiel avec trois stratégies adaptatives :
  - **Cas A (Classification Correcte)** : Renforcement des règles, poids réduit (w × 0.5)
  - **Cas B (Mauvaise Classification)** : Mise à jour des règles, poids amplifié (w × 1.5)
  - **Cas C (Rejet)** : Échantillonnage pondéré et induction d'une nouvelle unité CNC

### Composants Clés

- **Tampon** : Stockage des premières GP instances
- **Pondération dynamique** : Poids wᵢ par instance pour prioriser les exemples informatifs
- **Ensemble E d'unités CNC** : Collection de règles de classification
- **Échantillonnage adaptatif** : Paramètre s contrôlant l'induction de nouvelles unités

### Variantes d'Algorithme

Le système supporte quatre variantes de l'algorithme NCA :
  - **CpNC_COMV** : Attribut pertinent, fermeture multi-valeurs
  - **CpNC_CORV** : Attribut pertinent, fermeture valeur pertinente
  - **CaNC_COMV** : Tous les attributs, fermeture multi-valeurs
  - **CaNC_CORV** : Tous les attributs, fermeture valeurs pertinentes

## Algorithme OnlineFCA

### Opérations Fondamentales

1. **CNCPredict** : Applique l'ensemble de règles pour classifier les instances
   - Identifie les règles dont la prémisse est satisfaite
   - Agrège les prédictions pondérées par confiance
   - Retourne la classe majoritaire ou rejet (∅)

2. **CNCTrain** : Induit une nouvelle unité CNC
   - Calcule les scores d'informativité (gain d'information)
   - Sélectionne les paires attribut-valeur comme graines
   - Calcule les fermetures P'' = ϕ ∘ δ(P)
   - Génère des règles avec support et confiance

### Opérateurs ACF

- **Extension δ(P)** : Retourne les instances satisfaisant la prémisse P
- **Intention ϕ(I)** : Produit les attributs partagés par les instances I
- **Fermeture P''** : Ensemble maximal d'attributs impliqués par P

## Prérequis

- **Java 21** ou supérieur
- **Maven 3.x**
- **MOA 2024.07.0** (Massive Online Analysis)
- **Weka 3.8.6** (Machine Learning Library)

## Installation

### Cloner le projet

```bash
git clone https://github.com/bilallams/OnlineFCA.git
cd OnlineFCA
```

### Compiler le projet

```bash
mvn clean compile
```

### Créer le package JAR

```bash
mvn package
```

Le fichier JAR sera généré dans le dossier `target/`.

## Utilisation

### Intégration avec MOA

1. Compilez le projet avec Maven pour générer le JAR
2.  Ajoutez le JAR généré dans le classpath de MOA 
3.Lancez l'interface MOA ou utilisez MOA en ligne de commande
4. Sélectionnez `CANCLearnerMOA` comme classifieur dans l'évaluateur de flux

### Configuration des Hyperparamètres

- **gracePeriod** (`-g`) : Nombre d'instances pour la phase de grâce (défaut: 1750)
  - Période d'initialisation avant l'apprentissage adaptatif
  - Recommandé : adapter selon la complexité du flux

- **samplingSize** (`-s`) : Taille de l'échantillon pour induction de concepts
  - Contrôle le nombre d'instances utilisées pour créer de nouvelles unités CNC

## Structure du projet

```
src/
├── main/
│   ├── java/
│   │   └── com/lamsili/canc/
│   │       ├── app/           # Application de débogage
│   │       ├── classifier/     # Classifieur principal
│   │       ├── fca/           # Composants FCA (closure, concept, context)
│   │       ├── rules/         # Extraction et gestion de règles
│   │       └── varriants/     # Implémentation des variantes
│   └── resources/
└── test/
```

## Composants Principaux

### Architecture du Système

- **CANCLearnerMOA** : Classifieur principal compatible MOA implémentant OnlineFCA
- **NominalContext** : Gestion du contexte formel (instances × attributs nominaux)
- **ClosureOperator** : Calcul des fermetures P'' = ϕ ∘ δ(P) avec méthodes d'évaluation
  - `AttributeEvalMethod` : Stratégies de sélection d'attributs
  - `ValueEvalMethod` : Stratégies d'évaluation de valeurs
- **FormalConcept** : Représentation des concepts formels (extension/intension)
- **RuleExtractor** : Extraction et gestion de règles SI-ALORS avec métriques
- **NCACoupleSelector** : Sélection de couples (attribut, valeur) pour l'induction
- **Variant** : Implémentation des quatre variantes d'algorithme
- **CANCDebugger** : Application de débogage et visualisation

## Dépendances

- **MOA 2024.07.0** : Framework pour l'analyse de flux de données massifs
- **Weka 3.8.6** : Bibliothèque d'apprentissage automatique et structures de données
## Auteurs

**Bilal Lamsili**  
Efrei Research Lab, Efrei Paris  
Université Paris–Panthéon-Assas, Paris, France  
📧 bilal.lamsili@efrei.fr

**Mondher Maddouri**  
Efrei Research Lab, Efrei Paris  
Université Paris–Panthéon-Assas, Paris, France  
📧 mondher.maddouri@efrei.fr

**Nida Ben Alhabib Meddouri**  
LIPAH, Faculty of Sciences of Tunis  
University of Tunis El Manar, Tunis, Tunisia  
📧 nida.meddouri@gmail.com


## Licence

Ce projet est développé dans un cadre de recherche académique.

## Contact & Support

Pour toute question, suggestion ou collaboration :
- 📧 Email : bilal.lamsili@efrei.fr

---

