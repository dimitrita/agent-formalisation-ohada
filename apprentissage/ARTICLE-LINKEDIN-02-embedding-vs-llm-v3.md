# Article LinkedIn n°2 — VERSION 3 (esprit v1, ton humain, cible décideurs)

Statut : **brouillon prêt à relire**. 3e écriture de l'article n°2 (embedding ≠ LLM).
Direction retenue : l'**esprit de la v1** (pédagogique, engageant, mots-clés *embedding*/*LLM* assumés)
mais **sans gabarit répété** ni ton robotique. Cible : décideurs (enjeux org), lisible par tous.
Nouveauté clé : on **assume honnêtement** que des extraits internes sont envoyés au LLM à la fin — le
« presque » devient le fil de l'histoire. v1 et v2 conservées à côté. Matière : `APPRENTISSAGES.md` §1.

---

## Version à poster (slides)

---

### Slide 1 — Couverture

On croit que « faire de l'IA », c'est tout confier à ChatGPT.

Mon moteur de recherche juridique, lui, ne lui envoie **presque rien**.

Ce « presque » est le mot le plus important de ce post. Restez, je l'explique.

---

### Slide 2

D'abord, la croyance qui bloque beaucoup d'organisations.

On imagine qu'une IA, c'est **un seul gros cerveau** dans le cloud : on lui envoie tout, il répond, on
paie à chaque fois, et nos documents sensibles se promènent chez un fournisseur.

Avec cette image en tête, difficile de dire oui. Sauf que l'image est fausse — il n'y a pas un modèle,
il y en a **deux**, et ils ne font pas du tout le même métier.

---

### Slide 3

Le premier, celui qui fait le gros du travail, s'appelle un **embedding**. Retenez juste ce qu'il fait :
il **range vos textes par sens**.

Deux passages qui parlent de la même chose finissent voisins, même s'ils n'utilisent aucun mot en
commun. « Capital d'une SARL » et « montant de départ d'une société » se retrouvent côte à côte.

Ce modèle-là ne parle pas. Il trie. Et surtout : il tient sur un ordinateur normal, **gratuitement,
sans rien envoyer nulle part**.

---

### Slide 4

Du coup, chercher une réponse devient simple.

Votre question est rangée sur la même « carte du sens », et on regarde quels articles se trouvent juste
à côté. C'est de la géométrie, pas de la conversation — et ça se passe entièrement chez vous.

À ce stade, aucune donnée n'est sortie. Zéro. C'est la moitié de l'histoire qu'on ne raconte jamais.

---

### Slide 5

Vient le deuxième modèle : le **LLM**. Lui, c'est le bavard — la famille de ChatGPT. Son rôle est
étroit : **rédiger** la réponse finale, proprement, avec la citation.

Mais pour rédiger, il faut bien lui montrer de quoi parler. Alors oui — **on lui envoie les quelques
passages qu'on vient de trouver.** Pas votre base entière : trois ou quatre paragraphes, ceux qui
concernent *cette* question précise.

Voilà le fameux « presque ». On n'envoie pas tout. On envoie le strict nécessaire.

---

### Slide 6 — Ce que ça change, concrètement

La différence entre « tout envoyer » et « envoyer trois paragraphes » n'est pas un détail technique.
C'est une décision de direction, avec trois effets :

**Le coût.** La partie qui tourne des milliers de fois par jour (la recherche) est gratuite. On ne paie
le bavard que pour de courtes rédactions. La facture arrête de faire peur.

**Les données.** Vous n'exposez plus un coffre-fort, mais quelques extraits choisis — que vous pouvez
filtrer, voire anonymiser avant l'envoi. Et si le sujet l'exige, on peut même faire tourner le bavard en
interne, pour que rien ne sorte du tout.

**L'indépendance.** Le cœur du système ne dépend d'aucun fournisseur. C'est vous qui tenez la carte.

---

### Slide 7 — La nuance honnête

Le message n'est pas « le cloud, c'est le mal », ni « le gros modèle ne sert à rien ». Il est excellent —
au bon endroit, pour la bonne tâche.

La maturité, c'est de savoir **ce qui doit rester chez soi** (le corpus, la recherche) et **ce qui peut
sortir** (un extrait, une fois filtré). Ce partage-là, personne ne le fera à votre place.

---

### Slide 8 — Conclusion

« Faire de l'IA » n'est ni une facture unique, ni un fournisseur à qui l'on remet les clés.

C'est une série de choix — sur ce qui reste, ce qui part, ce qu'on paie, ce dont on dépend. Des choix
qui ne sont pas techniques. Ils sont stratégiques.

Dans votre organisation, qui décide de ce qui a le droit de sortir ?

#IA #RAG #Embeddings #LLM #Stratégie #Données #Confidentialité

---

## Notes de rédaction (ne pas poster)

- **Direction** : synthèse demandée = esprit v1 (pédago, mots *embedding*/*LLM* assumés, « carte du
  sens ») + ton humain de v2 (pas de flèche répétée, fins variées) + cible décideurs (enjeux org).
- **Honnêteté = le fil narratif** : le « presque » (slide 1) est posé comme énigme, résolu slide 5
  (oui, on envoie des extraits), puis transformé en enjeu de décision slide 6. Corrige l'erreur de la v2
  qui laissait croire que rien de sensible ne sortait.
- **Mots-clés gardés** (demande utilisateur) : *embedding* (slide 3) et *LLM* (slide 5), chacun expliqué
  en une phrase, sans en faire un cours.
- **Anti-gabarit** : les slides finissent différemment — énigme (1), bascule (2), fait (3), punch (4, 5),
  liste d'enjeux (6), avis (7), question (8). Aucune formule de transition répétée.
- **Chiffres/faits réels** : embedding local gratuit, 3-4 passages envoyés, option LLM local. Rien
  d'inventé.
- **Visuel suggéré** : la « carte du sens » (points-phrases, la question qui tombe près des bons
  articles) pour slides 3-4 ; puis un schéma « maison » slide 5-6 : le corpus reste dedans, une petite
  flèche sort avec « 3 extraits » — matérialise le « presque ».
- **Choix entre versions** : v3 = recommandée (équilibre pédagogie + honnêteté + décideurs). v1 = plus
  grand public/tech. v2 = 100 % dirigeant mais édulcorée sur les données (à ne pas publier telle quelle).
