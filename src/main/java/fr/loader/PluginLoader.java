package fr.loader;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Plugin principal.
 * - Au demarrage : charge tous les plugins du depot.
 * - /plreload : recontrole le depot, recompile et recharge tout.
 */
public class PluginLoader extends JavaPlugin {

    private ModuleManager manager;
    private volatile boolean busy = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new ModuleManager(this, getDataFolder());
        getLogger().info("Chargement initial des plugins depuis le depot...");
        reload(Bukkit.getConsoleSender());
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.disableAll();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("plreload")) {
            return false;
        }
        if (busy) {
            sender.sendMessage(Component.text("Un rechargement est deja en cours...")
                    .color(NamedTextColor.YELLOW));
            return true;
        }
        sender.sendMessage(Component.text("Rechargement depuis le depot...")
                .color(NamedTextColor.GRAY));
        reload(sender);
        return true;
    }

    private void reload(CommandSender sender) {
        busy = true;

        String repository = getConfig().getString("repository", "");
        String branch = getConfig().getString("branch", "main");
        String token = getConfig().getString("token", "");

        if (repository == null || repository.isEmpty() || !repository.contains("/")) {
            sender.sendMessage(Component.text("Configure 'repository: proprietaire/depot' dans config.yml")
                    .color(NamedTextColor.RED));
            busy = false;
            return;
        }

        final List<File> extraCp = collectLibraries();
        final RepoManager repo = new RepoManager(getLogger(), repository, branch, token);

        // On desactive d'abord l'existant (thread principal), puis on
        // telecharge + compile en asynchrone, puis on active (thread principal).
        manager.disableAll();

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            final ModuleManager.Result result = new ModuleManager.Result();
            final List<LoadedModule> prepared = manager.prepare(repo, extraCp, result);

            Bukkit.getScheduler().runTask(this, () -> {
                manager.enable(prepared, result);
                sendReport(sender, result);
                busy = false;
            });
        });
    }

    private void sendReport(CommandSender sender, ModuleManager.Result result) {
        Component msg = Component.text("\u2705 " + result.loaded + " plugins charge !")
                .color(NamedTextColor.GREEN);
        if (result.failed > 0) {
            msg = msg.append(Component.text("  (" + result.failed + " en erreur : "
                            + String.join(", ", result.failedNames) + ")")
                    .color(NamedTextColor.RED));
        }
        sender.sendMessage(msg);

        getLogger().info(result.loaded + " plugins charges"
                + (result.failed > 0 ? ", " + result.failed + " en erreur" : ""));
    }

    private List<File> collectLibraries() {
        List<File> libs = new ArrayList<>();
        File libDir = new File(getDataFolder(), "libraries");
        if (libDir.isDirectory()) {
            File[] jars = libDir.listFiles((d, n) -> n.endsWith(".jar"));
            if (jars != null) {
                for (File j : jars) {
                    libs.add(j);
                }
            }
        }
        return libs;
    }
}
