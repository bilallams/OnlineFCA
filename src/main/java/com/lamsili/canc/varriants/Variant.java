package com.lamsili.canc.varriants;

/**
 * Définit les différentes variantes de l'algorithme NCA selon la façon dont
 * sont sélectionnés les couples attribut-valeur.
 */
public enum Variant {
    /**
     * Variante CpNC_COMV : attribut pertinent, fermeture multi-valeurs
     */
    CpNC_COMV,

    /**
     * Variante CpNC_CORV : attribut pertinent, fermeture valeurs pertinentes
     */
    CpNC_CORV,

    /**
     * Variante CaNC_COMV : tous les attributs, fermeture multi-valeurs
     */
    CaNC_COMV,

    /**
     * Variante CaNC_CORV : tous les attributs, fermeture valeurs pertinentes
     */
    CaNC_CORV
}
