# CLAUDE.md — Projet Agent Formalisation OHADA (portfolio agentique Java)

## Objectif du projet

Construire un projet agentique de qualité **entreprise** pour alimenter un portfolio et
postuler comme **Chef de projet IA agentique technico-fonctionnel / Architecte IA / Consultant IA**.

Le projet de référence : **agent de formalisation d'entreprise en zone OHADA** (voir
`SPEC_V1_agent_formalisation_ohada.md`). La spec est décrite avec LangGraph (Python) mais
**l'implémentation se fait en Java**.

## Stack technique imposée (Java uniquement)

| Couche | Techno |
|---|---|
| Application / backbone | **Spring Boot** |
| LLM / embeddings / RAG / vector store | **Spring AI** (ou **LangChain4j** selon le module) |
| Orchestration graphe stateful (nœuds, edges conditionnels, fork-join, HITL, checkpointer) | **LangGraph4j** |
| Framework agent (optionnel, exploration) | **Embabel** |

- **Pas de Python** pour l'implémentation. La spec Python sert de contrat fonctionnel, on la porte en Java.
- Un seul framework LLM par module (ne pas mélanger Spring AI ET LangChain4j sur la même couche sans raison).

## Règle NON NÉGOCIABLE : context7 avant d'écrire du code

**Toujours interroger context7 AVANT d'écrire du code utilisant une librairie/framework**
(Spring AI, LangChain4j, LangGraph4j, Embabel, Spring Boot, etc.).

Pourquoi : ces librairies évoluent vite, les API changent. S'appuyer sur la mémoire du modèle
= risque d'hallucination d'API obsolètes ou inexistantes.

Procédure :
1. `resolve-library-id` pour trouver l'ID context7 de la librairie.
2. `query-docs` pour récupérer la doc/API à jour du point précis à coder.
3. Écrire le code en s'appuyant sur cette doc, pas sur la mémoire.

## Exigences entreprise à respecter (fil rouge)

Ces exigences doivent apparaître concrètement dans le projet — c'est ce qui distingue un
prototype d'un travail de niveau entreprise :

- **RAG + reranking** : retrieval avec reranking des chunks (pas juste top-k brut).
- **Évaluation** : pipeline d'éval (linters déterministes bloquants + LLM-as-judge). Cf §8 spec.
- **Observabilité** : traces/spans par nœud + sous-spans RAG, scores attachés (Langfuse ou équivalent).
- **Scalabilité** : conception qui tient la charge (stateless où possible, checkpointer externe, etc.).
- **Guardrails** : garde-fous (activité réglementée → blocage, citation obligatoire, anti-hallucination coûts).
- **HITL (Human-in-the-loop)** : points d'approbation humaine (interrupts LangGraph4j + UI de gate).

## Style de travail (IMPÉRATIF)

- **Progressif** : on avance par petits modules, un à la fois. Pas de big-bang.
- **Pédagogique niveau débutant** : chaque bout de code est expliqué pas à pas.
- L'utilisateur doit pouvoir **lire, comprendre et expliquer** tout le code produit.
  → privilégier la clarté sur l'astuce ; commenter les parties non évidentes ; éviter la magie implicite.
- Avant un nouveau module : rappeler où on en est, ce qu'on va faire, pourquoi.

## Notes

- RTK (voir CLAUDE.md global) : préfixer les commandes shell par `rtk` quand un filtre existe.
- Un dossier voisin `Agent creation entreprise/` existe (possible version Python) — ne pas y toucher sans demande explicite.
