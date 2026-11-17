# Génération de la carte

- Branche source : `etape-3-v176d-synchro-reelle`
- Commit source : `97b2ae608dc9c6496e0140ad26aa3a7549f96328`
- Version de Graphify : `0.9.112` (paquet PyPI `graphifyy`, dépôt [Graphify-Labs/graphify](https://github.com/Graphify-Labs/graphify), licence Apache-2.0)
- Commandes : `graphify extract . --code-only --out <répertoire> --exclude build/ --exclude .gradle/ --exclude docs/graphify/` puis `graphify cluster-only <répertoire> --no-label`
- Exclusions : `build/`, `.gradle/`, `docs/graphify/` (en plus de ce que `.gitignore` ignore déjà)
- Aucun LLM, aucune clé d'API. Coût de jetons relevé par l'outil : 0.

L'extracteur Kotlin de cette version signale des erreurs de syntaxe sur une partie des fichiers `.kt` : ces fichiers restent partiellement représentés. La carte sert à naviguer (appels, dépendances, communautés), elle ne garantit pas que chaque symbole du dépôt y figure.

Les chemins enregistrés dans `graph.json`, `graph.html` et `GRAPH_REPORT.md` sont relatifs à la racine du dépôt.
