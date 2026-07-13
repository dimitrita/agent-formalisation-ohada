# Spec V1 — Agent de formalisation d'entreprise en zone OHADA

> **Statut** : V1 (contrat de sortie figé). Orchestration LangGraph 1.x.
> **But de la V1** : à partir du profil d'un porteur de projet, recommander une **forme juridique** justifiée et citée, produire une **feuille de route procédurale** propre au pays, et générer un **projet de statuts + dossier documentaire**, avec deux points d'approbation humaine.

---

## 1. Périmètre & principes

### 1.1 Périmètre V1 (volontairement resserré)
- **Un seul pays cible** : **Côte d'Ivoire (guichet unique CEPICI)**. Le socle juridique reste pan-OHADA ; seule la couche procédurale est mono-pays.
- **Formes couvertes** : Entreprenant, Entreprise individuelle (EI), SARL, SARL unipersonnelle, SA (SAS mentionnée mais hors génération de statuts en V1).
- **Livrables** : (a) recommandation de forme, (b) roadmap démarches CI, (c) projet de statuts + DNSV + checklist pièces.

### 1.2 Hors périmètre V1 (→ V2)
- Autres pays OHADA (Sénégal/APIX, Bénin/APIEx-GUFE, Cameroun…) → via **country plugins** (même pattern que le moteur fiscal Konta).
- Activités réglementées (banque, santé, sécurité privée, télécom…) : **détectées et signalées**, mais l'agent **refuse de produire la roadmap complète** et renvoie vers l'autorité de tutelle.
- Dépôt/soumission automatisée en ligne (e-CEPICI) → V2, derrière l'adaptateur `submit_dossier()`.

