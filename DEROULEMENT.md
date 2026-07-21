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

### Tranche 5 — modèles multilingues (embeddings + rerank) ✅

- 💡 **Bascule du duo anglophone → multilingue FR** : embeddings `all-MiniLM` (384d) → **bge-m3** (1024d) ;
  rerank `ms-marco-MiniLM` → **bge-reranker-base**. Raison : corpus = droit OHADA **en français**, les
  modèles anglophones sous-performent (cf ratés Tranche 4).
- 💡 **Reranker = `bge-reranker-base`, PAS `-v2-m3`** : le v2-m3 (meilleur) n'a **pas d'export ONNX
  single-file** dispo (BAAI = safetensors only). `base` est multilingue, single-file, gros progrès vs
  l'anglophone. Upgrade v2-m3 = plus tard SI l'éval le justifie (nécessiterait un export Python/optimum).
- 🏗️ Manifests `models/embedding/SOURCES.md` (nouveau) + `models/reranker/SOURCES.md` (maj) — recettes
  de téléchargement reproductibles. `.gitignore` couvrait déjà `models/**`.
- ✋ J'ai téléchargé les 2 modèles.
- 💡 **Piège ONNX poids externes** : bge-m3 full-precision >2 Go → poids dans `model.onnx_data` séparé,
  qu'ONNX Runtime cherche par rapport au **CWD** (pas au `.onnx`) → crash `file introuvable`. **Fix** :
  prendre une variante **single-file < 2 Go** = `model_quantized.onnx` (int8, CPU-friendly, sortie 1024d).
- 🏗️ Config `application.properties` : `spring.ai.embedding.transformer.onnx.model-uri` + `tokenizer.uri`,
  `dimensions 384 → 1024`. Pooling laissé au **défaut Spring AI (mean)** — bge-m3 est CLS mais mean reste
  fonctionnel ; à raffiner (variante `sentence_transformers.onnx`) seulement si l'éval déçoit.
- ✋ **Ré-embed du corpus** : `DROP TABLE ohada_core` (colonne figée `vector(384)`) → Spring recrée en
  `vector(1024)` au boot → ré-ingestion AUSCGIE + AUDCG. **Lent** (~plusieurs min) : bge-m3 ≈ 568M params
  (25× MiniLM) sur CPU. Diagnostic en cours d'ingestion (Claude) : DB à 0 = normal (INSERT batch en fin),
  process Java à 4 Go RAM + CPU qui grimpe = ça calcule, pas figé.
- 💡 **Résultats mesurés sur les 3 questions golden** (endpoint `/rag/search-rerank`) :
  - **Q2 « obligations comptables du commerçant »** : Tranche 4 → `AUSCGIE 697 (commissaire aux comptes)`
    en #1 (fuite cross-doc). Tranche 5 → **top-3 = 100% AUDCG commerçant**, et `art. 13` (idéal, **absent**
    du top-N en T4) est **récupéré** (#2, rerank +3.22). ✅ **Gain de recall réel**, pas juste du tri.
  - **Q3 « statut d'entreprenant »** : plus aucune fuite cross-doc, tout dans le Titre 2 AUDCG.
  - **Q1 « capital minimum SARL »** : les 3 résultats ont un `score_rerank` **négatif** (-0.39 / -1.73 /
    -1.96). ✋ **Observation user** : l'AUSCGIE **révisé 2014** ne fixe plus de minimum SARL (art. 311
    renvoie aux statuts) → la bonne réponse n'existe pas → le RAG **devrait s'abstenir**.
