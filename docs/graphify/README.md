# Carte du code (Graphify)

Cette carte est produite par [Graphify](https://github.com/Graphify-Labs/graphify) (paquet PyPI `graphifyy`, licence Apache-2.0). Elle est proche d'un RAG, mais l'index n'est pas un tas de fragments de texte : c'est le **graphe du code**. On y voit qui appelle quoi, les dépendances entre symboles, et des communautés (groupes de code fortement reliés).

Aucun modèle de langage ni clé d'API n'intervient : l'extraction est l'analyse syntaxique locale (`--code-only`), puis un regroupement sans nommage par LLM (`cluster-only --no-label`).

Le commit source et la version de l'outil sont dans [`GENERATION.md`](GENERATION.md).

## Consulter

- Ouvrir [`graph.html`](graph.html) dans un navigateur. Le graphe est embarqué dans le fichier. L'affichage charge [vis-network](https://unpkg.com/vis-network@9.1.6/standalone/umd/vis-network.min.js) depuis unpkg.com : une connexion réseau est nécessaire pour le dessin, pas pour lire les données.
- Lire [`GRAPH_REPORT.md`](GRAPH_REPORT.md) : volume du graphe, communautés, nœuds les plus connectés, liens inattendus.
- Interroger le graphe depuis la racine du dépôt (Graphify installé) :

```bash
graphify query "où est gérée la vue AR ?" --graph docs/graphify/graph.json
```

La question est rapprochée des **noms de symboles**. Les mots très courts (comme « AR ») sont ignorés par l'outil, donc cette formulation peut ne rien ramener. Dans ce cas, partir d'un nom lu dans le rapport :

```bash
graphify query "ArOverlay" --graph docs/graphify/graph.json
graphify explain "ArTracker" --graph docs/graphify/graph.json
```

## Consigne pour les agents IA

Avant toute recherche large dans le code (parcours du dépôt, grep étendu, lecture de nombreux fichiers), consulter d'abord [`GRAPH_REPORT.md`](GRAPH_REPORT.md) et [`graph.json`](graph.json) — par la lecture du rapport ou par `graphify query "…" --graph docs/graphify/graph.json`. N'élargir la recherche dans les sources que si la carte ne suffit pas à localiser le code concerné.

La carte peut être incomplète sur certains fichiers Kotlin (l'extracteur signale des erreurs de syntaxe et n'en retient alors qu'une partie). Elle sert à s'orienter, elle ne remplace pas la lecture du fichier une fois celui-ci trouvé.

## Mise à jour

Le workflow [`.github/workflows/graphify.yml`](../../.github/workflows/graphify.yml) régénère cette carte et commite le résultat sur la branche `outils/graphify` :

- à la main, avec `workflow_dispatch` (entrée optionnelle `branche_source` ; si elle est vide, la branche `etape-3-*` dont le commit est le plus récent) ;
- chaque jour à 03:30 UTC (`30 3 * * *`).

GitHub n'exécute le planning (`schedule`) que si le workflow est présent sur la branche par défaut (`main`). Le lancement manuel (`workflow_dispatch`) a la même condition : tant que le fichier n'est pas sur `main`, l'API répond que le workflow est introuvable sur la branche par défaut. Les deux demandent donc l'accord du propriétaire pour ajouter `.github/workflows/graphify.yml` sur `main` (sans y merger la carte). Une fois le fichier sur `main`, `workflow_dispatch` peut viser la branche `outils/graphify`.

La régénération installe `graphifyy` 0.9.112, lance `graphify extract . --code-only` puis `graphify cluster-only <répertoire> --no-label`, et pousse `docs/graphify/` avec le `GITHUB_TOKEN` (`contents: write`). Aucun secret supplémentaire. Les répertoires `build/`, `.gradle/` et `docs/graphify/` sont exclus de l'analyse. Le code de l'application n'est pas modifié : le commit ne porte que la carte, sur `outils/graphify`.
