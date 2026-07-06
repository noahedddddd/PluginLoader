package fr.loader.api;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Classe de base que chaque plugin du depot doit etendre.
 *
 * Chaque dossier du depot GitHub = un plugin = une classe qui etend LoadedPlugin.
 * Ecris simplement onEnable() (et onDisable() si besoin).
 */
public abstract class LoadedPlugin {

    private JavaPlugin host;
    private String name;
    private File dataFolder;
    private Logger logger;

    private final List<Listener> listeners = new ArrayList<>();
    private final List<Command> commands = new ArrayList<>();

    /** Appele par le loader avant onEnable(). Ne pas appeler soi-meme. */
    public final void init(JavaPlugin host, String name, File dataFolder) {
        this.host = host;
        this.name = name;
        this.dataFolder = dataFolder;
        this.logger = Logger.getLogger("Plugin/" + name);
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    /** Appele quand le plugin est charge. A implementer. */
    public abstract void onEnable();

    /** Appele quand le plugin est decharge (ex: /plreload). Optionnel. */
    public void onDisable() {
    }

    // ---------- Utilitaires accessibles depuis ton plugin ----------

    public final String getName() {
        return name;
    }

    public JavaPlugin getHost() {
        return host;
    }

    public Server getServer() {
        return host.getServer();
    }

    public Logger getLogger() {
        return logger;
    }

    public File getDataFolder() {
        return dataFolder;
    }

    /** Enregistre un listener d'evenements. */
    protected void registerListener(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, host);
        listeners.add(listener);
    }

    /** Enregistre une commande dynamiquement (pas besoin de plugin.yml). */
    protected void registerCommand(String cmdName, CommandExecutor executor) {
        CommandMap map = getCommandMap();
        if (map == null) {
            logger.warning("CommandMap introuvable : commande '" + cmdName + "' non enregistree.");
            return;
        }
        Command command = new Command(cmdName) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                return executor.onCommand(sender, this, label, args);
            }
        };
        map.register(name.toLowerCase(), command);
        commands.add(command);
    }

    // ---------- Interne : nettoyage au dechargement ----------

    public final void disableInternal() {
        try {
            onDisable();
        } catch (Throwable t) {
            logger.warning("Erreur pendant onDisable : " + t);
        }
        for (Listener l : listeners) {
            HandlerList.unregisterAll(l);
        }
        listeners.clear();

        CommandMap map = getCommandMap();
        if (map != null) {
            for (Command c : commands) {
                try {
                    c.unregister(map);
                } catch (Throwable ignored) {
                }
            }
        }
        commands.clear();
    }

    private CommandMap getCommandMap() {
        try {
            Server server = Bukkit.getServer();
            Method m = server.getClass().getMethod("getCommandMap");
            return (CommandMap) m.invoke(server);
        } catch (Throwable t) {
            return null;
        }
    }
}
