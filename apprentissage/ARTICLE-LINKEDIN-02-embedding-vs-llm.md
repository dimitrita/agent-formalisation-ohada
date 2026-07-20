# Article LinkedIn n°2 — « Mon moteur de recherche juridique n'a pas de ChatGPT dedans »

Statut : **brouillon prêt à relire**. Sujet n°2 du backlog (embedding ≠ LLM). Tiré du projet agent
OHADA (Java / Spring AI / pgvector). Matière : `apprentissage/APPRENTISSAGES.md` §1.

Format : **carrousel LinkedIn**. Chaque slide est autoportante ET enchaîne avec la suivante (histoire,
pas liste). Objectif : démystifier « l'IA », montrer une compréhension fine + un choix d'archi
(local/gratuit/privé). Lisible par technicien **ou non**.

---

## Version à poster (slides)

---

### Slide 1 — Couverture (l'accroche)

**Mon moteur de recherche juridique « comprend » les questions.**
**Et pourtant, il n'y a AUCUN ChatGPT dedans.**

Comment une machine peut-elle trouver la bonne réponse… sans « intelligence qui parle » ?

*(Je vous explique — vous ne verrez plus jamais l'IA pareil. →)*

---

### Slide 2 — La croyance qu'on a tous

Quand on dit « IA », on pense à **un truc qui parle** : ChatGPT, un assistant, un cerveau qui répond.

Du coup, on imagine que **toute** application d'IA = un gros modèle bavard, quelque part sur un serveur,
à qui on envoie tout.

C'est faux. Et ça change tout — coût, vie privée, simplicité.

*Pour le voir, il faut connaître un 2e type de modèle, beaucoup plus discret. →*

---

### Slide 3 — Il existe DEUX familles de modèles

Dans mon projet, il y en a deux, avec **deux métiers différents** :

🗣️ **Le bavard (le LLM)** — transforme du texte en texte. Il *rédige* une réponse. (Ex. : Claude, ChatGPT.)

📐 **Le rangeur (l'embedding)** — transforme du texte en **nombres**. Il ne parle pas. Il *range*.

La recherche, ce n'est pas le bavard qui la fait. C'est le rangeur.

*Mais « ranger du texte en nombres »… ça veut dire quoi ? →*

---

### Slide 4 — Ce que fait le « rangeur »

Imaginez une **carte** (comme un GPS), mais au lieu de villes, on y place des **phrases**.

Le rangeur lit une phrase et lui donne une **position** sur cette carte.
Sa règle : **deux phrases qui parlent de la même chose → deux points proches.**

« Capital minimum d'une SARL » et « montant de départ d'une société » atterrissent **côte à côte**,
même sans aucun mot commun.

*Et voilà pourquoi on n'a pas besoin d'un cerveau bavard pour chercher. →*

---

### Slide 5 — Chercher = trouver le point le plus proche

Quand vous posez une question, on la place **elle aussi** sur la carte (même rangeur).

Puis on regarde : **quels articles de loi sont les points les plus proches ?**

C'est tout. Pas de « réflexion », pas de phrase générée. Juste une **distance** entre des points.
Chercher par le **sens**, pas par mots-clés — avec de la géométrie, pas de la conversation.

*Le plus beau, c'est ce que ce petit rangeur permet. →*

---

### Slide 6 — Le rangeur tient dans votre poche

Le bavard (LLM) est **énorme** et vit souvent sur un serveur payant : chaque question coûte, et vos
données **sortent** de chez vous.

Mon rangeur, lui : **~80 Mo**, tourne **sur mon ordinateur**, **zéro connexion**, **zéro euro** par
question. Les textes de loi — et les questions des gens — **ne quittent jamais la machine**. 🔒

Pour un sujet sensible (le juridique), ça n'est pas un détail : c'est un argument.

*Alors… le bavard ne sert à rien ? Si — mais seulement à la toute fin. →*

---

### Slide 7 — Chacun son rôle (et c'est ça, l'archi)

L'ordre réel dans mon agent :

1. 📐 **Le rangeur** trouve les bons articles (par le sens). — *local, gratuit*
2. 🗣️ **Le bavard** rédige la réponse **en s'appuyant sur ces articles**, avec la citation. — *au final*

Le bavard n'invente rien : il **reformule des sources qu'on lui a apportées**. Deux outils, deux
métiers, dans le bon ordre.

*La vraie leçon tient en une phrase. →*

---

### Slide 8 — La leçon

**« IA » ne veut pas dire « un gros modèle qui parle à qui on envoie tout ».**

Souvent, le travail utile est fait par un **petit modèle discret, local et gratuit** — et le bavard
n'intervient qu'à la fin, encadré.

Comprendre ça, c'est reprendre le contrôle : sur le **coût**, sur la **vie privée**, sur ce qu'on
**construit vraiment**.

Et vous — vous pensiez qu'il fallait un ChatGPT pour « chercher intelligemment » ? 👇

#IA #RAG #Embeddings #LLM #Java #OHADA #ViePrivée #MachineLearning

---

## Notes de rédaction (ne pas poster)

- **Format** : 8 slides. Chaque slide finit par une flèche `→` = lien narratif explicite vers la
  suivante (exigence CLAUDE.md : liaison entre slides, histoire autoportante).
- **Fil narratif** : croyance (2) → révélation des 2 familles (3) → ce que fait l'embedding (4-5) →
  bénéfice concret local/privé (6) → rôle du LLM à la fin (7) → leçon (8). Tension = « comment sans
  ChatGPT ? », résolue slide 5, prolongée en bénéfice.
- **Vulgarisation** : « embedding » → « le rangeur » + carte/GPS du sens ; « LLM » → « le bavard ».
  Termes techniques gardés une fois entre parenthèses pour le public tech, jamais imposés.
- **Chiffres réels** : ~80 Mo (all-MiniLM), local, zéro coût/connexion. Ne pas gonfler.
- **Visuel suggéré** : une **carte 2D** avec des points-phrases, la question qui « tombe » près des
  bons articles (slides 4-5). Icônes 📐 rangeur / 🗣️ bavard réutilisées slide 3 et 7 (cohérence).
- **Accroche alternative slide 1** : « Tout le monde croit que l'IA, c'est ChatGPT. Mon moteur juridique
  prouve le contraire. »
- **Pont vers article n°1 (reranking)** : possible en commentaire — « une fois les bons articles
  trouvés, comment je les classe ? → article précédent ». Crée une série.
