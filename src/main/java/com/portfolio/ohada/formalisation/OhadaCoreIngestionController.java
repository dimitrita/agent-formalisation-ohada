package com.portfolio.ohada.formalisation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingestion du corpus ohada_core (Phase 1, tranche 3).
 *
 * Pipeline : PDF -> texte brut -> DECOUPAGE PAR ARTICLE -> Document(+metadata) -> vector store.
 *
 * Pourquoi "par article" : l'unite de citation OHADA est l'article (ex. "AUSCGIE art. 311").
 * Un chunk = un article = une reference citable. C'est ce qui rendra la citation fiable plus tard.
 */
@RestController
@RequestMapping("/rag")
public class OhadaCoreIngestionController {

    private final VectorStore vectorStore;

    // Acces SQL direct a la table pgvector (nom par defaut : vector_store, colonne metadata jsonb).
    // Sert aux endpoints de comptage : le VectorStore de Spring AI n'expose pas de "count".
    private final JdbcTemplate jdbcTemplate;

    // Nom de la table pgvector, lu depuis application.properties (source unique de verite,
    // meme valeur que celle utilisee par le VectorStore). Interpole dans le SQL de comptage.
    private final String tableName;

    // Chemin du PDF AUSCGIE (partage par l'ingestion et le diagnostic).
    private static final String AUSCGIE_PDF = "corpus/ohada_core/AUSCGIE-2014.pdf";

    // Motif de detection d'un debut d'article OHADA. Partage (DRY) entre decoupe et diagnostic.
    //   (?i) casse ignoree, (?m) ^ = debut de ligne, (?:article|art\.) le mot ou l'abreviation,
    //   \s+(\d+) le numero capture en groupe 1.
    private static final Pattern DEBUT_ARTICLE = Pattern.compile("(?im)^\\s*(?:article|art\\.)\\s+(\\d+)");

