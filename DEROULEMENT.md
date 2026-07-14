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

- ✋ J'ai demandé d'ajouter un doc **Cameroun**.
- 💡 Cameroun = **plugin pays** → nouvelle collection `cm_procedures` (metadata `pays=CM`), PAS dans
  `ohada_core`. Rappel archi : `ohada_core` = transverse (droit OHADA commun aux 17 pays) ;
  les **démarches** (guichet, coûts, délais) sont spécifiques pays. Ajouter un pays = ajouter une
  collection procédures sans toucher au reste (cf spec §V2). pgvector = simple **extension** de Postgres.
- 🏗️ `corpus/cm_procedures/cm_creation_entreprise.md` : formalités CFCE (étapes/délais/coûts/pièces),
  source officielle minfi.gov.cm + manifest `SOURCES.md`.

### Tranche 3 — chunking par article + comptage en base ✅

- 🏗️ Dépendance Maven `spring-ai-pdf-document-reader` (lecture PDF via PDFBox).
- 🏗️ `OhadaCoreIngestionController` : pipeline `PDF → texte → découpe PAR ARTICLE → Document(+metadata) → vector store`.
  - `POST /rag/ingest/auscgie` : ingère l'AUSCGIE.
  - `GET /rag/ingest/auscgie/diagnostic` : **analyseur du PDF brut** (relit le PDF, ne touche PAS la base) —
    a servi à prouver le bug de sur-découpage.
- 💡 **Bug trouvé** : le regex `^(article|art.) \d+` trouve **1112 débuts** alors que l'AUSCGIE s'arrête
  à l'**art. 920** (le PDF répète des blocs → 83 numéros en double). `chunks_courts:0` → pas des lignes de
  sommaire courtes, mais de vrais blocs répétés.
- 💡 **Fix = déduplication par numéro d'article**, on garde le **chunk le plus long** (le vrai texte de loi
  bat un bloc répété plus court). Résultat : **906 articles** ingérés (`POST` le confirme).
  Pourquoi ça compte : 1 numéro = 1 `ref` unique = citation fiable (doublons = 1re cause de RAG cassé).
- ✋ J'ai écrit le cœur de la dédup (garder le plus long) puis, plus tard, demandé à Claude de finir.
- 🏗️ Comptage **en base** (preuve de stockage réel, ≠ diagnostic PDF) via `JdbcTemplate` (le `VectorStore`
  Spring AI n'expose pas de `count`) :
  - `GET /rag/count-db` : nb de chunks **par source**, `GROUP BY metadata->>'source'` → **auto-découvert**
    (chaque nouveau PDF ingéré apparaît sans code en plus) + `total`.
  - `GET /rag/count-db/{source}` : nb pour une source, requête **paramétrée `?`** (anti-injection SQL).
- 💡 Table pgvector : nom **par défaut** = `vector_store`, mais ici configuré à **`ohada_core`**
  (`application.properties`). Le comptage lit ce nom via `@Value` (source unique de vérité, pas de nom en dur).
  Metadata = colonne `jsonb`. Vérifié via context7 avant de coder.
- 💡 **⚠️ Idempotence de l'ingestion (leçon RAG importante)** : `vectorStore.add()` = **INSERT** (UUID
  aléatoire par chunk). Ré-ingérer le **même PDF** ⇒ **doublons** en base (2 runs = 1812 lignes au lieu de 906).
  Un RAG a **besoin d'une stratégie anti-doublon** dès qu'un document peut être ré-ingéré (redéploiement,
  correction, mise à jour de source). Choix retenu : ingest **idempotent** = `DELETE WHERE source=?` **avant**
  le `add` ⇒ « ré-ingérer = **remplacer**, pas empiler ». Bonus : purge aussi les articles disparus si le PDF
  change. (Alternative possible : id de `Document` **déterministe** = hash(source+numéro) → upsert au lieu de
  delete+insert.)

### Tranche 4 — reranking (cross-encoder local) ✅

- 🏗️ Ingestion **AUDCG 2010** (`POST /rag/ingest/audcg`) → 307 articles. Corpus `ohada_core` = 1213 chunks.
- ✋ J'ai testé `GET /rag/search` (baseline, sans rerank) sur 3 vraies questions. Constat : top-1 **faux 2 fois
  sur 3** (capital "SARL" → renvoie art. SA ; "obligations comptables" → renvoie *commissaire aux comptes*),
  fuites cross-doc, bruit en #2/#3. → prouve le besoin de reranking.
- 💡 **Reranker = cross-encoder local ONNX** (`cross-encoder/ms-marco-MiniLM-L-6-v2` via LangChain4j
  `OnnxScoringModel`). Choix cohérent avec les embeddings locaux : zéro clé, zéro réseau. Couche **distincte**
  du vector store (Spring AI reste sur l'embedding/retrieval) — usage légitime de LangChain4j sur SA propre couche.
- 🏗️ Dépendance `dev.langchain4j:langchain4j-onnx-scoring` + bean `ScoringModel` (`RerankConfig`, **@Lazy** :
  l'app démarre même sans le modèle sur disque).
- 🏗️ Endpoint `GET /rag/search-rerank` : **retrieve LARGE (top-N=20)** → cross-encoder re-note chaque paire
  (question, chunk) → **garde top-K=3**. Renvoie `score_rerank` ET `score_vectoriel` (comparaison visible).
- ✋ J'ai écrit le cœur du rerank (map→score→sort desc→limit topK) puis demandé à Claude de finir.
- 💡 Modèle **non versionné** (fichiers lourds) → manifest `models/reranker/SOURCES.md` (téléchargement HF)
  + `.gitignore` (`models/**/*.onnx`, `tokenizer.json`). Même logique que les PDF.
- 💡 **Résultat mesuré** : sur "obligations comptables du commerçant", le piège *commissaire aux comptes*
  (AUSCGIE 697) passe de **#1 → #3**, remplacé par un article AUDCG commerçant. Précision nettement meilleure.
  ⚠️ Limite : le rerank réordonne seulement le top-N retrieved → il soigne la **précision**, pas le **recall**
  (si l'article idéal n'est pas dans les 20 candidats, il reste absent). Leviers futurs : top-N ↑, nettoyage chunk.

**Reste pour finir la Phase 1** : **guardrail** de citation obligatoire (anti-hallucination). Pistes bonus :
nettoyage des chunks (résidus PDF : en-têtes/pieds de page), montée du top-N.

---

## Concepts clés & apprentissages

➡️ Déplacés dans **`APPRENTISSAGES.md`** (savoir transverse durable, relisable à froid).
Ce journal reste chronologique ; les leçons de fond (embedding, chunking, idempotence, metadata,
archi corpus) vivent là-bas.
