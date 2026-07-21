package com.portfolio.ohada.formalisation;

import java.util.List;

/**
 * Phase 2 — SORTIE (contrat) du noeud forme_juridique. Cf spec §5 : ce record est ce que Claude
 * REMPLIT via structured output (ChatClient.call().entity(RecoForme.class)). Spring AI genere un
 * schema JSON a partir de ces champs, force Claude a le respecter, puis deserialise la reponse ici.
 *
 * Pourquoi une sortie TYPEE et pas de la prose : ce nœud sera branche dans un graphe (Phase 3). Le
 * downstream (demarches, redacteur) et le routage conditionnel (via confiance) ont besoin de champs
 * exploitables par du code, pas d'un paragraphe a re-parser.
 *
 * @param forme               la forme recommandee (une seule) parmi l'enum Forme
 * @param justification       pourquoi CETTE forme convient au profil (doit citer les [ref] articles)
 * @param formesEcartees      chaque forme non retenue AVEC sa raison (exigence spec : ecart justifie)
 * @param capitalMinApplicable regle de capital applicable a la forme retenue (ex. "librement fixe par les statuts")
 * @param confiance           0..1 — degre de confiance ; servira au routage conditionnel en Phase 3
 * @param citations           references sources adossant les affirmations normatives
 */
public record RecoForme(
        Forme forme,
        String justification,
        List<FormeEcartee> formesEcartees,
        String capitalMinApplicable,
        double confiance,
        List<Citation> citations) {

    /** Les formes couvertes en V1 (spec §1.1). SAS mentionnee mais hors generation de statuts en V1. */
    public enum Forme {
        ENTREPRENANT, EI, SARL, SARL_U, SA
    }

    /** Une forme ecartee et la raison de l'ecart (ex. SA ecartee car capital/gouvernance trop lourds). */
    public record FormeEcartee(Forme forme, String raison) {
    }

    /**
     * Une citation = une affirmation adossee a une reference source. claim = ce qui est affirme,
     * source = l'acte uniforme (AUSCGIE / AUDCG), ref = l'article precis (ex. "AUSCGIE art. 309").
     * (url volontairement absent en T1 : nos chunks portent la ref, pas toujours l'url — a enrichir.)
     */
    public record Citation(String claim, String source, String ref) {
    }
}
