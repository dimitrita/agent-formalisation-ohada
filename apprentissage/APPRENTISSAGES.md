# Apprentissages — ce que j'ai compris (mémo durable)

But de ce document : un **savoir transverse relisable à froid** (avant un entretien, ou pour
réexpliquer le projet). Différent de `DEROULEMENT.md` qui est le journal chronologique
(« qui a fait quoi, dans quel ordre »). Ici : « **qu'est-ce que j'ai compris, et pourquoi ça compte** ».

---

## 1. Embedding — le cœur mal compris du RAG

### Embedding ≠ LLM (la confusion la plus fréquente)

| | Modèle d'**embedding** | **LLM** (génératif) |
|---|---|---|
| Fait quoi | texte → **vecteur de nombres** | texte → texte |
| Exemple | all-MiniLM (notre cas), OpenAI embeddings | Claude, GPT |
| Nature | encodeur (type BERT), **spécialisé** | gros modèle génératif |
| Taille | petit (~80 Mo pour MiniLM) | énorme (des Go) |
| Où | peut tourner **local, CPU** | souvent API distante |
| Coût | souvent gratuit/local | payant au token |

Un modèle d'embedding **ne "parle" pas** : il projette le sens d'un texte dans un vecteur.
Deux textes de sens proche → vecteurs proches → c'est ce qui permet la recherche « par le sens ».

**Non, embedder ne nécessite PAS un LLM.** Options, du moins cher au plus cher :
1. **Local** (notre choix) — MiniLM ONNX : gratuit, privé, correct pour un prototype.
2. **API d'embedding dédiée** — OpenAI/Cohere : meilleure qualité, payant au token
   (~100× moins cher qu'un appel LLM génératif), mais envoie le texte au fournisseur.
3. Certains fournisseurs LLM exposent un endpoint embeddings — mais c'est un **modèle
   d'embedding séparé** derrière, pas le LLM de chat.

### Dans NOTRE projet : quand et comment

- **Config** (`application.properties`) : `spring.ai.model.embedding=transformers`
  → modèle **all-MiniLM-L6-v2** embarqué (ONNX), **384 dimensions**, **zéro clé API, zéro appel réseau**.
  Le corpus juridique ne quitte jamais la machine à l'indexation.
- **Deux modèles distincts, à ne pas confondre :**
  - **Chat** = Claude Haiku (API Anthropic, payant) → génère les réponses. C'est un LLM.
  - **Embedding** = all-MiniLM (local, gratuit) → vectorise. **Pas un LLM.**
- **Le moment exact texte → vecteur : à l'appel `vectorStore.add(articles)`** (magie implicite Spring AI).
  Notre code ne voit jamais le vecteur ; `PgVectorStore.add()` fait, pour chaque `Document` :
  1. appelle l'`EmbeddingModel` → `float[384]`,
  2. `INSERT` en base (texte + embedding + metadata).
- **Côté requête** : la question utilisateur passe par le **même** MiniLM au moment du `search`
  (sinon les vecteurs ne seraient pas comparables). Embedding à l'indexation ET à la recherche = même modèle.

### ⚠️ Le `384` n'est pas arbitraire

`dimensions=384` DOIT matcher la taille de sortie d'all-MiniLM. Changer de modèle d'embedding
(ex. OpenAI `text-embedding-3-small` = 1536 dims) impose de **changer ce nombre ET de ré-embedder
tout le corpus**. Un mismatch = INSERT rejeté par pgvector.

### Point d'attention qualité (à trancher avec l'éval, pas à l'aveugle)

all-MiniLM est **multilingue moyen**, entraîné surtout sur de l'anglais généraliste. Sur du
**juridique français OHADA**, retrieval correct mais pas optimal. Si le `recall@k` déçoit → tester
un modèle orienté français/multilingue (`bge-m3`, ou embeddings OpenAI). **Décision guidée par les
chiffres d'éval.**

### Pooling `CLS` vs `mean` — comment un modèle résume une phrase en 1 vecteur

Un modèle d'embedding ne traite pas la phrase d'un bloc : il la découpe en **tokens** (bouts de mots)
et calcule **un vecteur par token**. Mais on veut **un seul** vecteur pour toute la phrase. Il faut
donc **agréger** (« pooling ») ces vecteurs-tokens en un. Deux méthodes dominantes :

- **mean pooling** : moyenne de tous les vecteurs-tokens. *(La moyenne des notes de la classe.)*
  Utilisé par **all-MiniLM** et la famille **e5**.
- **CLS pooling** : le modèle ajoute au début un token spécial `[CLS]` entraîné pour **absorber le sens
  global** ; on ne lit que son vecteur. *(Le délégué qui résume la classe.)*
  Utilisé par la famille **BERT/BGE** (donc **bge-m3**).

**⚠️ Le pooling doit matcher l'entraînement du modèle.** Un modèle entraîné en CLS agrégé en mean (ou
l'inverse) produit un vecteur **dégradé mais silencieux** : aucune erreur, juste un retrieval moins
précis — le pire type de bug (invisible sans éval).

**Dans Spring AI** : `TransformersEmbeddingModel` lit un **nœud de sortie** ONNX nommé
`model-output-name` (défaut `last_hidden_state` = les vecteurs-tokens bruts) puis applique un pooling.
Deux conséquences pratiques quand on change de modèle :
1. **Nom de sortie** : si le modèle n'expose pas ce nœud sous ce nom → **crash au démarrage** (à régler
   via `spring.ai.embedding.transformer.onnx.model-output-name`).
