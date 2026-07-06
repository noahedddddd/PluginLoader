# PluginLoader (Paper 1.21.1)

Charge dynamiquement des plugins depuis un dépôt GitHub, où **1 dossier = 1 plugin**.
Le code source (non compilé) reste dans le dépôt ; le serveur le compile et le charge tout seul.
La commande `/plreload` re-contrôle le dépôt, recompile et recharge tout à chaud.

---

## 1. Compiler PluginLoader

Depuis le dossier du projet (avec Maven installé) :

```bash
mvn clean package
```

Le jar final : `target/PluginLoader.jar`.

## 2. Installer

1. Mets `PluginLoader.jar` dans le dossier `plugins/` de ton serveur Paper 1.21.1.
2. Démarre le serveur une fois (ça génère `plugins/PluginLoader/config.yml`), puis arrête-le.
3. Édite `plugins/PluginLoader/config.yml` :

```yaml
repository: "TonPseudo/MesPlugins"   # proprietaire/depot
branch: "main"
token: ""                            # seulement si le depot est prive
```

4. Relance le serveur. Les plugins du dépôt se chargent automatiquement.

## 3. Structure attendue du dépôt

```
MesPlugins/                <-- ton dépôt GitHub
├── HelloPlugin/           <-- 1 dossier = 1 plugin
│   └── java/fr/example/HelloPlugin.java
├── Plugin2/
│   └── java/fr/truc/Main.java
└── Plugin3/
    ├── java/fr/machin/Core.java
    └── libs/              <-- (optionnel) jars externes utilisés par CE plugin
        └── ma-lib.jar
```

- Chaque dossier de premier niveau devient un plugin.
- L'emplacement des `.java` dans le dossier n'a pas d'importance (ici `java/fr/...`), ils sont tous compilés.
- Un dossier `libs/` (optionnel) dans un plugin ajoute des jars à sa compilation **et** à son exécution.
- Les dossiers commençant par `.` (ex : `.github`) sont ignorés.

## 4. Écrire un plugin

Une seule règle : la classe principale doit **étendre `fr.loader.api.LoadedPlugin`**.
Pas besoin de `plugin.yml` : la classe principale est trouvée automatiquement.

```java
package fr.example;

import fr.loader.api.LoadedPlugin;

public class MonPlugin extends LoadedPlugin {
    @Override
    public void onEnable() {
        getLogger().info("Salut !");
        registerListener(new MonListener());          // événements
        registerCommand("test", (s, c, l, a) -> {      // commande dynamique
            s.sendMessage("ça marche !");
            return true;
        });
    }

    @Override
    public void onDisable() {
        // nettoyage optionnel (les listeners/commandes sont déjà retirés)
    }
}
```

Méthodes utiles héritées : `getServer()`, `getLogger()`, `getDataFolder()`,
`registerListener(...)`, `registerCommand(...)`, `getHost()`.

Voir `example-repo/HelloPlugin/` pour un exemple complet à copier dans ton dépôt.

## 5. Recharger

En jeu ou en console :

```
/plreload
```

Résultat en vert :

```
✅ 12 plugins charge !
```

S'il y a des erreurs (compilation, etc.), le nom des plugins fautifs s'affiche en rouge
et le décompte vert ne compte que les plugins **valides**.

---

## Notes importantes

- **Java** : le serveur compile le code à l'exécution. Si le serveur tourne sur un JDK, le
  compilateur `javac` est utilisé. Sinon, PluginLoader utilise **ECJ** (le compilateur Eclipse,
  embarqué dans le jar) — donc ça marche aussi sur un simple JRE.
- **API Paper introuvable à la compilation ?** Si tu vois des erreurs du type « package
  org.bukkit does not exist », dépose le jar de l'API dans
  `plugins/PluginLoader/libraries/` (ex : `paper-api-1.21.1.jar`). Ces jars sont ajoutés au
  classpath de compilation de tous les plugins.
- **Commandes dynamiques** : elles fonctionnent immédiatement ; l'auto-complétion côté client
  peut nécessiter une reconnexion.
- **Rechargement** : à chaque `/plreload`, les anciens plugins sont proprement désactivés
  (listeners et commandes retirés, classloaders fermés) avant d'être remplacés.
- Le symbole `✅` peut s'afficher comme un carré selon la police du client. Change-le dans
  `PluginLoader.java` (méthode `sendReport`) si tu préfères un simple `V` ou `✔`.
