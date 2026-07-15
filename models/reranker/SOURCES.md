# Modèle de reranking multilingue (cross-encoder ONNX)

Modèle : **BAAI/bge-reranker-base** (export ONNX par Xenova).
Rôle : re-noter la pertinence d'une paire `(question, chunk)` après le retrieval vectoriel.
Remplace `cross-encoder/ms-marco-MiniLM-L-6-v2` (anglophone) par du **multilingue** (Tranche 5).
Non versionné dans git (fichiers lourds) — reproductible via les commandes ci-dessous.

Deux fichiers attendus par `OnnxScoringModel` (cf `application.properties`) :
- `models/reranker/model.onnx`
- `models/reranker/tokenizer.json`

## Téléchargement

Depuis la racine du projet (PowerShell) :

```powershell
New-Item -ItemType Directory -Force models/reranker | Out-Null
curl.exe -L -o models/reranker/model.onnx      https://huggingface.co/Xenova/bge-reranker-base/resolve/main/onnx/model.onnx
curl.exe -L -o models/reranker/tokenizer.json  https://huggingface.co/Xenova/bge-reranker-base/resolve/main/tokenizer.json
```

Si une URL renvoie 404 → me le signaler (on corrige le manifest).

## Notes

- Cross-encoder ≠ bi-encoder : il lit la **paire** (question, passage) ensemble et sort **1 score**
  de pertinence. Plus lent que l'embedding (pas de vecteur pré-calculé), d'où le schéma
  *retrieve large (top-N) → rerank → garder top-K* plutôt que reranker toute la base.
- **Pourquoi `bge-reranker-base` et pas `bge-reranker-v2-m3`** : le v2-m3 (meilleur) n'a **pas d'export
  ONNX single-file** disponible (BAAI = safetensors only, pas d'ONNX Xenova accessible). L'exporter
  soi-même = étape Python/optimum lourde, hors scope de ce module Java. `base` est multilingue,
  single-file (pas de poids externes), et déjà un net progrès vs l'anglophone ms-marco.
  → passer à v2-m3 plus tard **seulement si** l'éval (recall/précision sur golden set) le justifie.
- Config `application.properties` **inchangée** : mêmes chemins `model.onnx` / `tokenizer.json`,
  donc `RerankConfig` et l'endpoint `/rag/search-rerank` n'ont rien à modifier — juste re-télécharger
  le fichier à la place de l'ancien.

## Ancien modèle (Tranche 4, remplacé)

Avant la Tranche 5 : `Xenova/ms-marco-MiniLM-L-6-v2` (entraîné MS MARCO, anglophone).
Conservé ici pour mémoire — pour revenir en arrière, re-télécharger depuis :
`https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/onnx/model.onnx` (+ `tokenizer.json`).
