# Déroulement du projet — journal de bord

But de ce document : me permettre de **relire plus tard** ce qu'on a construit, dans quel ordre,
**qui a fait quoi** (Claude = socle/plomberie ; moi = petites décisions/contributions), et
**quels choix on a faits et pourquoi**. Volontairement synthétique.

Légende : 🏗️ = fait par Claude (socle) · ✋ = fait par moi (contribution/décision) · 💡 = choix + raison.

---

## Phase 0 — Socle Spring Boot + chat Claude ✅

- 🏗️ Squelette Spring Boot (Spring Initializr) + `HelloClaudeController` (endpoint `/hello` qui
  appelle Claude via Spring AI).
- 🏗️ Config `application.properties` : clé Anthropic lue depuis la variable d'env `ANTHROPIC_API_KEY`
  (jamais en dur), modèle `claude-haiku-4-5` (le moins cher, parfait pour tester).
- ✋ J'ai fourni/validé la clé API Anthropic et confirmé que `/hello` répond.
- 💡 **Java + Spring Boot + Spring AI** : stack imposée (portfolio Java). Claude pour le chat car
  déjà dispo ; Haiku car peu coûteux pour la mise au point.

---

## Phase 1 — RAG `ohada_core` (en cours)

Objectif : retrouver les bons **articles OHADA** pour sourcer chaque affirmation (citation obligatoire).

### Tranche 1 — plomberie RAG + smoke test

- 💡 **Embeddings = modèle local ONNX all-MiniLM (384 dims)**. Pourquoi : Anthropic ne fait pas
  d'embeddings ; le modèle local = zéro clé, zéro coût, tourne dans la JVM. Claude reste le chat.
- 💡 **Vector store = PostgreSQL + pgvector**. Pourquoi : choix « entreprise » (vrai SGBD, scalable),
  colle à l'exigence de store externe. Lancé via Docker (`docker-compose.yml`).
- 🏗️ Ajout des dépendances Maven (`spring-ai-starter-model-transformers`,
  `spring-ai-starter-vector-store-pgvector`, driver `postgresql`).
- 🏗️ `docker-compose.yml` : conteneur `pgvector/pgvector:pg17`, base `ohada_rag`.
- 🏗️ Config pgvector dans `application.properties` : `dimensions=384` (⚠️ doit matcher la taille des
  vecteurs MiniLM), distance `COSINE`, index `HNSW`, `initialize-schema=true` (Spring crée la table).
- 🏗️ `RagSmokeController` : `POST /rag/seed` (injecte des articles de test) + `GET /rag/search?q=`
  (recherche sémantique → renvoie `ref` + `score` + extrait).
- ✋ **J'ai écrit le 2e article (`AUSCGIE art. 311`)** avec ses 5 clés de metadata
  (`source, ref, pays, date_maj, url`). But pédagogique : comprendre que `ref` = l'unité de **citation**.
- ✋ J'ai testé `seed` puis `search` → ça marche. Le search renvoie 2 docs = normal (2 seuls en base,
  `topK=3`, pas de seuil).
- 💡 **Metadata homogènes sur tous les chunks** : mêmes clés partout → filtrage et citation fiables.
  Clés incohérentes = 1re cause de RAG cassé en prod.

### Tranche 2 — corpus PDF (en cours)

- 🏗️ Téléchargé les 2 PDF officiels dans `corpus/ohada_core/` : **AUSCGIE 2014** (sociétés, 6 Mo) +
  **AUDCG 2010** (droit commercial, statut entreprenant / RCCM, 67 p).
- 💡 On prend les versions **révisées** (2014 / 2010), pas les originales : c'est la version en vigueur,
  et l'AUDCG 2010 contient le « statut d'entreprenant » cité dans la spec.
- 💡 PDF **non versionnés** dans git (`.gitignore`) → à la place un manifest `corpus/ohada_core/SOURCES.md`
  (URLs + commandes) = corpus reproductible sans alourdir le dépôt public.

**Reste pour finir la Phase 1** : parser les PDF → découper **par article** (chunking) → embeddings +
stockage (pipeline déjà prêt) → puis **reranking** + **guardrail** de citation obligatoire.

---

## Concepts clés (mémo « comment marche un agent »)

- **Embedding** : transformer un texte en vecteur de nombres. Deux textes proches de sens → vecteurs
  proches. C'est ce qui permet la recherche « par le sens » et pas par mots-clés.
- **Vector store** : base qui stocke ces vecteurs et sait retrouver les plus proches d'une question.
- **RAG** (Retrieval-Augmented Generation) : on récupère d'abord les bons passages (retrieval), puis
  le LLM répond **en s'appuyant dessus** → réponses sourcées, moins d'hallucinations.
- **Citation / `ref`** : chaque passage porte sa source ; l'agent doit citer → traçabilité juridique.