2. **Type de pooling** : vérifier qu'il correspond au modèle (CLS pour BGE). Sinon dégradation muette.

**À retenir** : changer de modèle d'embedding, ce n'est pas juste changer une URL — c'est aussi
**réaccorder le pooling et le nœud de sortie** au nouveau modèle. Décidé/vérifié au 1er démarrage,
mesuré sur le golden set (§7).

---

## 2. Chunking — la frontière compte plus que la taille

- **Deux leviers séparés** : la **taille** (combien de tokens) et la **frontière** (où on coupe).
  La frontière prime.
- **Un embedding compresse le chunk en 1 seul vecteur.** Trop de sujets dans un chunk → vecteur
  « moyen » flou → ne matche rien précisément. Chunk trop petit → manque de contexte au LLM.
- **Découper par STRUCTURE, pas par taille fixe aveugle.** Notre corpus est un cas idéal :
  AUSCGIE/AUDCG sont en **articles numérotés** → **1 article = 1 chunk = 1 référence citable**.
  Couper au milieu d'un article casse le sens juridique ET la citation.
- Recette OHADA : split par `Article \d+` ; article trop long → sous-découpe par alinéa (garder la
  réf) ; article court → garder tel quel (ne jamais fusionner 2 articles = citation ambiguë) ;
  **pas d'overlap entre articles** (unités indépendantes).
- **Comment décider une taille en général** : mesurer, pas deviner. ~20 questions test + bonne réponse
  attendue → comparer stratégies (article entier / 256 / 512) sur **recall@k** + LLM-judge.
  Défaut hors-légal : ~512 tokens, overlap 50–100, puis ajuster.

---

## 3. Metadata homogènes = fiabilité du RAG

- **Mêmes clés sur TOUS les chunks** (`source, ref, pays, date_maj, url`). Clés incohérentes = **1re
  cause de RAG cassé en prod** (filtrage et citation deviennent imprévisibles).
- La clé `ref` (ex. `AUSCGIE art. 311`) = **l'unité de citation**. C'est elle qui rend la traçabilité
  juridique possible (guardrail « citation obligatoire »).
- Ces metadata servent **3 fois** : filtrage au retrieval, signal au reranking, et citation finale.

---

## 4. Idempotence de l'ingestion (leçon RAG importante)

- `vectorStore.add()` = **INSERT** (UUID aléatoire par chunk). Ré-ingérer le **même PDF** ⇒ **doublons**
  (2 runs = 1812 lignes au lieu de 906).
- Un RAG **a besoin d'une stratégie anti-doublon** dès qu'un document peut être ré-ingéré
  (redéploiement, correction, mise à jour de source).
- **Choix retenu** : ingest **idempotent** = `DELETE WHERE source=?` **avant** le `add`
  → « ré-ingérer = **remplacer**, pas empiler ». Bonus : purge les articles disparus si le PDF change.
- Alternative : id de `Document` **déterministe** = `hash(source+numéro)` → upsert au lieu de delete+insert.

---

## 5. Déduplication par numéro d'article (bug réel rencontré)

- Le regex `^(article|art.) \d+` trouvait **1112 débuts** alors que l'AUSCGIE s'arrête à l'**art. 920**
  (le PDF **répète des blocs** → 83 numéros en double).
- **Fix** : dédup par numéro, on garde le **chunk le plus long** (le vrai texte de loi bat un bloc
  répété plus court). Résultat : **906 articles**.
- Pourquoi ça compte : 1 numéro = 1 `ref` unique = citation fiable. Doublons = RAG cassé.

---

## 6. Architecture corpus — transverse vs plugin pays

- `ohada_core` = **droit OHADA commun** aux 17 pays (transverse).
- Les **démarches** (guichet, coûts, délais) sont **spécifiques pays** → collection séparée
  (ex. `cm_procedures`, metadata `pays=CM`), **pas** dans `ohada_core`.
