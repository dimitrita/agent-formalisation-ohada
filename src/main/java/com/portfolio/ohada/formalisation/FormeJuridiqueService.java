package com.portfolio.ohada.formalisation;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.MarkdownCodeBlockCleaner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.portfolio.ohada.formalisation.HybridSearchService.ResultatRerank;

/**
 * Phase 2, Tranche 1 — NŒUD forme_juridique (version service autonome).
 *
 * Role : a partir d'un ProfilPorteur, recommander UNE forme juridique JUSTIFIEE et SOURCEE (RecoForme).
 * On construit ce nœud comme un service Spring classique ; il sera EMBALLE dans le graphe LangGraph4j
 * en Phase 3. Construire la brique avant la tuyauterie = testable isolement (POST /forme/recommander).
 *
 * Pipeline (reutilise tout le RAG de la Phase 1) :
 *   profil --construireRequete--> requete texte
 *          --HybridSearchService--> articles OHADA pertinents (dense+lexical -> RRF -> rerank)
 *          --ChatClient.entity()--> RecoForme (Claude rempli un objet Java typé, borne au contexte)
 */
@Service
public class FormeJuridiqueService {

    private final HybridSearchService hybridSearch;
    private final ChatClient chatClient;
    private final int topK;   // combien d'articles on donne comme contexte a Claude

    /**
     * Convertisseur "reponse LLM -> RecoForme". On le construit nous-memes (au lieu du .entity(Class)
     * par defaut) pour lui greffer un MarkdownCodeBlockCleaner : Claude renvoie souvent son JSON
     * enrobe de ```json ... ``` et le parser Jackson bute sur le backtick. Le cleaner retire ces
     * fences AVANT le parsing. Etant aussi un FormatProvider, ce converter injecte tout seul le
     * schema JSON attendu dans le prompt quand on le passe a .entity(converter).
     */
    private final BeanOutputConverter<RecoForme> recoConverter =
            new BeanOutputConverter<>(RecoForme.class, null, new MarkdownCodeBlockCleaner());

    public FormeJuridiqueService(HybridSearchService hybridSearch,
            ChatClient.Builder builder,
            @Value("${rerank.top-k}") int topK) {
        this.hybridSearch = hybridSearch;
        this.chatClient = builder.build();
        this.topK = topK;
    }

    /**
     * Consigne systeme = garde-fou anti-hallucination (meme principe qu'au guardrail T7) MAIS ici la
     * sortie est STRUCTUREE : Claude ne redige pas de la prose, il remplit le record RecoForme. Le
     * schema JSON du record est ajoute automatiquement par Spring AI ; on n'a qu'a poser les regles.
     * %s sera remplace par les articles OHADA retrouves.
     */
    private static final String CONSIGNE_SYSTEME = """
            Tu es un assistant juridique specialise en droit OHADA (formalisation d'entreprise en zone OHADA).
            A partir du PROFIL du porteur (message utilisateur) et UNIQUEMENT des ARTICLES fournis ci-dessous,
            recommande UNE seule forme juridique parmi : ENTREPRENANT, EI, SARL, SARL_U, SA.

            Regles imperatives :
            - N'utilise AUCUNE connaissance exterieure : raisonne seulement sur les articles fournis.
            - Justifie la forme retenue ET justifie chaque forme ecartee.
            - Chaque affirmation normative (capital, responsabilite, nombre d'associes...) doit citer sa
              reference source (ex : "AUSCGIE art. 309") dans le champ citations.
            - Si les articles ne suffisent pas a trancher avec certitude, BAISSE le champ confiance
              plutot que d'inventer.

            ARTICLES DISPONIBLES :
            %s
            """;

    /** Un candidat unifie renvoye au controller : la reco + les articles RAG utilises (tracabilite). */
    public record Recommandation(RecoForme reco, List<ResultatRerank> sourcesRag) {
    }

    /**
     * Recommande une forme juridique pour ce profil.
     *  1. transforme le profil en requete de recherche (construireRequete) ;
     *  2. recupere les articles OHADA les plus pertinents (RAG hybride + rerank) ;
     *  3. laisse Claude remplir RecoForme, borne a ces articles (grounding + citation).
     */
    public Recommandation recommander(ProfilPorteur profil) {
        String requete = construireRequete(profil);
        List<ResultatRerank> articles = hybridSearch.rechercheHybride(requete, topK);

        // Contexte = chaque article prefixe de sa [ref], pour que Claude puisse citer tel quel.
        String contexte = articles.stream()
                .map(a -> "[" + a.ref() + "] " + a.texte())
                .collect(Collectors.joining("\n\n"));

        RecoForme reco = chatClient.prompt()
                .system(CONSIGNE_SYSTEME.formatted(contexte))
                .user(decrireProfil(profil))
                .call()
                // On passe NOTRE converter (avec cleaner de fences) au lieu de .entity(RecoForme.class).
                // Mode "prompt" volontaire : le structured output NATIF d'Anthropic refuse le schema de
                // RecoForme (l'enum Forme, reutilisee, devient une reference $defs qu'Anthropic ne
                // resout pas). Le converter injecte le schema dans le prompt et nettoie la reponse.
                .entity(recoConverter);

        return new Recommandation(reco, articles);
    }

    /**
     * Construit la REQUETE de recherche RAG a partir du profil. C'est le pont entre le profil
     * structure et le moteur de recherche : de bons mots-cles ici = les bons articles remontes.
     *
     * Deux principes tires de la Phase 1 :
     *  - On ecrit le vocabulaire JURIDIQUE LITTERAL, pas les sigles : la loi ecrit "societe a
     *    responsabilite limitee", jamais "SARL" -> le moteur lexical ne matche que le mot present.
     *  - On injecte les concepts qui DEPARTAGENT les formes (responsabilite, capital, associes,
     *    statut d'entreprenant) pour faire remonter les articles utiles a la comparaison, pas
     *    seulement ceux qui parlent de l'activite.
     * Le nombre d'associes oriente le vocabulaire : 1 seul -> pistes entreprenant / individuelle /
     * unipersonnelle ; plusieurs -> pistes SARL / SA.
     */
    private String construireRequete(ProfilPorteur profil) {
        StringBuilder q = new StringBuilder("forme juridique societe ");
        q.append(profil.activite()).append(' ');
        if (profil.objectif() != null) {
            q.append(profil.objectif()).append(' ');
        }
        q.append(profil.nbAssocies() <= 1
                ? "entreprenant commercant entreprise individuelle societe unipersonnelle un seul associe "
                : "plusieurs associes societe a responsabilite limitee societe anonyme ");
        q.append("capital social responsabilite des associes apports");
        return q.toString();
    }

    /** Rend le profil lisible par Claude (message utilisateur). Simple mise en forme, pas de logique. */
    private String decrireProfil(ProfilPorteur profil) {
        return """
                Profil du porteur :
                - Activite : %s
                - Nombre d'associes : %d
                - Capital envisage (FCFA) : %s
                - Apports en nature : %s
                - Resident : %s
                - Budget formalites (FCFA) : %s
                - Objectif prioritaire : %s
                """.formatted(
                profil.activite(),
                profil.nbAssocies(),
                profil.capitalEnvisageFcfa() == null ? "non precise" : profil.capitalEnvisageFcfa(),
                profil.apportsEnNature() ? "oui" : "non",
                profil.resident() ? "oui" : "non",
                profil.budgetFormalitesFcfa() == null ? "non precise" : profil.budgetFormalitesFcfa(),
                profil.objectif());
    }
}
