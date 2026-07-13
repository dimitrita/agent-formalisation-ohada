package com.portfolio.ohada.formalisation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    // Injection par constructeur : Spring fournit le VectorStore pgvector deja configure.
    public RagSmokeController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
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
}
