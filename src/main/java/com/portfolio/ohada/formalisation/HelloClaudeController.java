package com.portfolio.ohada.formalisation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smoke test Phase 0 : verifier qu'on parle bien a Claude via Spring AI.
 *
 * @RestController = cette classe expose des endpoints HTTP qui renvoient
 * directement du texte/JSON (pas des pages web).
 */
@RestController
public class HelloClaudeController {

    /** Notre client vers Claude. Cree une seule fois, reutilise a chaque requete. */
    private final ChatClient chatClient;

    /**
     * Injection de dependance : Spring nous fournit AUTOMATIQUEMENT un
     * ChatClient.Builder (cree par le starter spring-ai-starter-model-anthropic,
     * configure avec la cle API lue dans application.properties).
     * On l'utilise pour construire notre ChatClient au demarrage.
     */
    public HelloClaudeController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * GET http://localhost:8080/hello?question=...
     * Envoie la question a Claude et renvoie sa reponse en texte brut.
     */
    @GetMapping("/hello")
    public String hello(
            @RequestParam(defaultValue = "Bonjour Claude, reponds en une phrase.") String question) {
        return chatClient
                .prompt()        // 1. demarre la construction d'une requete
                .user(question)  // 2. definit le message "utilisateur"
                .call()          // 3. appel synchrone au modele (attend la reponse)
                .content();      // 4. extrait la reponse sous forme de String
    }
}
