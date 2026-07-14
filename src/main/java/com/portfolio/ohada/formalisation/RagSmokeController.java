package com.portfolio.ohada.formalisation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.langchain4j.model.scoring.ScoringModel;

/**
 * Smoke test de la couche RAG (Phase 1).
 *
 * But : prouver que la plomberie tourne AVANT de brancher les vrais PDF OHADA.
 * Pipeline demontre ici : texte -> embeddings (all-MiniLM, en local) -> stockage
 * dans pgvector -> recherche semantique par similarite.
 *
 * Le bean {@link VectorStore} est cree automatiquement par Spring AI a partir de
 * la config pgvector (application.properties). On l'injecte, on ne le construit pas.
 */
@RestController
@RequestMapping("/rag")
public class RagSmokeController {

    private final VectorStore vectorStore;

    // Reranker local (cross-encoder ONNX). @Lazy : injecte sans forcer sa construction au demarrage
    // (le modele sur disque peut etre absent tant qu'on n'appelle pas /search-rerank).
    private final ScoringModel scoringModel;

    // top-n = candidats remontes de pgvector AVANT rerank ; top-k = gardes APRES rerank.
    private final int topN;
    private final int topK;

    // Injection par constructeur : Spring fournit le VectorStore pgvector deja configure.
    public RagSmokeController(VectorStore vectorStore,
            @Lazy ScoringModel scoringModel,
            @Value("${rerank.top-n}") int topN,
            @Value("${rerank.top-k}") int topK) {
        this.vectorStore = vectorStore;
        this.scoringModel = scoringModel;
        this.topN = topN;
        this.topK = topK;
    }

    /**
     * POST /rag/seed : injecte quelques faux articles OHADA dans la collection ohada_core.
     * A appeler UNE fois (chaque appel ajoute des lignes -> doublons sinon).
     *
     * Chaque {@link Document} porte un texte + des metadata. Les metadata suivent le
     * contrat de la spec (section 2) : {source, ref, pays, date_maj, url}. Le champ "ref"
     * est l'unite de citation (ex. "AUSCGIE art. 311") qui alimentera plus tard le champ
     * citations de l'etat de l'agent.
     */
    @PostMapping("/seed")
    public String seed() {
        List<Document> docs = new ArrayList<>();

        // Exemple COMPLET a imiter : un article + ses 5 metadata obligatoires.
        docs.add(new Document(
                "La societe a responsabilite limitee (SARL) est une societe dans laquelle "
                        + "les associes ne sont responsables des dettes sociales qu'a concurrence "
                        + "de leurs apports.",
                Map.of(
                        "source", "AUSCGIE",
                        "ref", "AUSCGIE art. 309",
                        "pays", "OHADA",
                        "date_maj", "2014-01-30",
                        "url", "https://www.ohada.org/auscgie")));

        // 2e article (ajoute par l'utilisateur) : meme schema de metadata que ci-dessus.
        Document nouveauDoc = new Document(
                "le capital de la SARL est " 
                        + " librement fixe par les associes dans les statuts ",
                Map.of(
                        "source", "AUSCGIE",
                        "ref", "AUSCGIE art. 311",
                        "pays", "OHADA",
                        "date_maj", "2014-01-30",
                        "url", "https://www.ohada.org/auscgie"));
        docs.add(nouveauDoc);

        vectorStore.add(docs);
        return docs.size() + " document(s) ajoute(s) dans la collection ohada_core.";
    }

    /**
     * GET /rag/search?q=... : recherche semantique.
     * On embed la question, pgvector renvoie les articles les plus proches (cosine).
     */
    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam String q) {
        SearchRequest request = SearchRequest.builder()
                .query(q)
                .topK(3)                    // on veut les 3 plus proches
                .similarityThreshold(0.0)   // 0.0 = aucun filtre, on veut VOIR tous les scores
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        // On renvoie l'essentiel : la citation (ref), le score de similarite, un extrait.
        return results.stream()
                .map(doc -> Map.<String, Object>of(
                        "ref", doc.getMetadata().get("ref"),
                        "score", doc.getScore(),
                        "extrait", doc.getText()))
                .toList();
    }

    /** Un candidat apres reranking : le document + son score de pertinence recalcule. */
    private record Reclasse(Document doc, double scoreRerank) {}

    /**
     * GET /rag/search-rerank?q=... : recherche AVEC reranking.
     *
     * Pipeline : retrieve LARGE (top-N, rappel eleve mais bruite) -> le cross-encoder RE-NOTE
     * chaque paire (question, chunk) -> on garde les top-K vraiment pertinents. A comparer
     * cote a cote avec /search (sans rerank) pour voir le gain.
     */
    @GetMapping("/search-rerank")
    public List<Map<String, Object>> searchRerank(@RequestParam String q) {
        // 1. RETRIEVE LARGE : top-N candidats (on ratisse large, le rerank fera le tri fin).
        SearchRequest request = SearchRequest.builder()
                .query(q)
                .topK(topN)
                .similarityThreshold(0.0)
                .build();
        List<Document> candidats = vectorStore.similaritySearch(request);

        // 2. + 3. RERANK puis TOP-K.
        List<Reclasse> meilleurs = rerank(q, candidats);

        // 4. Reponse : ref + score de rerank + score vectoriel d'origine + extrait.
        return meilleurs.stream()
                .map(r -> Map.<String, Object>of(
                        "ref", r.doc().getMetadata().get("ref"),
                        "score_rerank", r.scoreRerank(),
                        "score_vectoriel", r.doc().getScore(),
                        "extrait", r.doc().getText()))
                .toList();
    }

    /**
     * Cœur du reranking : note chaque candidat avec le cross-encoder, trie, garde les top-K.
     */
    private List<Reclasse> rerank(String q, List<Document> candidats) {
        // Chaque candidat -> Reclasse(doc, score cross-encoder), trie DECROISSANT, garde topK.
        return candidats.stream()
                .map(d -> new Reclasse(d, scoringModel.score(q, d.getText()).content()))
                .sorted(Comparator.comparingDouble(Reclasse::scoreRerank).reversed())
                .limit(topK)
                .toList();
    }
}
