package com.portfolio.ohada.formalisation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.portfolio.ohada.formalisation.FormeJuridiqueService.Recommandation;

/**
 * Phase 2, Tranche 1 — expose le nœud forme_juridique en HTTP pour le tester isolement.
 * Controller MINCE (comme au T7) : il ne fait que deleguer au service. Toute la logique est dans
 * FormeJuridiqueService, pour que ce meme service soit reutilisable tel quel par le graphe (Phase 3).
 */
@RestController
@RequestMapping("/forme")
public class FormeJuridiqueController {

    private final FormeJuridiqueService formeJuridique;

    public FormeJuridiqueController(FormeJuridiqueService formeJuridique) {
        this.formeJuridique = formeJuridique;
    }

    /**
     * POST /forme/recommander — corps = ProfilPorteur (JSON). Renvoie la RecoForme + les articles RAG
     * utilises (tracabilite). Ex :
     *   { "activite":"commerce de detail", "nbAssocies":2, "capitalEnvisageFcfa":null,
     *     "apportsEnNature":false, "resident":true, "budgetFormalitesFcfa":null,
     *     "objectif":"responsabilite limitee" }
     */
    @PostMapping("/recommander")
    public Recommandation recommander(@RequestBody ProfilPorteur profil) {
        return formeJuridique.recommander(profil);
    }
}