- 💡 **Trouvaille = seuil d'abstention gratuit** : les rerankers **BGE** sont calibrés `>0 ≈ pertinent,
  <0 ≈ non pertinent`. Un **seuil à ~0 sur le score rerank** = mécanisme d'abstention → motive
  concrètement le **guardrail Tranche 7** (chiffres réels, pas théorie).

### Tranche 6 — recherche hybride (dense + lexical, fusion RRF) ✅

**L'idée en une phrase** : jusqu'ici on cherchait seulement « par le sens » (bge-m3). On ajoute une 2e
recherche « par le mot exact » (comme un Ctrl+F malin), et on **marie** les deux classements. But :
rattraper les termes précis (« SARL », « art. 311 », « RCCM ») que le sens seul dilue et rate.

- 💡 **Deux moteurs complémentaires.** Dense (bge-m3) = « même **idée** » (paraphrases, synonymes).
  Lexical = « même **mot** » (sigles, numéros d'article). La loi est pleine de termes exacts non
  négociables → il faut les deux.
- 💡 **Moteur lexical = full-text Postgres natif en `french`** (pas d'extension à installer). Postgres
  transforme chaque texte en liste de mots-racines (« comptables », « comptable » → même racine `compt`)
  et compare à la question. C'est du « BM25-like » : suffisant pour ~1213 articles. (Vrai BM25 = extension
  ParadeDB, gardée en réserve si l'éval le réclame.)
- 💡 **Fusion = RRF (Reciprocal Rank Fusion).** Les 2 moteurs donnent des scores **incomparables**
  (cosinus ∈ [0,1] vs ts_rank échelle libre). RRF ignore les scores, ne regarde que la **place** (rang) :
  chaque article gagne `1/(60+rang)` dans chaque liste, on additionne. Un article **bien placé dans les
  deux** remonte → c'est le but. (Analogie : 2 jurys aux barèmes différents → on compare les classements,
  pas les notes.)
- 🏗️ Nouveau `HybridSearchController` (`GET /rag/search-hybrid`) : dense top-N + lexical top-N → RRF →
  rerank (bge-reranker-base, réutilisé) → top-K. On **garde** `/search-rerank` (T5) pour comparer.
- 🏗️ Index full-text créé par nous au boot (`@PostConstruct`, `CREATE INDEX IF NOT EXISTS ... USING GIN
  (to_tsvector('french', content))`) — Spring AI ne crée que l'index vectoriel. Idempotent.
- ✋ **J'ai écrit le cœur = la formule RRF** (`rrf.merge(id, 1.0/(RRF_K+rang), Double::sum)`). Le `merge`
  avec `Double::sum` = ce qui fait **cumuler** un article présent dans les 2 listes.
- 💡 **Résultat mesuré (golden set), honnête :**
  - **Q3 « statut d'entreprenant » ✅ GAIN** : en T5, l'`art. 30` (la définition exacte) était **absent**
    du top-3 (on avait 29/31/32). En T6, la recherche « par le mot » le fait remonter → **art. 30 entre
    dans le top-3**. Preuve concrète que l'hybride ajoute du **recall**.
  - **Q2 « obligations comptables » ✅ stable** : top-3 = AUDCG 12/13/16, correct (inchangé, pas cassé).
  - **Q1 « capital minimum SARL » ➖ inchangé** (824/61/66, scores négatifs) — voir ci-dessous.
- 💡 **Découverte Q1 (vérifiée en base) — la FRONTIÈRE du lexical** :
  - `SELECT count(*) ... WHERE content ILIKE '%SARL%'` = **0**. Le sigle « SARL » **n'existe nulle part**
    dans le corpus : la loi écrit « société à responsabilité limitée » en toutes lettres. → le lexical ne
    peut PAS matcher un mot que le texte ne contient jamais.
  - `plainto_tsquery` combine les mots en **ET** : « capital & minimum & sarl ». Comme `sarl` matche 0
    ligne, la requête lexicale renvoie **vide** → l'hybride retombe sur le dense seul → **identique à T5**.
  - Bonus : l'`art. 311` réel parle de **parts sociales**, pas de capital minimum. La révision 2014 a
    supprimé le minimum SARL → **la bonne réponse n'existe pas** → c'est un cas d'**abstention** (T7),
    pas de retrieval.
  - **Leçon** : l'hybride n'est pas magique. Il rattrape les termes exacts **présents** (Q3). Il ne crée
    pas un mot absent (Q1). Leviers pour Q1 : **expansion de requête** (SARL → « société à responsabilité
    limitée »), sémantique **OR** (`websearch_to_tsquery`), et surtout **abstention (T7)**.

### Tranche 7 — guardrail (abstention + citation obligatoire + génération groundée) ✅

**L'idée en une phrase** : `/rag/answer` répond à une question, mais **sous contrôle** — il refuse
plutôt que d'halluciner, et toute réponse est **sourcée**. C'est l'aboutissement de la Phase 1 RAG.

- 💡 **Refactor archi d'abord** : le pipeline hybride (T6) est extrait du controller vers un **service**
  `HybridSearchService` (`@Service`). Pourquoi : le guardrail a besoin du MÊME pipeline (dense+lexical→RRF
  →rerank). Dupliquer = bug garanti le jour où on modifie un côté. `HybridSearchController` (T6) devient
  mince (délègue au service, garde `/search-hybrid` = diagnostic). `@PostConstruct` (index FTS) déménage
  dans le service. Le service renvoie un record typé `ResultatRerank(id, ref, texte, scoreRerank, scoreRrf)`
  **trié par score rerank décroissant** (le [0] = le meilleur).
- 🏗️ `GuardrailController` (`GET /rag/answer?q=`), 2 garde-fous **dans l'ordre** :
  1. **Abstention AVANT tout appel LLM** : `doitSabstenir(chunks)` = `chunks.isEmpty() || chunks.get(0)
     .scoreRerank() < seuil`. Si même le meilleur chunk est sous le seuil → réponse `ABSTENTION`, **zéro
     token Claude**. Double bénéfice : anti-hallucination (pas de source fiable = pas de réponse inventée)
     + économie. Seuil externalisé = `guardrail.rerank-min-score=0.0` (frontière calibrage BGE `>0=pertinent`).
  2. **Grounding + citation** : si on répond, Claude reçoit un **prompt système strict** (« réponds
     UNIQUEMENT à partir des articles fournis, cite `[ref]` chaque affirmation, sinon dis que tu ne sais
     pas »). Le contexte = chaque chunk préfixé de `[ref]`. La réponse porte **toujours** sa liste de
     `sources` (ref + score + extrait) → traçabilité.
- ✋ **J'ai écrit la règle d'abstention** (`doitSabstenir`) après avoir lu/compris toute la structure
  (controller + service). Point clé retenu : le court-circuit `||` protège le `get(0)` (liste vide → membre
  droit jamais évalué → pas d'`IndexOutOfBounds`) ; un seul test sur `get(0)` suffit car la liste est triée.
- 💡 **Choix `<` strict** : au score exactement 0.0 on répond (0 = cas neutre BGE, bénéfice du doute).
  Rendre l'agent plus prudent = remonter le seuil en config, sans toucher au code.
- 💡 **API vérifiée via context7** (règle projet) : `ChatClient.prompt().system(txt).user(txt).call()
  .content()` confirmé sur Spring AI **2.0.0** (`.system(String)` existe sur `ChatClientRequestSpec`).
- 💡 **Cas d'usage type = Q1 « capital minimum SARL »** : la recherche remonte bien 3 chunks mais scores
  rerank **négatifs** (la révision AUSCGIE 2014 a supprimé ce minimum → la réponse n'existe pas) → le gate
  s'abstient. C'est ce cas que le placeholder `isEmpty()` seul RATAIT (la liste n'est pas vide, juste non
  pertinente). ⏳ **À tester côté user** : `curl /rag/answer?q=...` sur les 3 questions golden (Q1 attendu
  = ABSTENTION ; Q2/Q3 = REPONDU avec citations).

**Reste pour finir la Phase 1** (ordre décidé) :
- Plus tard / bonus : **expansion de requête** (sigles → forme longue) pour Q1 ; nettoyage des chunks
  (**boundary bleed** : en-têtes de section collés, ex. art.12/art.29) ; contextual retrieval ; montée
  top-N ; upgrade rerank v2-m3.

> 💡 **Pourquoi 5 et 6 AVANT le guardrail** : ils améliorent le **recall** (faire apparaître le bon
> article). Le rerank et le top-N ne font que *réordonner* l'existant. Détail des leviers :
> `APPRENTISSAGES.md` §8. Chaque levier à mesurer sur un golden set (recall@k avant/après).

---

## Phase 2 — nœud `forme_juridique` (en cours)

Objectif : à partir d'un **profil de porteur**, recommander **une forme juridique** (ENTREPRENANT / EI /
SARL / SARL_U / SA) **justifiée et sourcée** OHADA, avec les formes écartées + leur raison. C'est le
premier vrai nœud « agent » : il porte le contrat de citation et conditionne tout le downstream (spec §5).

### Avant de coder — vérif corpus (💡 déclenchée par une question user)

- ✋ Question : les 5 formes existent-elles vraiment dans le corpus, et peut-on les **citer** ? Faut-il pivoter ?
- 🏗️ Vérif en base (source de vérité, pas les PDF) : les 5 formes sont présentes et citables
  (SA 23, SARL 23, entreprenant 16, commerçant 25, SAS 7). **Pas de pivot** : l'objectif tient.
- 💡 **Mais 2 défauts de corpus confirmés** (texte brut inspecté) → détaillés dans `apprentissage/APPRENTISSAGES.md` §9 :
  1. **désaccentuation + espaces multiples** (extraction PDFBox) — dense robuste, lexical + citations dégradés ;
  2. **GRAVE : collision `311-1` → `311`** — le vrai **art. 311** (capital SARL librement fixé, révision 2014)
     a été **écrasé** par l'art. 311-1. Explique l'échec Q1 « capital minimum SARL » : sigle « SARL » absent
     du texte **+** article-réponse détruit. Défaut déjà prédit en tranche 3, à corriger dans la tranche
     nettoyage (planifiée **avant** le nœud rédacteur). Non bloquant pour T1.

### Tranche 1 — nœud autonome (service + endpoint) ✅ compile

- 💡 **Nœud construit en service Spring autonome d'abord**, embarqué dans le graphe LangGraph4j en Phase 3.
  Construire la brique avant la tuyauterie = testable isolément.
- 🏗️ Contrats (records) : `ProfilPorteur` (entrée : activité, nb associés, capital nullable, résident…) et
  `RecoForme` (sortie : `forme`, `justification`, `formesEcartees[{forme,raison}]`, `capitalMinApplicable`,
  `confiance`, `citations[]`) avec enum `Forme` + records imbriqués.
- 🏗️ `FormeJuridiqueService` (@Service) : profil → `construireRequete` → RAG hybride (réutilise
  `HybridSearchService` de la Phase 1) → **structured output** `ChatClient…call().entity(RecoForme.class)`
  (Claude remplit le record typé, borné aux articles = grounding + citation, comme T7). API `.entity()`
  vérifiée via **context7** (Spring AI 2.0.0). Renvoie `Recommandation(reco, sourcesRag)` pour la traçabilité.
- 🏗️ `FormeJuridiqueController` mince : `POST /forme/recommander` (corps JSON = profil).
- ✋ **J'ai écrit `construireRequete`** (le pont profil→RAG) : vocabulaire juridique **littéral** (pas de
  sigle « SARL », la loi écrit en toutes lettres — leçon §9), piloté par le nombre d'associés, enrichi des
  concepts qui départagent les formes (responsabilité, capital, apports).
- 🏗️ `./mvnw -DskipTests compile` = **exit 0**. ⏳ À tester : `POST /forme/recommander` sur 2-3 profils.

#### Fix runtime structured output (T1.1) ✅ testé 200

Le 1er `POST /forme/recommander` plantait (HTTP 500). Deux bugs empilés, corrigés :

- 💡 **Bug 1 — fences Markdown.** `.entity(RecoForme.class)` attend du JSON pur, mais Claude enrobe sa
  réponse en ` ```json … ``` ` → Jackson bute sur le backtick (`StreamReadException: Unexpected character '`'`).
- 💡 **Fausse piste — structured output natif.** Testé `useProviderStructuredOutput()` (schéma en contrainte
  API, pas en prompt) : Anthropic **refuse** le schéma de `RecoForme` car l'enum `Forme` réutilisée devient
  une référence `$defs/Forme` non résolue (`Invalid schema: Reference to non-existent definition`). Le natif
  d'Anthropic ne gère pas les `$ref/$defs` → inutilisable pour ce type. Leçon : type riche + provider ≠ OpenAI
  ⇒ mode prompt d'abord.
- 🏗️ **Correction retenue :** `BeanOutputConverter<RecoForme>` construit à la main avec un
  `MarkdownCodeBlockCleaner` (retire les fences avant parsing), passé à `.entity(recoConverter)` (le converter
  est un `FormatProvider` → injecte quand même le schéma dans le prompt). API vérifiée via **context7**.
- 🏗️ **Bug 2 — `max-tokens=300`** tronquait le JSON de `RecoForme` (objet riche) → parsing cassé. Monté à **1024**.
- 🏗️ Testé bout-en-bout (app sur 8081, profil « vente vêtements, 1 associé ») : **HTTP 200**, reco `SARL_U`
  justifiée + citations (AUSCGIE art. 5/6) + `sourcesRag`. ⚠️ Les articles remontés confirment le bug connu de
  **désaccentuation** de l'extraction PDF (corpus) — non bloquant, déjà backloggé.

---

## Concepts clés & apprentissages

➡️ Déplacés dans **`apprentissage/APPRENTISSAGES.md`** (savoir transverse durable, relisable à froid).
Ce journal reste chronologique ; les leçons de fond (embedding, chunking, idempotence, metadata,
archi corpus) vivent là-bas.
