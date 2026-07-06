package fr.loader;

import fr.loader.api.LoadedPlugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Orchestre le cycle : recuperation -> compilation -> instanciation -> activation. */
public class ModuleManager {

    private final JavaPlugin host;
    private final Logger logger;
    private final File workDir;   // extraction + compilation
    private final File dataRoot;  // dossiers de donnees par plugin
    private final List<LoadedModule> modules = new ArrayList<>();

    public ModuleManager(JavaPlugin host, File baseDir) {
        this.host = host;
        this.logger = host.getLogger();
        this.workDir = new File(baseDir, "work");
        this.dataRoot = new File(baseDir, "plugins");
        this.dataRoot.mkdirs();
    }

    public static class Result {
        public int loaded;
        public int failed;
        public final List<String> failedNames = new ArrayList<>();
    }

    /** Desactive tout ce qui est charge. A appeler sur le thread principal. */
    public void disableAll() {
        for (LoadedModule m : modules) {
            try {
                m.instance.disableInternal();
            } catch (Throwable t) {
                logger.warning("Erreur en desactivant " + m.name + " : " + t);
            }
            try {
                m.classLoader.close();
            } catch (Exception ignored) {
            }
        }
        modules.clear();
    }

    /**
     * Recupere le depot, compile et instancie chaque plugin (PAS encore active).
     * Travail lourd : a lancer en asynchrone.
     */
    public List<LoadedModule> prepare(RepoManager repo, List<File> extraCp, Result result) {
        List<LoadedModule> prepared = new ArrayList<>();

        List<File> pluginDirs;
        try {
            pluginDirs = repo.fetch(workDir);
        } catch (Exception e) {
            logger.severe("Impossible de recuperer le depot : " + e.getMessage());
            return prepared;
        }

        PluginCompiler compiler = new PluginCompiler(logger, extraCp);
        File classesRoot = new File(workDir, "classes");
        classesRoot.mkdirs();

        for (File dir : pluginDirs) {
            String name = dir.getName();
            File out = new File(classesRoot, name);
            try {
                boolean ok = compiler.compile(dir, out);
                if (!ok) {
                    result.failed++;
                    result.failedNames.add(name);
                    logger.warning("X Compilation echouee : " + name);
                    continue;
                }
                LoadedModule mod = instantiate(name, dir, out);
                if (mod == null) {
                    result.failed++;
                    result.failedNames.add(name);
                    continue;
                }
                prepared.add(mod);
            } catch (Throwable t) {
                result.failed++;
                result.failedNames.add(name);
                logger.warning("X Erreur avec " + name + " : " + t);
            }
        }
        return prepared;
    }

    private LoadedModule instantiate(String name, File sourceDir, File classesDir) throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(classesDir.toURI().toURL());

        // libs runtime propres au plugin
        File libs = new File(sourceDir, "libs");
        if (libs.isDirectory()) {
            File[] jars = libs.listFiles((d, n) -> n.endsWith(".jar"));
            if (jars != null) {
                for (File j : jars) {
                    urls.add(j.toURI().toURL());
                }
            }
        }

        URLClassLoader cl = new URLClassLoader(
                urls.toArray(new URL[0]), host.getClass().getClassLoader());

        String mainClass = findMainClass(classesDir, cl);
        if (mainClass == null) {
            logger.warning("X Aucune classe etendant LoadedPlugin dans " + name);
            cl.close();
            return null;
        }

        Class<?> c = Class.forName(mainClass, true, cl);
        LoadedPlugin instance = (LoadedPlugin) c.getDeclaredConstructor().newInstance();
        File dataFolder = new File(dataRoot, name);
        instance.init(host, name, dataFolder);
        return new LoadedModule(name, instance, cl);
    }

    private String findMainClass(File classesDir, ClassLoader cl) {
        List<String> classNames = new ArrayList<>();
        collectClasses(classesDir, classesDir, classNames);
        for (String cn : classNames) {
            try {
                Class<?> c = Class.forName(cn, false, cl);
                if (LoadedPlugin.class.isAssignableFrom(c)
                        && !Modifier.isAbstract(c.getModifiers())) {
                    return cn;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private void collectClasses(File root, File dir, List<String> out) {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File k : kids) {
            if (k.isDirectory()) {
                collectClasses(root, k, out);
            } else if (k.getName().endsWith(".class") && !k.getName().contains("$")) {
                String rel = root.toPath().relativize(k.toPath()).toString();
                rel = rel.replace(File.separatorChar, '.');
                rel = rel.substring(0, rel.length() - ".class".length());
                out.add(rel);
            }
        }
    }

    /** Active les plugins prepares. A appeler sur le thread principal. */
    public void enable(List<LoadedModule> prepared, Result result) {
        for (LoadedModule m : prepared) {
            try {
                m.instance.onEnable();
                modules.add(m);
                result.loaded++;
                logger.info("V " + m.name + " charge");
            } catch (Throwable t) {
                result.failed++;
                result.failedNames.add(m.name);
                logger.warning("X onEnable a echoue pour " + m.name + " : " + t);
                try {
                    m.classLoader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public int getLoadedCount() {
        return modules.size();
    }
}
