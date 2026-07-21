package com.portfolio.ohada.formalisation;

/**
 * Phase 2 — ENTREE du noeud forme_juridique : le profil du porteur de projet.
 *
 * C'est le contrat d'ENTREE (cf spec §3, TypedDict ProfilPorteur porte en record Java). L'agent lit
 * ces champs pour recommander une forme juridique. Les entiers "nullable" sont des Integer (objets) et
 * pas des int (primitifs) : une valeur ABSENTE (le porteur ne sait pas encore son capital) doit pouvoir
 * valoir null — un int primitif vaudrait 0, ce qui MENT (0 FCFA de capital != capital inconnu).
 *
 * @param activite             activite envisagee (ex. "commerce de detail", "conseil informatique")
 * @param nbAssocies           nombre d'associes (1 = candidat entreprenant/EI/SARL_U ; 2+ = SARL/SA)
 * @param capitalEnvisageFcfa  capital envisage en FCFA, ou null si non encore decide
 * @param apportsEnNature      y a-t-il des apports en nature (materiel, fonds de commerce) ?
 * @param resident             le porteur reside-t-il dans le pays (vs etranger) ?
 * @param budgetFormalitesFcfa budget disponible pour les formalites, ou null si inconnu
 * @param objectif             objectif prioritaire ("responsabilite limitee", "cout minimal", "lever des fonds"...)
 */
public record ProfilPorteur(
        String activite,
        int nbAssocies,
        Integer capitalEnvisageFcfa,
        boolean apportsEnNature,
        boolean resident,
        Integer budgetFormalitesFcfa,
        String objectif) {
}
