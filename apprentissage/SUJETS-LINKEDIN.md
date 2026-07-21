# Sujets d'articles LinkedIn — projet agent RAG OHADA

Backlog de sujets tirés du projet (agent de formalisation d'entreprise en zone OHADA, stack Java /
Spring AI / pgvector / reranking). Chaque sujet développé aura **son propre fichier dédié**
(`ARTICLE-LINKEDIN-xx-<slug>.md`).

Positionnement visé : **Chef de projet IA agentique technico-fonctionnel / Architecte IA / Consultant IA**.
→ Les sujets doivent montrer à la fois la **compréhension technique** ET la **capacité à vulgariser /
décider** (ce que cherche un profil technico-fonctionnel).

---

## ⭐ Sujet recommandé n°1 — « Mon RAG me renvoyait le mauvais article 2 fois sur 3 »

**Angle** : le retrieval vectoriel seul ne suffit pas — retour d'expérience chiffré sur le reranking.

**Hook (accroche)** :
> « J'ai posé une question simple à mon RAG juridique : *capital minimum d'une SARL ?*
> Il m'a renvoyé, confiant, l'article sur les SA. 2 fois sur 3, le top-1 était faux.
> Voici pourquoi — et le composant à ~80 Mo qui a tout changé. »

**Pourquoi ce sujet gagne** :
- **Hook concret et contre-intuitif** : un échec chiffré accroche plus qu'un tuto générique.
- Montre une démarche d'**ingénieur qui mesure** (baseline → constat d'échec → fix → preuve), pas un
  bricoleur. Exactement le signal « niveau entreprise ».
- Explique une notion mal comprise : **bi-encoder (retrieval) vs cross-encoder (reranking)**, le schéma
  *retrieve top-N large → rerank → top-K*.

**Message clé à faire passer** : un RAG de qualité = **deux étages** (rappel large grossier + reclassement
fin), pas un seul `top-k` brut. Et ça peut rester **local, gratuit, privé**.

**Public** : recruteurs tech, praticiens IA, décideurs qui « ont fait un POC RAG qui marche à moitié ».

**Matière disponible** : `APPRENTISSAGES.md` §8 (reranking) + `DEROULEMENT.md` tranche 4 (chiffres réels :
307 articles AUDCG, 1213 chunks, top-1 faux 2/3).

---

## Autres sujets candidats (à développer plus tard)

### n°2 — « Mon moteur de recherche juridique n'a pas de ChatGPT dedans » ✅ ÉCRIT
Embedding ≠ LLM. Démystifie la confusion la plus fréquente : on peut vectoriser **en local, gratuitement**,
sans appel API. Angle pédagogique + argument coût/confidentialité. Matière : `APPRENTISSAGES.md` §1.
→ Brouillon : `ARTICLE-LINKEDIN-02-embedding-vs-llm.md` (carrousel 8 slides).

### n°3 — « Comment je sais que mon RAG répond juste (sans deviner) »
Le **golden set** + `recall@k`. Montre la rigueur d'évaluation (mesure reproductible vs « ça a l'air de
marcher »). Très fort pour un profil technico-fonctionnel. Matière : `EVALUATION-RAG.md`.

### n°4 — « Le vrai problème du RAG juridique, c'est le chunking »
Découper par **article** (unité citable), pas par taille fixe. La citation obligatoire comme contrainte
d'architecture. Angle métier/domaine. Matière : `APPRENTISSAGES.md` §2 + §3 + §5 (le bug 1112→906).

### n°5 — « Architecturer un RAG multi-pays : le pattern plugin »
`ohada_core` transverse + collections pays (`cm_procedures`). Comment ajouter un pays sans toucher au
reste. Angle archi/scalabilité. Matière : `APPRENTISSAGES.md` §6.

### ⭐ n°6 — « Votre RAG cherche mal parce que la question de l'utilisateur n'est PAS la requête »
**Angle** : la *query construction* — étape invisible et décisive, où le **métier** entre dans la
mécanique. On ne cherche pas avec la question brute de l'utilisateur : on **fabrique** la requête à partir
du contexte (client, dossier, montant…) et de l'**objectif** de la tâche. Message : c'est un principe
**universel**, pas un truc juridique.

**Format = 3 cas CROSS-MÉTIERS (secteurs différents) + le cas OHADA en PREUVE vécue.**
Les 3 génériques posent le principe (le lecteur RH / banque / e-commerce se reconnaît) ; le cas OHADA
prouve « je l'ai fait pour de vrai, avec du code et des chiffres ». Chaque cas = 1 slide : *question brute
pauvre* → *requête construite riche* → *ce que ça change*.

- **Cas A — Support / SAV e-commerce.** Question brute : « ma commande est en retard ». Requête construite :
  + `délai livraison, politique de retard, remboursement, transporteur` + orientée par le **statut client**
  (VIP → conditions spéciales). Sans ça : on ramène la FAQ générique, pas la règle applicable à CE client.
- **Cas B — RH interne.** Question brute : « combien de congés il me reste ? ». Requête construite :
  + `solde congés payés, RTT, ancienneté, convention collective` + filtrée par **pays/contrat** du salarié.
  Montre qu'un même intitulé donne des réponses différentes selon le contexte injecté.
- **Cas C — Banque / conformité (climax).** Question brute : « ce virement est-il autorisé ? ». Requête
  construite : + `plafond, pays destinataire, KYC, sanctions, seuil de déclaration` + orientée par le
  **montant** (gros montant → aimant vers les règles anti-blanchiment). Ici l'**objectif** (autoriser vs
  contrôler) réoriente la recherche → l'exemple le plus « waouh » pour un décideur.
- **Cas preuve — OHADA (le nôtre).** Le seul avec code réel (`construireRequete`) + articles réellement
  remontés (art. 5 « associé unique »). Vocabulaire **littéral** de la loi (pas les sigles, §9), branche
  selon le nb d'associés. Crédibilité : les 3 cas posent le principe, celui-ci le matérialise.

**Message clé (décideurs)** : la pertinence d'un RAG se joue **avant** le LLM et **avant** le reranking —
dans la façon dont on **traduit un besoin métier en recherche**. Mauvaise traduction = bons outils, mauvais
résultats. C'est un **choix de domaine** (vos experts métier doivent valider la requête, pas seulement les
devs), pas un réglage technique → exactement le pont technico-fonctionnel.

**Attention rédaction** (cf CLAUDE.md) : cadrer chaque cas par sa **conséquence business**, pas par la
mécanique. Analogie fil rouge = l'**aimant** (la requête attire les bons documents) / le **filet** (large au
retrieval, resserré au rerank). Cas A/B/C = illustratifs (inventés mais réalistes, à assumer comme tels) ;
cas OHADA = **chiffres/requête RÉELS** (générer via `construireRequete` avant d'écrire).

**Matière** : `APPRENTISSAGES.md` §10 + `FormeJuridiqueService.construireRequete` + §9 (vocabulaire littéral).

---

## Prochaine étape

Choisir le sujet à développer → créer `ARTICLE-LINKEDIN-01-<slug>.md` (structure : accroche, corps,
visuel/schéma, punchline, appel à discussion).