### 1.3 Garde-fous (non négociables, hérités du pattern AI Act Copilot)
- **Aide à la décision, pas conseil juridique.** Disclaimer systématique en tête de tout livrable.
- **Citation obligatoire** : toute affirmation normative (forme, capital, pièce, délai) doit porter une référence source (article d'Acte uniforme OU page CEPICI). Une affirmation non sourcée est un échec d'éval, pas une sortie valide.
- **Pas d'hallucination de coûts/délais** : si la valeur n'est pas dans le corpus, l'agent écrit `à vérifier auprès du guichet` plutôt qu'un chiffre inventé.

---

## 2. Corpus RAG

Deux collections vectorielles distinctes (namespaces séparés → permet un routage de retrieval par agent).

| Collection | Contenu | Source primaire | Usage |
|---|---|---|---|
| `ohada_core` | AUSCGIE (2014), AUDCG (statut entreprenant, RCCM) | PDF officiels ohada.org / ohada.com | Agent Forme Juridique, Agent Rédacteur |
| `ci_procedures` | Formalités, pièces, coûts, délais CEPICI | cepici.gouv.ci, servicepublic.gouv.ci | Agent Démarches |

**Chunking** : par article pour `ohada_core` (l'unité de citation = l'article, ex. `AUSCGIE art. 311`). Par étape/procédure pour `ci_procedures`.
**Metadata obligatoire par chunk** : `{source, ref, pays, date_maj, url}` — `ref` alimente directement le champ `citations` de l'état.

---

## 3. Schéma d'état

```python
from typing import Annotated, Literal, TypedDict
from operator import add
from langgraph.graph import add_messages

# --- Sous-types ---

class PorteurProfil(TypedDict):
    activite: str                    # description libre de l'activité
    activite_reglementee: bool | None
    pays: str                        # "CI" en V1
    nb_associes: int
    capital_envisage_fcfa: int | None
    apports_en_nature: bool
    resident: bool                   # porteur résident vs étranger
    budget_formalites_fcfa: int | None
    objectif: str                    # "responsabilité limitée", "coût minimal", "levée de fonds"...

class Citation(TypedDict):
    claim: str                       # affirmation supportée
    source: str                      # "AUSCGIE" | "AUDCG" | "CEPICI"
    ref: str                         # "art. 311" | "page RCCM SARL"
    url: str

class RecoForme(TypedDict):
    forme: Literal["ENTREPRENANT","EI","SARL","SARL_U","SA","SAS"]
    justification: str
    formes_ecartees: list[dict]      # [{forme, raison}]
    capital_min_applicable: str
    confiance: float                 # 0..1, pour routage conditionnel

class Demarche(TypedDict):
    ordre: int
    intitule: str
    organisme: str                   # CEPICI / DGI / CNPS / Notaire...
    pieces: list[str]
    cout_fcfa: str                   # str car peut valoir "à vérifier"
    delai: str

class DossierStatuts(TypedDict):
    statuts_md: str                  # projet de statuts (markdown)
    dnsv_requise: bool               # Déclaration Notariée de Souscription/Versement
    checklist_pieces: list[str]

# --- Reducers custom ---

def merge_last(a, b):
    """Prend la dernière valeur non nulle (nœuds séquentiels)."""
    return b if b is not None else a

# --- État global ---

class FormalisationState(TypedDict):
    # entrée / dialogue
    messages: Annotated[list, add_messages]
    profil: PorteurProfil

    # sorties d'agents (fork-join → reducers additifs / last-write)
    reco_forme: Annotated[RecoForme | None, merge_last]
    demarches: Annotated[list[Demarche], add]        # branche parallèle A
    dossier: Annotated[DossierStatuts | None, merge_last]  # branche parallèle B
    citations: Annotated[list[Citation], add]         # accumulées par TOUS les nœuds

    # contrôle / HITL
    forme_validee_humain: bool
    blocage: str | None              # ex. "activite_reglementee" → arrêt propre
    route: str | None                # décision du superviseur

    # observabilité
    trace_id: str | None
```

> **Note reducers** : `citations` et `demarches` utilisent `add` car remplis en parallèle (fork-join) → concaténation. `reco_forme` et `dossier` sont écrits par un seul nœud chacun → `merge_last` évite les conflits de merge.

---

## 4. Squelette du graphe

### 4.1 Topologie

```
                       ┌─────────────┐
                       │   intake    │  (peut interrupt: infos manquantes)
                       └──────┬──────┘
                              │
                    ┌─────────▼─────────┐
                    │  garde_reglemente │  conditional edge
                    └───┬───────────┬───┘
              bloqué    │           │  ok
              ┌─────────▼──┐   ┌────▼──────────┐
              │  fin_blocage│   │ forme_juridique│
              └────────────┘   └───────┬────────┘
                                       │
                              ┌────────▼────────┐
                              │ INTERRUPT #1    │  approbation forme (HITL)
                              └────────┬────────┘
                          revalider ◄──┤ conditional (forme_validee_humain)
                                       │ validé
                        ┌──────────────┴──────────────┐   FORK
                 ┌──────▼──────┐              ┌────────▼────────┐
                 │  demarches  │              │   redacteur     │
                 └──────┬──────┘              └────────┬────────┘
                        └──────────────┬──────────────┘   JOIN
                              ┌────────▼────────┐
                              │  superviseur    │  assemble le dossier
                              └────────┬────────┘
                              ┌────────▼────────┐
                              │ INTERRUPT #2    │  revue finale (HITL)
                              └────────┬────────┘
                                       │
                                 ┌─────▼─────┐
                                 │    END    │
                                 └───────────┘
```

### 4.2 Câblage

```python
from langgraph.graph import StateGraph, START, END
from langgraph.types import interrupt, Command

g = StateGraph(FormalisationState)

g.add_node("intake", intake_node)
g.add_node("garde_reglemente", garde_reglemente_node)
g.add_node("fin_blocage", fin_blocage_node)
g.add_node("forme_juridique", forme_juridique_node)
g.add_node("approbation_forme", approbation_forme_node)   # contient interrupt()
g.add_node("demarches", demarches_node)
g.add_node("redacteur", redacteur_node)
g.add_node("superviseur", superviseur_node)
g.add_node("revue_finale", revue_finale_node)             # contient interrupt()

g.add_edge(START, "intake")
g.add_edge("intake", "garde_reglemente")

# garde-fou activité réglementée
g.add_conditional_edges(
    "garde_reglemente",
    lambda s: "bloque" if s.get("blocage") else "ok",
    {"bloque": "fin_blocage", "ok": "forme_juridique"},
)
g.add_edge("fin_blocage", END)

g.add_edge("forme_juridique", "approbation_forme")

# HITL #1 : boucle tant que non validé
g.add_conditional_edges(
    "approbation_forme",
    lambda s: "valide" if s["forme_validee_humain"] else "revoir",
    {"valide": "fork", "revoir": "forme_juridique"},
)

# FORK — parallélisme
g.add_edge("approbation_forme", "demarches")   # remplacé par un nœud "fork" no-op
g.add_edge("approbation_forme", "redacteur")

# JOIN — superviseur attend les deux branches
g.add_edge("demarches", "superviseur")
g.add_edge("redacteur", "superviseur")

g.add_edge("superviseur", "revue_finale")
g.add_edge("revue_finale", END)

graph = g.compile(checkpointer=checkpointer)  # checkpointer requis pour interrupt()
```

> **Fork-join** : en LangGraph, un nœud qui a plusieurs `add_edge` sortants déclenche l'exécution parallèle des cibles ; un nœud cible de plusieurs arêtes (`superviseur`) attend que **toutes** les branches amont soient terminées (barrière de jointure implicite). Le merge des états parallèles est géré par les reducers du §3.

---

## 5. Contrats de sortie par nœud (le point à figer)

Chaque nœud **doit** retourner exactement ces clés. Toute clé normative s'accompagne d'entrées dans `citations`.

| Nœud | Lit | Écrit (contrat) | Citations exigées |
|---|---|---|---|
| `intake` | `messages` | `profil` (complet ou `interrupt` pour compléter) | non |
| `garde_reglemente` | `profil.activite` | `blocage` (`"activite_reglementee"` \| `None`) | oui si bloqué |
| `forme_juridique` | `profil` + RAG `ohada_core` | `reco_forme`, `citations[+]` | **oui** (chaque `formes_ecartees` justifiée) |
| `approbation_forme` | `reco_forme` | `forme_validee_humain`, éventuel feedback dans `messages` | non |
| `demarches` | `reco_forme`, `profil.pays` + RAG `ci_procedures` | `demarches[+]`, `citations[+]` | **oui** (coût/délai/pièces sourcés) |
| `redacteur` | `reco_forme`, `profil` + RAG `ohada_core` | `dossier`, `citations[+]` | **oui** (clauses statutaires référencées AUSCGIE) |
| `superviseur` | tout l'état | assemble le livrable final (markdown), pas de nouvelle donnée normative | non |
| `revue_finale` | livrable | `interrupt` → validation/annotations humaines | non |

---

## 6. Human-in-the-loop (interrupts)

```python
def approbation_forme_node(state: FormalisationState):
    decision = interrupt({
        "type": "approbation_forme",
        "reco": state["reco_forme"],
        "question": "Valider cette forme juridique ?",
        "actions": ["valider", "demander_alternative"],
    })
    if decision["action"] == "valider":
        return {"forme_validee_humain": True}
    return {
        "forme_validee_humain": False,
        "messages": [{"role": "user", "content": decision.get("feedback", "")}],
    }
```

- **Interrupt #1 (forme)** : bloquant, car tout le downstream (démarches + statuts) en dépend. Boucle de reprise vers `forme_juridique` avec le feedback humain injecté dans `messages`.
- **Interrupt #2 (revue finale)** : validation avant export du dossier. Actions : `approuver` / `annoter` / `regenerer_section`.
- **UI** : Streamlit comme gate d'approbation (réutilise ton pattern Anaplan). Le `checkpointer` (Postgres) permet la reprise après interruption.

---

## 7. Observabilité — Langfuse

- **Trace racine** = une session de formalisation ; `trace_id` propagé dans l'état.
- **Spans** par nœud + sous-spans par appel RAG (query, chunks retournés, scores).
- **Tags** : `pays`, `forme_recommandee`, `nb_interrupts`.
- **Scores** attachés à la trace : `citation_coverage`, `judge_score` (§8) → alimentent le golden dataset.

---

## 8. Pipeline d'évaluation

**Hybride, évaluateurs déterministes prioritaires** (même philosophie que ton eval Anaplan) :

**Déterministes (linters) — bloquants en CI :**
1. `citation_coverage` : % d'affirmations normatives portant une `ref`. Seuil V1 = 100 % (toute affirmation forme/capital/pièce/délai sourcée).
2. `no_hallucinated_cost` : aucun montant FCFA hors corpus (regex sur montants ∩ absence de source → fail).
3. `schema_conformity` : chaque sortie de nœud respecte le contrat du §5.
4. `capital_rule_check` : cohérence forme ↔ règle capital (ex. SARL CI = pas de minimum légal imposé).

**LLM-as-judge (Generator→Evaluator, ton pattern connu) — qualitatif :**
5. Qualité de la justification de forme (pertinence vs profil, mention des formes écartées).
6. Complétude de la roadmap (aucune étape structurante manquante vs référentiel CEPICI).

**Golden dataset** : ~15 profils de départ (mono-associé coût minimal, 3 associés responsabilité limitée, étranger, apports en nature, activité réglementée → attendu = blocage…), issus de traces Langfuse réelles une fois le prototype en marche.

---

## 9. Couture V1 → V2 (adaptateurs fins)

Deux adaptateurs isolent la partie mono-pays (même principe que `getModel()` / `applyChanges()` Anaplan) :

```python
# V1 : CI hardcodé. V2 : dispatch par pays (country plugins).
def get_country_procedures(pays: str, forme: str) -> list[Demarche]: ...
def submit_dossier(pays: str, dossier: DossierStatuts) -> SubmissionResult: ...  # V2 only
```

- V1 : `get_country_procedures` lit uniquement `ci_procedures`.
- V2 : registre `{pays: plugin}` → ajout Sénégal/Bénin/Cameroun sans toucher au graphe. Le socle `ohada_core` reste partagé (c'est tout l'intérêt OHADA).

---

## 10. Prochaine étape

Deux options pour la suite (comme pour la due diligence) :
- **(A)** décomposition détaillée d'**un seul nœud** — je recommande `forme_juridique`, car c'est lui qui porte le contrat de citation et conditionne tout le downstream ;
- **(B)** le **prompt système + schéma de retrieval** de la couche RAG `ohada_core` (requête → chunks → format de citation imposé).
```