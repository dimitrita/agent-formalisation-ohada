package com.portfolio.ohada.formalisation;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.portfolio.ohada.formalisation.HybridSearchService.ResultatRerank;

/**
 * Tranche 6 — recherche HYBRIDE (dense + lexical, fusionnee par RRF), reranking cross-encoder.
 *
 * Depuis la Tranche 7, tout le pipeline vit dans {@link HybridSearchService} (reutilise aussi par
 * le guardrail /rag/answer). Ce controller n'est plus qu'un ENDPOINT DE DIAGNOSTIC : il expose la
 * liste rerankee brute (scores visibles) pour comparer cote a cote avec /rag/search-rerank (T5).
 */
@RestController
@RequestMapping("/rag")
public class HybridSearchController {

    private final HybridSearchService hybridSearch;
    private final int topK; // gardes APRES rerank

    public HybridSearchController(HybridSearchService hybridSearch,
            @Value("${rerank.top-k}") int topK) {
        this.hybridSearch = hybridSearch;
        this.topK = topK;
    }

    /**
     * GET /rag/search-hybrid?q=... : dense + lexical -> RRF -> rerank -> top-K.
     * Sortie brute (avec scores) pour le diagnostic / la comparaison sur le golden set.
     */
    @GetMapping("/search-hybrid")
    public List<Map<String, Object>> searchHybrid(@RequestParam String q) {
        return hybridSearch.rechercheHybride(q, topK).stream()
                .map(r -> Map.<String, Object>of(
                        "ref", r.ref(),
                        "score_rerank", r.scoreRerank(),
                        "score_rrf", r.scoreRrf(),
                        "extrait", r.texte()))
                .toList();
    }
}
