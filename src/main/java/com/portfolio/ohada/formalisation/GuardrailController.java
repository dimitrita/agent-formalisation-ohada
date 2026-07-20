package com.portfolio.ohada.formalisation;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.portfolio.ohada.formalisation.HybridSearchService.ResultatRerank;

/**
 * Tranche 7 — GUARDRAIL (garde-fou anti-hallucination).
 *
 * C'est l'aboutissement de la Phase 1 : un endpoint qui REPOND a une question, mais sous controle.
 * Deux garde-fous, dans cet ordre :
 *
 *  1. ABSTENTION (avant tout appel LLM). On recupere les meilleurs chunks (recherche hybride +
 *     rerank). Si meme le MEILLEUR n'est pas assez pertinent (score rerank sous le seuil), on
 *     REFUSE de repondre ("je ne sais pas"). On n'appelle PAS Claude. Double benefice :
 *       - anti-hallucination : pas de source fiable => pas de reponse inventee ;
 *       - economie : zero token depense quand on ne peut pas repondre.
 *     Cas type = "capital minimum SARL" : la revision AUSCGIE 2014 a supprime ce minimum, la
 *     reponse n'existe pas dans le corpus, les scores rerank sont negatifs -> abstention legitime.
 *
 *  2. CITATION OBLIGATOIRE + GROUNDING. Si on repond, Claude ne rediger QUE depuis les chunks
 *     fournis (prompt systeme strict) et DOIT citer la ref de chaque article utilise. La reponse
 *     porte toujours la liste des sources -> tracabilite, pas d'affirmation hors-source.
 */
@RestController
@RequestMapping("/rag")
public class GuardrailController {

    private final HybridSearchService hybridSearch;
    private final ChatClient chatClient;
    private final int topK;                  // combien de chunks on donne comme contexte a Claude
    private final double seuilAbstention;     // score rerank minimal du meilleur chunk pour oser repondre

    public GuardrailController(HybridSearchService hybridSearch,
            ChatClient.Builder builder,
            @Value("${rerank.top-k}") int topK,
            @Value("${guardrail.rerank-min-score}") double seuilAbstention) {
        this.hybridSearch = hybridSearch;
        this.chatClient = builder.build();
        this.topK = topK;
        this.seuilAbstention = seuilAbstention;
    }

    /**
     * Consigne systeme = le garde-fou anti-hallucination. Elle enferme Claude dans le contexte fourni
     * et impose la citation. %s sera remplace par les articles OHADA retrouves.
     */
    private static final String CONSIGNE_SYSTEME = """
            Tu es un assistant juridique specialise en droit OHADA (formalisation d'entreprise).
            Reponds UNIQUEMENT a partir des articles fournis ci-dessous. N'utilise AUCUNE connaissance exterieure.
            Chaque affirmation doit citer l'article source entre crochets, ex: [AUDCG art. 30].
            Si les articles ne suffisent pas a repondre, dis-le explicitement au lieu d'inventer.
            Sois concis et factuel.

            ARTICLES DISPONIBLES :
            %s
            """;

    /**
     * GET /rag/answer?q=... : recherche hybride -> gate d'abstention -> (si OK) reponse Claude sourcee.
     */
    @GetMapping("/answer")
    public Map<String, Object> answer(@RequestParam String q) {
        // 1. Retrieve : meilleurs chunks tries par score rerank decroissant (le [0] = le plus pertinent).
        List<ResultatRerank> chunks = hybridSearch.rechercheHybride(q, topK);

        // 2. GARDE-FOU #1 : abstention. Si on ne peut pas repondre de facon fiable, on s'arrete ICI.
        if (doitSabstenir(chunks)) {
            return Map.of(
                    "statut", "ABSTENTION",
                    "raison", "Aucune source assez pertinente dans le corpus OHADA (seuil rerank non atteint).",
                    "question", q,
                    "meilleur_score", chunks.isEmpty() ? null : chunks.get(0).scoreRerank(),
                    "sources", List.of());
        }

        // 3. GARDE-FOU #2 : grounding + citation. On assemble le contexte puis on laisse Claude rediger.
        // Chaque chunk est prefixe de sa citation [ref] pour que Claude puisse la reprendre telle quelle.
        String contexte = chunks.stream()
                .map(c -> "[" + c.ref() + "] " + c.texte())
                .collect(Collectors.joining("\n\n"));

        String reponse = chatClient.prompt()
                .system(CONSIGNE_SYSTEME.formatted(contexte))
                .user(q)
                .call()
                .content();

        // La reponse EST accompagnee de ses sources (tracabilite = citation obligatoire cote donnees).
        List<Map<String, Object>> sources = chunks.stream()
                .map(c -> Map.<String, Object>of(
                        "ref", c.ref(),
                        "score_rerank", c.scoreRerank(),
                        "extrait", c.texte()))
                .toList();

        return Map.of(
                "statut", "REPONDU",
                "question", q,
                "reponse", reponse,
                "sources", sources);
    }

    /**
     * Decide si l'agent doit S'ABSTENIR (refuser de repondre) plutot que d'appeler Claude.
     *
     * Regle : on s'abstient si (1) rien n'a ete retrouve, OU (2) meme le MEILLEUR chunk est sous le
     * seuil de pertinence. `chunks` est trie par score rerank decroissant -> chunks.get(0) = le meilleur.
     * On teste isEmpty() AVANT get(0) (sinon IndexOutOfBounds sur une liste vide).
     * Choix : comparaison stricte `<` -> un score exactement egal au seuil est juge suffisant (on repond).
     * (Retourne true = s'abstenir, false = repondre.)
     */
    private boolean doitSabstenir(List<ResultatRerank> chunks) {
        return chunks.isEmpty() || chunks.get(0).scoreRerank() < seuilAbstention;
    }
}
