# Article LinkedIn n°1 — « Mon RAG me renvoyait le mauvais article 2 fois sur 3 »

Statut : **brouillon prêt à relire**. Tiré du projet agent de formalisation d'entreprise OHADA
(Java / Spring AI / pgvector). Matière : `APPRENTISSAGES.md` §8, `DEROULEMENT.md` tranche 4.

Objectif : montrer une démarche d'ingénieur qui **mesure** (baseline → échec → fix → preuve), et
vulgariser une notion mal comprise (retrieval vs reranking). Ton : direct, honnête, pédagogique.

---

## Version à poster

**Mon RAG me renvoyait le mauvais article 2 fois sur 3. Voici ce que j'ai appris.**

Je construis un agent qui aide à créer une entreprise en zone OHADA (17 pays d'Afrique, droit des
affaires commun). Le cœur : un RAG qui doit retrouver le **bon article de loi** pour sourcer chaque
réponse. En droit, une réponse sans la bonne référence ne vaut rien.

J'avais fait « comme dans les tutos » : je découpe les textes, je transforme chaque article en vecteur,
je stocke, et je récupère les 3 plus proches d'une question. Ça *marchait*… en apparence.

Puis j'ai vraiment testé. Question : **« Quel est le capital minimum d'une SARL ? »**
Réponse top-1 de mon RAG : l'article sur les **SA**. Confiant. Faux.

Sur 3 vraies questions, le **meilleur résultat était faux 2 fois sur 3.** 😅

**Pourquoi ?** Parce que la recherche vectorielle classique compare des *proximités de sens de surface*.
Chaque texte a été résumé à l'avance en un seul vecteur. « SARL » et « SA » se ressemblent beaucoup pour
la machine — même univers, mots voisins. Le vecteur seul ne capte pas assez l'**intention** de la question.

**Ce qui a tout changé : le reranking.**

Au lieu d'un seul étage, j'en ai mis deux :
1. **Rappel large** — je récupère 20 candidats vite (l'ancienne méthode).
2. **Reclassement fin** — un second modèle (un *cross-encoder*) relit chaque paire
   **(question + article) ensemble**, et note leur vraie pertinence. Je ne garde que le top 3.

La différence est là : le premier étage regarde question et texte **séparément** ; le second les lit
**ensemble**. C'est plus lent — donc on ne l'applique qu'à une petite sélection. D'où le schéma :
**récupérer large → reclasser → garder le meilleur.**

Résultat : le bon article remonte en tête. Et le plus beau —

**tout tourne en local, gratuitement.** Le reranker fait ~80 Mo, s'exécute sur mon CPU, zéro appel API,
zéro donnée qui sort de la machine. Pas besoin d'un gros LLM payant pour ça.

**La leçon que je retiens :** un RAG de qualité, ce n'est pas « brancher un LLM sur des documents ».
C'est une chaîne d'étages, où **le retrieval brut n'est que le premier**. Et surtout : tant qu'on n'a pas
**mesuré**, on ne sait pas si ça marche — on *croit* que ça marche.

Le POC qui répond joliment sur 3 questions choisies ≠ le système qui tient sur les vraies.

Et vous, votre RAG, vous l'avez déjà pris en défaut sur une question simple ? 👇

---

#IA #RAG #IngénierieIA #LLM #Java #OHADA #MachineLearning #AIagents

---

## Notes de rédaction (ne pas poster)

- **Longueur** : ~2800 caractères, dans la cible LinkedIn (post long lisible, pas article-blog).
- **Vulgarisation assumée** : « bi-encoder » n'apparaît pas (jargon) → « regarde séparément » ;
  « cross-encoder » gardé une fois car explicable en une phrase. Logits/ONNX volontairement omis.
- **Chiffres réels** à garder pour la crédibilité : 17 pays, 2/3 faux, top-N=20, top-K=3, ~80 Mo.
- **Visuel suggéré** : un schéma simple `question → [récupérer 20] → [reranker] → [3 meilleurs]`,
  avant/après (top-1 faux → top-1 juste). Un carrousel 3 slides marcherait aussi.
- **Variantes d'accroche à tester** : garder l'échec en 1re ligne (fort) ; éviter d'ouvrir sur la
  définition du RAG (ennuyeux).
- **Prochaine itération possible** : ajouter le *chiffre d'après-mesure* (recall@k avant/après) une fois
  le golden set en place — passerait de « ça remonte en tête » à une preuve chiffrée. Lien avec le sujet n°3.
