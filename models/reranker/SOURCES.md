# Modèle de reranking (cross-encoder ONNX)

Modèle : **cross-encoder/ms-marco-MiniLM-L-6-v2** (export ONNX par Xenova).
Rôle : re-noter la pertinence d'une paire `(question, chunk)` après le retrieval vectoriel.
Non versionné dans git (fichiers lourds) — reproductible via les commandes ci-dessous.

Deux fichiers attendus par `OnnxScoringModel` (cf `application.properties`) :
- `models/reranker/model.onnx`
- `models/reranker/tokenizer.json`

## Téléchargement

Depuis la racine du projet (PowerShell) :

```powershell
New-Item -ItemType Directory -Force models/reranker | Out-Null
curl.exe -L -o models/reranker/model.onnx      https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/onnx/model.onnx
curl.exe -L -o models/reranker/tokenizer.json  https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/tokenizer.json
```

## Notes

- Cross-encoder ≠ bi-encoder : il lit la **paire** (question, passage) ensemble et sort **1 score**
  de pertinence. C'est plus lent que l'embedding (pas de vecteur pré-calculé), d'où le schéma
  *retrieve large (top-N) → rerank → garder top-K* plutôt que reranker toute la base.
- Modèle entraîné sur MS MARCO (recherche de passages). Multilingue « correct » ; si la pertinence
  FR déçoit, alternative : `BAAI/bge-reranker-base` (ONNX) — plus lourd, meilleur multilingue.
