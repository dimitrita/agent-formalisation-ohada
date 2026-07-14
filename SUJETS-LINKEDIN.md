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

### n°2 — « Non, votre RAG n'a pas besoin d'un LLM pour tout »
Embedding ≠ LLM. Démystifie la confusion la plus fréquente : on peut vectoriser **en local, gratuitement**,
sans appel API. Angle pédagogique + argument coût/confidentialité. Matière : `APPRENTISSAGES.md` §1.

### n°3 — « Comment je sais que mon RAG répond juste (sans deviner) »
Le **golden set** + `recall@k`. Montre la rigueur d'évaluation (mesure reproductible vs « ça a l'air de
marcher »). Très fort pour un profil technico-fonctionnel. Matière : `EVALUATION-RAG.md`.

### n°4 — « Le vrai problème du RAG juridique, c'est le chunking »
Découper par **article** (unité citable), pas par taille fixe. La citation obligatoire comme contrainte
d'architecture. Angle métier/domaine. Matière : `APPRENTISSAGES.md` §2 + §3 + §5 (le bug 1112→906).

### n°5 — « Architecturer un RAG multi-pays : le pattern plugin »
`ohada_core` transverse + collections pays (`cm_procedures`). Comment ajouter un pays sans toucher au
reste. Angle archi/scalabilité. Matière : `APPRENTISSAGES.md` §6.

---

## Prochaine étape

Choisir le sujet à développer → créer `ARTICLE-LINKEDIN-01-<slug>.md` (structure : accroche, corps,
visuel/schéma, punchline, appel à discussion).
