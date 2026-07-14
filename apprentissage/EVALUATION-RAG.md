# Évaluation du RAG — comment mesurer sans dépendre d'un LLM à chaque fois

But de ce document : la **méthode d'évaluation** de notre RAG. Réponse à la question
« comment je sais que mon RAG fournit les bonnes réponses, sans confier ça à Claude à chaque fois ? ».

Rattaché à `APPRENTISSAGES.md` (savoir transverse). Voir aussi l'exigence « Évaluation » du CLAUDE.md
(§8 spec : linters déterministes bloquants + LLM-as-judge).

---

## Le problème avec l'éval manuelle

Lancer des requêtes, regarder les résultats, demander à un LLM s'ils sont bons = **éval ad hoc**.
Défauts : non reproductible, ne scale pas, coûte des tokens à chaque fois, subjectif.

La pratique pro : **un jeu de référence construit UNE fois**, puis rejoué automatiquement à l'infini.

---

## Le concept clé : le « golden set » (jeu de référence)

Écrit **une seule fois, à la main**, une liste de paires :

```
question → réponse attendue (+ SURTOUT : article source attendu)
```

Exemple pour notre corpus :

| Question | `ref` attendue |
|---|---|
| « Capital minimum d'une SARL ? » | `AUSCGIE art. 311` |
| « Qu'est-ce que le statut d'entreprenant ? » | `AUDCG art. 30` |

Une fois ce fichier écrit (~20-50 questions), **plus jamais besoin d'un LLM pour évaluer le retrieval**.
Le test devient **déterministe**.

---

## Les 3 idées qui structurent tout

1. **Séparer DEUX évaluations distinctes** (le point que la plupart ratent) :
   - **Retrieval** (a-t-il ramené le bon chunk ?) → **100 % déterministe, zéro LLM**.
   - **Génération** (la réponse finale est-elle bonne ?) → besoin de jugement (humain ou LLM-judge).
   La 1re est la plus importante pour nous (citation) ET la plus facile à automatiser.

2. **Notre `ref` rend l'éval retrieval triviale.** Chaque chunk porte déjà `AUSCGIE art. 311`.
   Évaluer = « l'article attendu est-il dans le top-k renvoyé par `/rag/search` ? » = **comparaison de
   chaînes**. Pas de LLM, pas de subjectivité. L'architecture metadata paie ici.

3. **Qui fabrique la vérité terrain ? Un humain, une fois.** C'est la vraie réponse à « sans LLM ».
   Un LLM peut aider à amorcer, mais le ground truth doit être **validé par un humain** — sinon on
   évalue le RAG avec les hallucinations d'un LLM. Le golden set humain est l'ancre.

---

## Métriques retrieval (déterministes, sans LLM)

Pour chaque question, `/rag/search` renvoie une liste ordonnée de `ref` + score. On compare à la
`ref` attendue :

| Métrique | Question qu'elle répond | Formule simple |
|---|---|---|
| **Recall@k** | Le bon article est-il dans le top-k ? | `bon_ref ∈ top_k ? 1 : 0`, moyenné |
| **MRR** (Mean Reciprocal Rank) | À quelle position ? (haut = mieux) | `moyenne(1 / rang_du_bon_ref)` |
| **Hit rate** | Au moins un bon résultat ? | = recall@1 |

**Recall@k = la métrique reine** : pour de la citation juridique, ce qui compte c'est « le bon
article est-il récupéré ? ». On tourne ça sur les ~30 questions → **un chiffre unique** (« recall@3 =
0.87 »). On change le chunking ou le modèle d'embedding → on relance → on compare les chiffres.
**Décision par la mesure, plus à l'œil.**

---

## Évaluer la génération (la réponse en prose)

Deux couches, la 1re sans LLM :

1. **Linters déterministes** (règles, zéro LLM) — exactement le §8 spec / les guardrails :
   - Chaque affirmation cite-t-elle une `ref` présente dans les chunks récupérés ? (regex/parsing)
   - Un coût/délai est-il inventé (absent des sources) ? → bloquant anti-hallucination.
   - Activité réglementée détectée → réponse bloquée ?
   Ce sont des **assertions de code**, pas du jugement. Rapides, gratuites, bloquantes en CI.

2. **LLM-as-judge** (optionnel, 2e couche) — un LLM note la fluidité/exactitude de la prose contre la
   réponse attendue. Utile pour ce que les règles ne capturent pas, mais : coûte des tokens, non
   déterministe, à calibrer. **Le complément, pas le fondement.**

---

## Le pipeline concret

```
golden_set.json (humain, une fois)
      │
      ▼
pour chaque question :
   /rag/search?q=…  →  liste de ref
   ├─ recall@k, MRR         ← déterministe, sans LLM   ← COMMENCER ICI
   └─ (option) réponse LLM  →  linters citation        ← déterministe
                            →  LLM-judge               ← option, coûteux
      │
      ▼
rapport : recall@3 = 0.87, MRR = 0.71, 2/30 échecs → quels articles ratés
```

---

## Ce que ça apporte au portfolio

- **Chiffre reproductible** au lieu de « ça a l'air de marcher » → argument d'entretien fort.
- **Non-régression** : en ajoutant le reranking (en cours), on prouve *avec un chiffre* qu'il améliore
  le recall.
- Coche l'exigence « **Évaluation** » du CLAUDE.md (linters bloquants + LLM-judge).

---

## Premier pas le plus rentable

Un **`golden_set.json`** (~20 questions, `ref` attendue) + un endpoint/test qui calcule **recall@k**.
Purement déterministe, aucun coût LLM récurrent. Le cœur métier (écrire les bonnes questions de
référence) est écrit par l'humain — c'est là qu'on apprend ce qu'un bon jeu de test contient.
