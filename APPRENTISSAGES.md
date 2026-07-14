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

## Mémo « comment marche un agent » (rappels de base)

- **Embedding** : texte → vecteur ; sens proche → vecteurs proches → recherche « par le sens ».
- **Vector store** : base qui stocke ces vecteurs et retrouve les plus proches d'une question.
- **RAG** (Retrieval-Augmented Generation) : récupérer d'abord les bons passages (retrieval), puis le
  LLM répond **en s'appuyant dessus** → réponses sourcées, moins d'hallucinations.
- **Citation / `ref`** : chaque passage porte sa source ; l'agent doit citer → traçabilité juridique.
