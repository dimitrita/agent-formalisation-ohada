package com.portfolio.ohada.formalisation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;

/**
 * Bean du reranker local (cross-encoder ONNX, via LangChain4j).
 *
 * Pourquoi @Lazy : le modele est un fichier sur disque (models/reranker/*), non versionne.
 * Sans @Lazy, l'absence du fichier empecherait TOUTE l'appli de demarrer (ingest, search...).
 * Avec @Lazy, le bean n'est construit qu'au 1er appel de /search-rerank -> le reste tourne sans.
 */
@Configuration
public class RerankConfig {

    @Bean
    @Lazy
    public ScoringModel scoringModel(
            @Value("${rerank.model-path}") String modelPath,
            @Value("${rerank.tokenizer-path}") String tokenizerPath) {
        // OnnxScoringModel(cheminModeleOnnx, cheminTokenizer) : cf doc LangChain4j (context7).
        return new OnnxScoringModel(modelPath, tokenizerPath);
    }
}
