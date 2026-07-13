# Agent de formalisation d'entreprise — zone OHADA (Java / agentique)

Projet **portfolio** : un système multi-agents de niveau entreprise qui accompagne la
**création et la formalisation d'une entreprise en zone OHADA** (V1 : Côte d'Ivoire / CEPICI).
À partir du profil d'un porteur de projet, l'agent recommande une **forme juridique**, liste
les **démarches** et génère un **dossier** — chaque affirmation normative étant **obligatoirement
sourcée** (article d'Acte uniforme OHADA ou page officielle).

> Objectif : démontrer des compétences de **chef de projet IA agentique / architecte IA /
> consultant IA** sur une stack **100 % Java**, avec les exigences réelles de la production.

## Pourquoi ce projet est « entreprise » et pas un prototype

| Exigence | Mise en œuvre |
|---|---|
| **RAG + reranking** | Retrieval sur corpus juridique OHADA, reranking des chunks (pas un simple top-k brut) |
| **Guardrails** | Citation obligatoire, blocage des activités réglementées, anti-hallucination des coûts |
| **HITL** (human-in-the-loop) | Points d'approbation humaine (interrupts LangGraph4j + UI de validation) |
| **Évaluation** | Pipeline d'éval : linters déterministes bloquants + LLM-as-judge |
| **Observabilité** | Traces/spans par nœud + sous-spans RAG, scores attachés |
| **Scalabilité** | Sans état où c'est possible, checkpointer externe, vector store SGBD |

## Stack technique

| Couche | Techno |
|---|---|
| Application / backbone | **Spring Boot 4** |
| LLM (chat) | **Spring AI** + **Anthropic Claude** |
| Embeddings | **Transformers ONNX local** (all-MiniLM-L6-v2, 384 dims) — aucun appel réseau |
| Vector store | **PostgreSQL + pgvector** |
| Orchestration graphe stateful | **LangGraph4j** (nœuds, edges conditionnels, fork-join, HITL, checkpointer) |
| Java | **25** (Temurin), build via wrapper `./mvnw` |

## État d'avancement

- ✅ **Phase 0** — socle : Spring Boot + Spring AI, endpoint de chat Claude opérationnel.
- 🚧 **Phase 1** — RAG `ohada_core` : embeddings locaux + pgvector câblés, smoke test de bout
  en bout (`/rag/seed`, `/rag/search`). Suite : ingestion des PDF officiels, chunking par
  article, reranking, guardrail de citation.
- ⬜ Phases 2→7 : nœud forme juridique, orchestration LangGraph4j, fork-join, HITL,
  observabilité (Langfuse), évaluation.

## Démarrer en local

Prérequis : JDK 25, Docker, une clé API Anthropic.

```bash
# 1. Variable d'environnement (jamais commitée)
export ANTHROPIC_API_KEY=sk-ant-...

# 2. Base vectorielle
docker compose up -d

# 3. Application
./mvnw spring-boot:run
```

Smoke test RAG :

```bash
curl -X POST http://localhost:8080/rag/seed
curl "http://localhost:8080/rag/search?q=capital%20minimum%20SARL"
```

## Architecture fonctionnelle

Le contrat fonctionnel de référence est décrit dans
[`SPEC_V1_agent_formalisation_ohada.md`](SPEC_V1_agent_formalisation_ohada.md) (spécifié en
LangGraph, porté en Java). Le graphe enchaîne : profilage → forme juridique → démarches →
rédaction, avec deux points d'approbation humaine et une accumulation des citations par nœud.

---

*Projet de portfolio. Le code privilégie la clarté et l'explicabilité (chaque brique est
commentée et justifiée) plutôt que l'astuce.*