    public OhadaCoreIngestionController(VectorStore vectorStore, JdbcTemplate jdbcTemplate,
            @Value("${spring.ai.vectorstore.pgvector.table-name}") String tableName) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
    }

    /**
     * GET /rag/count-db : combien de chunks reellement STOCKES en base, PAR source.
     *
     * Transverse + auto-decouvert : chaque nouveau PDF ingere (nouvelle valeur de "source")
     * apparait tout seul, sans ajouter une ligne de code. La cle = la colonne metadata->>'source'.
     * C'est la vraie preuve du stockage (contrairement au diagnostic qui, lui, relit le PDF brut).
     */
    @GetMapping("/count-db")
    public Map<String, Object> countParSource() {
        List<Map<String, Object>> lignes = jdbcTemplate.queryForList(
                "SELECT metadata->>'source' AS source, count(*) AS n FROM " + tableName
                        + " GROUP BY metadata->>'source'");

        Map<String, Object> resultat = new LinkedHashMap<>();
        long total = 0;
        for (Map<String, Object> ligne : lignes) {
            long n = ((Number) ligne.get("n")).longValue();
            resultat.put((String) ligne.get("source"), n);
            total += n;
        }
        resultat.put("total", total);
        return resultat;
    }

    /**
     * GET /rag/count-db/{source} : combien de chunks pour UNE source (ex. /rag/count-db/AUSCGIE).
     */
    @GetMapping("/count-db/{source}")
    public Map<String, Object> countPourSource(@PathVariable String source) {
        // Le ? lie 'source' comme donnee (pas comme SQL) -> anti-injection.
        Long n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + tableName + " WHERE metadata->>'source' = ?", Long.class, source);
        return Map.of("source", source, "count", n == null ? 0L : n);
    }

    /** POST /rag/ingest/auscgie : lit le PDF AUSCGIE, decoupe par article, stocke. */
    @PostMapping("/ingest/auscgie")
    public String ingestAuscgie() {
        return ingest(AUSCGIE_PDF, "AUSCGIE", "2014-01-30", "https://www.ohada.org/auscgie");
    }

    /**
     * GET /rag/ingest/auscgie/diagnostic : ANALYSE le decoupage SANS rien stocker.
     * But : comprendre le sur-decoupage (1142 matches alors que l'AUSCGIE s'arrete a l'art. 920).
     * On veut prouver la cause (doublons de sommaire ? lignes trop courtes ?) avant de corriger.
     */
    @GetMapping("/ingest/auscgie/diagnostic")
    public Map<String, Object> diagnosticAuscgie() {
        String texte = lireTexteComplet(AUSCGIE_PDF);

        // On rejoue le regex, mais on ne garde QUE (numero d'article, longueur du chunk).
        Matcher m = DEBUT_ARTICLE.matcher(texte);
        List<Integer> debuts = new ArrayList<>();
        List<Integer> numeros = new ArrayList<>();
        while (m.find()) {
            debuts.add(m.start());
            numeros.add(Integer.parseInt(m.group(1)));
        }

        // Longueur de chaque chunk = distance jusqu'au debut suivant.
        List<Integer> longueurs = new ArrayList<>();
        for (int i = 0; i < debuts.size(); i++) {
            int fin = (i + 1 < debuts.size()) ? debuts.get(i + 1) : texte.length();
            longueurs.add(fin - debuts.get(i));
        }

        // Combien de numeros distincts ? Combien apparaissent plusieurs fois (= doublons TOC) ?
        Map<Integer, Long> occurrences = numeros.stream()
                .collect(Collectors.groupingBy(n -> n, Collectors.counting()));
        long distincts = occurrences.size();
        long numerosEnDouble = occurrences.values().stream().filter(c -> c > 1).count();

        // Combien de chunks tres courts (typiquement une ligne de sommaire) ?
        long chunksCourts = longueurs.stream().filter(l -> l < 120).count();

        // Quelques exemples de numeros dupliques (pour inspection).
        List<Integer> exemplesDoublons = occurrences.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .limit(10)
                .toList();

        Map<String, Object> rapport = new LinkedHashMap<>();
        rapport.put("total_matches", numeros.size());
        rapport.put("numeros_distincts", distincts);
        rapport.put("numero_max", numeros.stream().mapToInt(Integer::intValue).max().orElse(0));
        rapport.put("numeros_apparaissant_plusieurs_fois", numerosEnDouble);
        rapport.put("chunks_courts_moins_120_car", chunksCourts);
        rapport.put("exemples_numeros_dupliques", exemplesDoublons);
        return rapport;
    }

    /** Lit un PDF et recolle le texte de toutes ses pages en une seule chaine. */
    private String lireTexteComplet(String cheminPdf) {
        PagePdfDocumentReader reader = new PagePdfDocumentReader(new FileSystemResource(cheminPdf));
        return reader.read().stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Etape 1 (lecture) + etape 2 (decoupage) + etape 3 (stockage), IDEMPOTENT.
     *
     * Idempotent = ré-appeler ne crée pas de doublons : on PURGE d'abord tous les chunks de cette
     * source, PUIS on réécrit. Sinon vectorStore.add() = INSERT pur (UUID aleatoire par Document)
     * -> chaque appel empilerait 906 lignes de plus. Ré-ingest = remplacer, pas empiler.
     */
    private String ingest(String cheminPdf, String source, String dateMaj, String url) {
        String texteComplet = lireTexteComplet(cheminPdf);
        List<Document> articles = decoupeParArticle(texteComplet, source, dateMaj, url);

        int supprimes = jdbcTemplate.update(
                "DELETE FROM " + tableName + " WHERE metadata->>'source' = ?", source);
        vectorStore.add(articles);

        return articles.size() + " article(s) ingere(s) depuis " + source
                + " (" + supprimes + " ancien(s) supprime(s)).";
    }

    /**
     * Un debut d'article repere dans le texte, AVANT deduplication.
     * Le sommaire du PDF genere de faux debuts (le meme numero apparait 2 fois : sommaire + vrai article).
     */
    private record ArticleBrut(int numero, String contenu) {}

    /**
     * Decoupe le texte complet en un Document par article, en 3 etapes :
     *   1. REPERER  : chaque debut d'article -> (numero, texte integral jusqu'au debut suivant).
     *   2. DEDUPLIQUER : un seul chunk par numero (le sommaire cree des doublons courts a jeter).
     *   3. MAPPER   : ArticleBrut -> Document(+metadata homogenes citables).
     *
     * Le NUMERO capture (groupe 1 du regex) construit la reference de citation (ex. "AUSCGIE art. 311").
     */
    private List<Document> decoupeParArticle(String texte, String source, String dateMaj, String url) {

        // 1. REPERER : meme motif que le diagnostic (constante partagee DEBUT_ARTICLE).
        Matcher m = DEBUT_ARTICLE.matcher(texte);
        List<Integer> debuts = new ArrayList<>();
        List<Integer> numeros = new ArrayList<>();
        while (m.find()) {
            debuts.add(m.start());
            numeros.add(Integer.parseInt(m.group(1)));
        }

        List<ArticleBrut> bruts = new ArrayList<>();
        for (int i = 0; i < debuts.size(); i++) {
            int fin = (i + 1 < debuts.size()) ? debuts.get(i + 1) : texte.length();
            String contenu = texte.substring(debuts.get(i), fin).trim();
            bruts.add(new ArticleBrut(numeros.get(i), contenu));
        }

        // 2. DEDUPLIQUER : sinon la meme "ref" existe en double dans le vector store -> citation cassee.
        Collection<ArticleBrut> uniques = deduplique(bruts);

        // 3. MAPPER : chaque article unique -> un Document avec metadata homogenes.
        return uniques.stream()
                .map(a -> new Document(a.contenu(), Map.of(
                        "source", source,
                        "ref", source + " art. " + a.numero(),
                        "pays", "OHADA",
                        "date_maj", dateMaj,
                        "url", url)))
                .toList();
    }

    /**
     * Ne garder qu'UN seul chunk par numero d'article : le vrai texte de loi, pas la ligne
     * de sommaire (courte) qui porte le meme numero.
     */
    private Collection<ArticleBrut> deduplique(List<ArticleBrut> bruts) {
        Map<Integer, ArticleBrut> parNumero = new LinkedHashMap<>();
        for (ArticleBrut a : bruts) {
            ArticleBrut retenu = parNumero.get(a.numero());
            // Premier vu, ou plus long que l'actuel -> on (re)tient celui-la. Le sommaire (court) perd.
            if (retenu == null || a.contenu().length() > retenu.contenu().length()) {
                parNumero.put(a.numero(), a);
            }
        }
        return parNumero.values();
    }
}
