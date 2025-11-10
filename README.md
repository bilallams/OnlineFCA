# OnlineFCA 

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
2. Ajoutez le JAR généré dans le classpath de MOA 
3. Lancez l'interface MOA ou utilisez MOA en ligne de commande
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