- Ajouter un pays = ajouter une collection procédures **sans toucher au reste**. pgvector = simple
  **extension** de Postgres → scalable proprement.

---

## 7. Évaluer le RAG (mesurer, pas deviner)

Comment savoir que le RAG répond juste **sans confier ça à un LLM à chaque fois** : un **golden set**
(jeu question → `ref` attendue) écrit **une fois par un humain**, puis rejoué en **déterministe**.
Séparer **retrieval** (recall@k, MRR — zéro LLM) et **génération** (linters déterministes + LLM-judge
optionnel).

➡️ Détail complet dans **`EVALUATION-RAG.md`**.

---

## 8. Reranking, et quoi faire quand il ne suffit pas

### Recherche vs reranking — l'intuition (le « PLUS » qu'apporte le rerank)

La recherche **trouve déjà** un lien question ↔ réponse. Alors que fait le rerank de plus ? La clé,
c'est **QUAND** et **COMMENT** le lien est jugé :

- **Recherche = jugée À L'AVANCE et SÉPARÉMENT.** Chaque chunk a été résumé en 1 vecteur **une fois,
  sans connaître la question**. À la requête, on compare deux résumés faits chacun de leur côté.
  Rapide, mais l'étiquette est **générale** → confond les cas voisins (ex. « SARL » vs « SA », tous deux
  « société + capital »). Son job : **ne rien rater** (grand filet de ~20) = *recall*.
- **Reranking = jugé À LA DEMANDE et ENSEMBLE.** Le cross-encoder relit chaque candidat **avec la
  question sous les yeux, les deux en même temps**. Il voit la nuance que l'étiquette générale ratait.
  Son job : **mettre LE bon en premier** (garder top-3) = *précision*.
- **Pourquoi deux étages et pas que le minutieux ?** Le rerank est **lent** (il lit vraiment chaque
  paire) → impossible sur les 1213 articles à chaque question. Donc : recherche dégrossit (20) →
  rerank affine (3). Deux **métiers différents**, pas deux fois le même.

Image d'enfant : la recherche te tend **20 bonnes pioches en vrac** (étiquettes collées à l'avance) ;
le rerank les **relit avec ta question en tête** et dit lequel est vraiment le meilleur.

### Ce qu'est (et n'est pas) le reranking

- **Retrieval vectoriel** = rappel large mais grossier : compare des **proximités de sens de surface**
  (un bi-encoder a pré-calculé 1 vecteur par chunk, on cherche les plus proches). Rapide, mais rate
  l'**intention** et les **termes exacts**.
- **Reranking** = un **cross-encoder** relit chaque paire **(question, chunk) ensemble** et sort **1 score
  de pertinence**. Bien plus fin, mais lent (pas de vecteur pré-calculé) → on ne l'applique qu'à un
  petit lot. D'où le schéma **retrieve top-N (large) → rerank → garder top-K**.
- Scores du cross-encoder = **logits** (échelle libre, souvent négatifs) : servent à **ordonner**, pas
  une probabilité.

### ⚠️ La règle qui explique 90 % des échecs

**Le rerank ne peut réordonner que ce que le retrieval a déjà remonté.** Si le bon article n'est pas
dans le top-N, le rerank ne peut pas l'inventer. Donc un mauvais résultat est presque toujours un
problème de **recall** (retrieval) ou de **qualité de chunk**, **pas** de tri.
→ Corollaire : **augmenter N est le levier le plus grossier** (plus de N = plus de bruit à trier, coût
rerank ↑). Rarement la meilleure réponse.

### Cas réel rencontré

"obligations comptables du commerçant" : la baseline mettait **AUSCGIE 697 (*commissaire aux comptes*)**
en #1 — piège lexical *comptes ≈ comptables*. Le cross-encoder l'a rétrogradé **#1 → #3** et remonté un
article **AUDCG commerçant**. ✅ Gain de **précision** clair. Mais le cœur idéal (AUDCG art. 13, tenue de
comptabilité) restait absent → **plafond de recall** : il n'était pas dans les 20 candidats.

### Leviers fiables quand le rerank ne suffit pas (par ROI décroissant)

