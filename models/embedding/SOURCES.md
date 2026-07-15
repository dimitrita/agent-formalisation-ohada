# Modèle d'embedding multilingue (bge-m3, ONNX)

Modèle : **BAAI/bge-m3** (export ONNX par Xenova).
Rôle : transformer chaque texte (article de loi OU question) en **vecteur dense de 1024 nombres**.
Remplace `all-MiniLM-L6-v2` (384 dims, anglophone) par du **multilingue orienté FR** (Tranche 5).
Non versionné dans git (fichiers lourds) — reproductible via les commandes ci-dessous.

Deux fichiers attendus dans `models/embedding/` :
- `model.onnx`         → le modèle **quantifié int8, auto-suffisant** (~570 Mo, poids inclus)
- `tokenizer.json`     → le découpeur de texte en tokens

⚠️ **NE PAS prendre le `onnx/model.onnx` full-precision** : il fait >2 Go → dépasse la limite protobuf
d'ONNX → ses poids sortent dans un fichier séparé `model.onnx_data`. Or ONNX Runtime cherche ce
`.onnx_data` par rapport au **dossier de travail** (racine projet, là où tourne `mvnw`), PAS à côté du
`.onnx` → **crash `file introuvable: model.onnx_data`**. On évite tout le piège en prenant une variante
single-file (< 2 Go). Ici : `model_quantized.onnx` (int8, CPU-friendly, sortie 1024-dim).

## Téléchargement

Depuis la racine du projet (PowerShell) :

```powershell
New-Item -ItemType Directory -Force models/embedding | Out-Null
curl.exe -L -o models/embedding/model.onnx     https://huggingface.co/Xenova/bge-m3/resolve/main/onnx/model_quantized.onnx
curl.exe -L -o models/embedding/tokenizer.json https://huggingface.co/Xenova/bge-m3/resolve/main/tokenizer.json
```

Si une URL renvoie 404 → me le signaler (le repo a peut-être bougé, on corrige le manifest).

## Alternatives single-file (si la qualité int8 déçoit à l'éval)

- `onnx/model_fp16.onnx` (~1,1 Go) : meilleure qualité que int8, mais **plus lent sur CPU** (ORT insère
  des casts fp16↔fp32). Même sortie 1024-dim.
- `onnx/model_uint8.onnx` : autre quantification, à tester si besoin.
- Éviter `onnx/model.onnx` et `onnx/sentence_transformers.onnx` : ce sont les seuls avec poids externes
  (`*.onnx_data`) → le piège ci-dessus.

## Câblage Spring AI (application.properties)

```properties
spring.ai.embedding.transformer.onnx.model-uri=file:models/embedding/model.onnx
spring.ai.embedding.transformer.tokenizer.uri=file:models/embedding/tokenizer.json
# nœud de sortie ONNX — à confirmer au 1er démarrage (voir Notes)
spring.ai.embedding.transformer.onnx.model-output-name=last_hidden_state
```

Et : `spring.ai.vectorstore.pgvector.dimensions=1024` (au lieu de 384).

## Notes (les 2 pièges — cf apprentissage §1 « pooling CLS vs mean »)

- **Poids externes** : ONNX Runtime charge `model.onnx_data` automatiquement s'il est **à côté** de
  `model.onnx`. C'est pour ça qu'on garde les deux dans `models/embedding/`.
- **Pooling** : bge-m3 (famille BGE) est entraîné en **CLS pooling** (token spécial en tête), pas en
  mean. Deux stratégies possibles selon ce que Spring AI accepte :
  1. brancher `model.onnx` (sort `last_hidden_state`) et laisser Spring AI pooler — **vérifier** que le
     pooling appliqué est cohérent (sinon dégradation muette) ;
  2. ou brancher la variante `onnx/sentence_transformers.onnx` (pooling + normalisation **déjà intégrés**
     dans le graphe → Spring AI lit le vecteur final, zéro risque de mismatch).
  → tranché au 1er démarrage, mesuré sur le golden set.
- **1024 ≠ 384** : changer l'embedding oblige à **ré-embedder tout le corpus** (les anciens vecteurs
  384-dim sont illisibles par bge-m3) → `DROP TABLE ohada_core` puis ré-ingestion (voir DEROULEMENT.md).
- **Variante quantifiée** (optionnel, si CPU/RAM serrés) : `onnx/model_quantized.onnx` (int8, plus léger,
  qualité légèrement en dessous). Défaut = full precision `model.onnx`.
