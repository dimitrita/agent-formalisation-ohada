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
import org.springframework.stereotype.Service;

import dev.langchain4j.model.scoring.ScoringModel;
import jakarta.annotation.PostConstruct;

/**
 * Tranche 6 (extraite en service a la Tranche 7) — recherche HYBRIDE reutilisable.
 *
 * Pourquoi un SERVICE et plus un controller : le guardrail (T7) a besoin EXACTEMENT du meme
 * pipeline de recherche (dense + lexical -> RRF -> rerank). Dupliquer ce code dans deux controllers
 * = bug garanti le jour ou on modifie la recherche d'un cote et pas de l'autre. On isole donc la
 * logique ici ; /rag/search-hybrid (T6) ET /rag/answer (T7) l'appellent.
 *
 * Rappel du pipeline :
 *  - DENSE (bge-m3)   : "meme idee" (paraphrases, synonymes).
 *  - LEXICAL (FTS FR) : "meme mot" (sigles, numeros d'article).
 *  - RRF              : fusionne les deux CLASSEMENTS (par rang, pas par score).
 *  - RERANK           : cross-encoder bge-reranker-base re-note (question, chunk) -> tri final.
 */
@Service
public class HybridSearchService {

    private final VectorStore vectorStore;
    private final ScoringModel scoringModel;   // @Lazy : cross-encoder charge seulement a l'usage
    private final JdbcTemplate jdbcTemplate;   // pour la requete SQL full-text sur la colonne content
    private final String tableName;            // nom de la table pgvector (source unique = config)
    private final int topN;                    // candidats remontes PAR MOTEUR avant fusion

    /** Constante RRF : amortit le poids des premiers rangs. Valeur standard de la litterature = 60. */
    private static final int RRF_K = 60;

    public HybridSearchService(VectorStore vectorStore,
            @Lazy ScoringModel scoringModel,
            JdbcTemplate jdbcTemplate,
            @Value("${spring.ai.vectorstore.pgvector.table-name}") String tableName,
            @Value("${rerank.top-n}") int topN) {
        this.vectorStore = vectorStore;
        this.scoringModel = scoringModel;
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.topN = topN;
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
     * Resultat rerank d'un chunk : sa citation (ref), son extrait, et ses 2 scores.
     * scoreRerank = note du cross-encoder (calibre BGE : >0 pertinent, <0 non) — sert au guardrail.
     * scoreRrf    = score de fusion des rangs (diagnostic, pour comprendre pourquoi il est remonte).
     */
    public record ResultatRerank(String id, String ref, String texte, double scoreRerank, double scoreRrf) {}

    /**
     * Recherche hybride complete : renvoie les topK meilleurs chunks, TRIES par scoreRerank decroissant.
     * Le 1er element = le plus pertinent -> c'est lui que le guardrail teste contre le seuil d'abstention.
     */
    public List<ResultatRerank> rechercheHybride(String q, int topK) {
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

        // 3. RERANK de la liste fusionnee (cross-encoder) puis top-K, trie par score rerank decroissant.
        return fusion.stream()
                .map(id -> {
                    Candidat c = parId.get(id);
                    double scoreRerank = scoringModel.score(q, c.texte()).content();
                    return new ResultatRerank(id, c.ref(), c.texte(), scoreRerank, rrf.get(id));
                })
                .sorted(Comparator.comparingDouble(ResultatRerank::scoreRerank).reversed())
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
