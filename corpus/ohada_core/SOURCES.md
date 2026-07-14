# Corpus `ohada_core` — sources

Les PDF ne sont **pas** versionnés (cf `.gitignore`). Les retélécharger avec les commandes ci-dessous
pour reconstruire le corpus à l'identique.

| Fichier | Texte | Version | Source |
|---|---|---|---|
| `AUSCGIE-2014.pdf` | Acte uniforme relatif au droit des sociétés commerciales et du GIE | révisé 30/01/2014 (JO fév. 2014) | justice.sec.gouv.sn |
| `AUDCG-2010.pdf` | Acte uniforme relatif au droit commercial général (statut entreprenant, RCCM) | révisé 15/12/2010 (Lomé) | acpce.cg |

## Retélécharger

```bash
mkdir -p corpus/ohada_core
curl -sL -A "Mozilla/5.0" -o corpus/ohada_core/AUSCGIE-2014.pdf \
  "https://justice.sec.gouv.sn/wp-content/uploads/textes-reglements/OHADA/Acte-uniforme-relatif-droit-societes-commerciales-gie-auscgie-jo-fevrier-2014.pdf"
curl -sL -A "Mozilla/5.0" -o corpus/ohada_core/AUDCG-2010.pdf \
  "https://acpce.cg/wp-content/uploads/2024/08/ACTE-UNIFORME-REVISE-PORTANT-SUR-LE-DROIT-COMMERCIAL-GENERAL.pdf"
```

> Note : ce sont des textes de loi OHADA (domaine public). On retient la **version révisée** de chaque
> acte car c'est elle qui fait foi (l'AUDCG 2010 introduit le statut d'entreprenant cité dans la spec §2).
> Toujours vérifier après download : `file corpus/ohada_core/*.pdf` doit afficher « PDF document ».