1. **Modèles multilingues** (souvent le plus gros gain). **✅ FAIT (Tranche 5)** : embeddings
   `all-MiniLM` (384d) → **bge-m3** (1024d) ; rerank `ms-marco-MiniLM` → **bge-reranker-base**.
   **Gain mesuré** : Q2 « obligations comptables » passait par une fuite cross-doc (`AUSCGIE 697`
   commissaire aux comptes) en #1 → désormais top-3 = 100% AUDCG commerçant, et `art. 13` (idéal,
   **absent** du top-N avant) est **récupéré** → gain de **recall réel**, pas juste du tri.
   ⚠️ Pièges rencontrés : (a) ONNX à **poids externes** (`model.onnx_data`) introuvable car résolu par
   rapport au CWD → prendre une variante **single-file < 2 Go** (`model_quantized.onnx` int8) ;
   (b) **pooling** BGE = CLS mais défaut Spring AI = mean (fonctionnel, à raffiner si besoin, cf §1).
2. **Recherche hybride dense + lexical, fusionnée par RRF. ✅ FAIT (Tranche 6).** Le vectoriel rate les
   **termes exacts** ; le **lexical** (full-text Postgres `french`, "BM25-like") les attrape. On lance
   les deux et on fusionne les **rangs** par **RRF** (`score(id) = Σ 1/(60+rang)`, on ignore les scores
   car cosinus et ts_rank sont incomparables). Endpoint `/rag/search-hybrid`.
   **Gain mesuré** : Q3 « entreprenant » → l'`art. 30` (définition exacte, **absent** du top-3 en dense
   seul) est **récupéré** grâce au lexical.
   ⚠️ **La FRONTIÈRE du lexical (leçon Q1)** : le lexical ne matche que le **mot littéral présent** dans
   le texte. « SARL » apparaît **0 fois** dans le corpus (la loi écrit « société à responsabilité
   limitée ») → le lexical ne peut pas le rattraper, et comme `plainto_tsquery` combine en **ET**, un
   seul mot absent (`sarl`) vide la requête → l'hybride retombe sur le dense. **L'hybride rattrape les
   termes exacts _présents_, il n'invente pas un mot absent.** Leviers alors : **expansion de requête**
   (sigle → forme longue), sémantique **OR** (`websearch_to_tsquery`), ou **abstention** (§ guardrail).
3. **Nettoyage + enrichissement des chunks.** Résidus PDF (en-têtes, `page 7/67`, OCR) **polluent
   l'embedding**. Bonus : **contextual retrieval** = préfixer chaque chunk de son titre/section.
4. **Pré-filtrage par metadata.** Question « commerçant » → filtrer `source=AUDCG` avant de chercher.
   Coupe les **fuites cross-doc**. Fiable dès qu'on sait router la question.
5. **Réécriture / expansion de requête (LLM).** *Multi-query* (N reformulations fusionnées) ou **HyDE**
   (embed une réponse hypothétique générée, plus proche d'un article de loi qu'une question courte).
   Puissant mais ajoute latence + coût LLM.

**Ordre d'attaque rationnel** : (1) multilingue → (2) hybride RRF → (3) chunks. Ce sont des gains de
**recall/qualité** (ils font *apparaître* le bon doc). Rerank et top-N ne font que *réordonner* l'existant.

### Bonus : le score rerank BGE donne un seuil d'abstention gratuit

Les rerankers **BGE** sont calibrés pour que **`score > 0 ≈ pertinent`, `score < 0 ≈ non pertinent`**
(≠ ms-marco dont les logits sont sur une échelle libre, bons pour ordonner mais pas pour décider).
Cas réel (Tranche 5) : « capital minimum SARL » → les 3 résultats **tous négatifs** (-0.39, -1.73, -1.96),
parce que l'AUSCGIE révisé 2014 **ne fixe plus** de minimum SARL → la bonne réponse **n'existe pas**.
→ Un **seuil à ~0 sur le score rerank** = mécanisme d'**abstention** (« aucun article pertinent »).
C'est la brique anti-hallucination du **guardrail** (Tranche 7) : mieux vaut *ne pas répondre* que citer
un article hors-sujet. Le RAG doit savoir dire « je ne sais pas ».

### Le fil rouge : sans éval, on optimise à l'aveugle

Chaque levier se juge sur un **golden set** (question → `ref` attendue), en mesurant `recall@k` **avant/
après**. Sinon on « améliore » au feeling. Cf section 7 + `EVALUATION-RAG.md`.

---

## Mémo « comment marche un agent » (rappels de base)

- **Embedding** : texte → vecteur ; sens proche → vecteurs proches → recherche « par le sens ».
- **Vector store** : base qui stocke ces vecteurs et retrouve les plus proches d'une question.
- **RAG** (Retrieval-Augmented Generation) : récupérer d'abord les bons passages (retrieval), puis le
  LLM répond **en s'appuyant dessus** → réponses sourcées, moins d'hallucinations.
- **Citation / `ref`** : chaque passage porte sa source ; l'agent doit citer → traçabilité juridique.
