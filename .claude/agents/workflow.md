    /tmp/claude-0/-home-claude/1b405532-b626-53b1-88e2-c206b29e69e2/scratchpad/agents/.claude/workflow.md
Contenu

    # Workflow des agents — D&D Clean
    Ce fichier dit à Claude Code **quel agent appeler, dans quel ordre, et quand s'arrêter**.
    Les agents sont définis dans `.claude/agents/`. Toi (la session principale) tu es
    l'**orchestrateur** : tu délègues, tu transmets le contexte, tu vérifies et tu rends compte.
    ## Les agents
    | Agent | Rôle | Modifie le code |
    |---|---|---|
    | `debugger` | Trouve la cause racine d'une erreur | Non |
    | `bug-fixer` | Corrige au minimum, prouvé par un test | Oui |
    | `test-writer` | Écrit les tests manquants | Tests uniquement |
    | `refactorer` | Restructure sans changer le comportement | Oui |
    | `code-reviewer` | Relit le diff avant commit | Non |
    ## Règles générales
    1. **Chaque agent repart de zéro.** Transmets-lui tout ce dont il a besoin : la demande
       de l'utilisateur, les fichiers concernés, le résultat de l'agent précédent (cause,
       fichiers modifiés, tests ajoutés).
    2. **Un seul agent modifie le code à la fois.** Jamais deux agents qui écrivent en parallèle.
    3. **Les tests sont l'arbitre.** Une étape qui modifie du code n'est terminée que si
       `./mvnw test` (back) ou `npm test` (front) passe.
    4. **Boucles limitées.** Au plus **2 allers-retours** entre `code-reviewer` et l'agent qui
       corrige. Au-delà, arrête-toi et demande à l'utilisateur.
    5. **Stop et demande** si : la cause d'un bug reste incertaine, un changement touche une
       API publique (routes, JSON), le schéma de base, la sécurité, ou une migration Flyway
       déjà appliquée.
    6. **Jamais de commit ni de push** sans demande explicite de l'utilisateur.
    7. Respecte toujours `CLAUDE.md` (architecture, SOLID/DRY, DTO, modules).
    ## Workflow 1 — Corriger un bug
    Déclencheur : stack trace, test qui échoue, « ça ne marche pas ».
    ```
    debugger → bug-fixer → code-reviewer → [bug-fixer si bloquant] → rapport
    ```
    1. `debugger` : reçoit l'erreur complète et la façon de la reproduire.
