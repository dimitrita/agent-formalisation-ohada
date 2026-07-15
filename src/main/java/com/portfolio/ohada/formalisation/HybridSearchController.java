package com.portfolio.ohada.formalisation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.langchain4j.model.scoring.ScoringModel;
import jakarta.annotation.PostConstruct;

/**
 * Tranche 6 — recherche HYBRIDE (dense + lexical, fusionnee par RRF).
 *
 * Pourquoi : la recherche vectorielle (bge-m3) trouve "par le sens" mais rate les TERMES EXACTS
 * rares ("SARL", "art. 311", "RCCM"). On ajoute une 2e recherche "par le mot" = full-text search
 * Postgres en francais (tsvector/tsquery, BM25-like). Puis on FUSIONNE les deux classements par
 * RRF (Reciprocal Rank Fusion) : on ne compare pas les scores (incomparables : cosinus vs ts_rank),
 * seulement les RANGS. Enfin on rerank la liste fusionnee (cross-encoder bge-reranker-base) -> top-K.
 *
 * A comparer cote a cote avec /rag/search-rerank (Tranche 5, dense seul) sur le golden set.
 */
@RestController
@RequestMapping("/rag")
public class HybridSearchController {

    private final VectorStore vectorStore;
    private final ScoringModel scoringModel;   // @Lazy : cross-encoder charge seulement a l'usage
    private final JdbcTemplate jdbcTemplate;   // pour la requete SQL full-text sur la colonne content
    private final String tableName;            // nom de la table pgvector (source unique = config)
    private final int topN;                    // candidats remontes PAR MOTEUR avant fusion
    private final int topK;                    // gardes APRES rerank

    /** Constante RRF : amortit le poids des premiers rangs. Valeur standard de la litterature = 60. */
    private static final int RRF_K = 60;

    public HybridSearchController(VectorStore vectorStore,
            @Lazy ScoringModel scoringModel,
            JdbcTemplate jdbcTemplate,
            @Value("${spring.ai.vectorstore.pgvector.table-name}") String tableName,
            @Value("${rerank.top-n}") int topN,
            @Value("${rerank.top-k}") int topK) {
        this.vectorStore = vectorStore;
        this.scoringModel = scoringModel;
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.topN = topN;
        this.topK = topK;
    }

    /**
     * Cree l'index full-text s'il n'existe pas (Spring AI ne cree que l'index vectoriel HNSW).
     * GIN sur to_tsvector('french', content) = recherche lexicale rapide (sinon scan sequentiel).
     * Idempotent (IF NOT EXISTS). tableName vient de la config (pas d'input utilisateur) -> sur.
     */
    @PostConstruct
    void ensureFtsIndex() {
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS " + tableName + "_content_fts_idx "
                        + "ON " + tableName + " USING GIN (to_tsvector('french', content))");
    }

    /** Un candidat unifie (peu importe qu'il vienne du dense ou du lexical) : id + texte + citation. */
    private record Candidat(String id, String texte, String ref) {}

    /**
     * GET /rag/search-hybrid?q=... : dense + lexical -> RRF -> rerank -> top-K.
     */
    @GetMapping("/search-hybrid")
    public List<Map<String, Object>> searchHybrid(@RequestParam String q) {
        // Table id -> candidat (union dense + lexical, dedupliquee).
        Map<String, Candidat> parId = new LinkedHashMap<>();

        // 1a. DENSE : top-N par similarite vectorielle bge-m3 (recherche "par le sens").
        List<String> rangsDense = new ArrayList<>();
        List<Document> denseDocs = vectorStore.similaritySearch(SearchRequest.builder()
                .query(q).topK(topN).similarityThreshold(0.0).build());
        for (Document d : denseDocs) {
            parId.putIfAbsent(d.getId(),
                    new Candidat(d.getId(), d.getText(), String.valueOf(d.getMetadata().get("ref"))));
            rangsDense.add(d.getId());
        }

        // 1b. LEXICAL : top-N par full-text francais (recherche "par le mot exact").
        // plainto_tsquery : transforme "obligations comptables" en 'obligation & comptable' (stemming FR).
        List<String> rangsLexical = new ArrayList<>();
        List<Candidat> lexical = jdbcTemplate.query(
                "SELECT id::text AS id, content, metadata->>'ref' AS ref FROM " + tableName
                        + " WHERE to_tsvector('french', content) @@ plainto_tsquery('french', ?)"
                        + " ORDER BY ts_rank(to_tsvector('french', content), plainto_tsquery('french', ?)) DESC"
                        + " LIMIT ?",
                (rs, i) -> new Candidat(rs.getString("id"), rs.getString("content"), rs.getString("ref")),
                q, q, topN);
        for (Candidat c : lexical) {
            parId.putIfAbsent(c.id(), c);
            rangsLexical.add(c.id());
        }

        // 2. RRF : fusion des deux classements par les RANGS.
        Map<String, Double> rrf = new HashMap<>();
        ajouteRangs(rrf, rangsDense);
        ajouteRangs(rrf, rangsLexical);

        // Ordonne les ids par score RRF decroissant = liste fusionnee.
        List<String> fusion = rrf.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();

        // 3. RERANK de la liste fusionnee (cross-encoder) puis top-K.
        return fusion.stream()
                .map(id -> {
                    Candidat c = parId.get(id);
                    double scoreRerank = scoringModel.score(q, c.texte()).content();
                    return Map.<String, Object>of(
                            "ref", c.ref(),
                            "score_rerank", scoreRerank,
                            "score_rrf", rrf.get(id),
                            "extrait", c.texte());
                })
                .sorted(Comparator.comparingDouble(m -> -((Double) m.get("score_rerank"))))
                .limit(topK)
                .toList();
    }

    /**
     * Ajoute la contribution RRF de chaque id d'UN classement au score cumule.
     * idsClasses est ordonne du meilleur (rang 0) au pire. Un id present dans les DEUX
     * classements (dense ET lexical) cumule deux contributions -> il remonte : c'est tout
     * l'interet de la fusion.
     */
    private void ajouteRangs(Map<String, Double> rrf, List<String> idsClasses) {
        for (int rang = 0; rang < idsClasses.size(); rang++) {
            String id = idsClasses.get(rang);
            // Contribution RRF de cet id pour ce classement = 1/(RRF_K + rang).
            // merge(..., Double::sum) : cumule si l'id est deja vu dans l'autre classement.
            rrf.merge(id, 1.0 / (RRF_K + rang), Double::sum);
        }
    }
}
