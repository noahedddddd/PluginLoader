package fr.loader;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/** Compile les sources d'un dossier de plugin en .class. */
public class PluginCompiler {

    private final Logger logger;
    private final List<File> extraClasspath;

    public PluginCompiler(Logger logger, List<File> extraClasspath) {
        this.logger = logger;
        this.extraClasspath = extraClasspath;
    }

    /** Compile tous les .java sous sourceDir vers outputDir. true si succes. */
    public boolean compile(File sourceDir, File outputDir) throws IOException {
        JavaCompiler compiler = getCompiler();
        if (compiler == null) {
            logger.severe("Aucun compilateur Java disponible (ni JDK, ni ECJ).");
            return false;
        }

        List<File> javaFiles = new ArrayList<>();
        collectJava(sourceDir, javaFiles);
        if (javaFiles.isEmpty()) {
            logger.warning("Aucun fichier .java dans " + sourceDir.getName());
            return false;
        }

        RepoManager.deleteRecursively(outputDir);
        outputDir.mkdirs();

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fm =
                compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        try {
            fm.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputDir));
            fm.setLocation(StandardLocation.CLASS_PATH, buildClasspath(sourceDir));

            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromFiles(javaFiles);
            List<String> options = Arrays.asList("-encoding", "UTF-8", "-source", "21", "-target", "21");

            JavaCompiler.CompilationTask task =
                    compiler.getTask(null, fm, diagnostics, options, null, units);
            boolean ok = task.call();

            if (!ok) {
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    if (d.getKind() == Diagnostic.Kind.ERROR) {
                        logger.warning("    " + shortName(d) + ":" + d.getLineNumber()
                                + " -> " + d.getMessage(null));
                    }
                }
            }
            return ok;
        } finally {
            fm.close();
        }
    }

    /** javac du JDK si dispo, sinon ECJ (embarque). */
    private JavaCompiler getCompiler() {
        JavaCompiler c = ToolProvider.getSystemJavaCompiler();
        if (c != null) {
            return c;
        }
        try {
            return (JavaCompiler) Class
                    .forName("org.eclipse.jdt.internal.compiler.tool.EclipseCompiler")
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (Throwable t) {
            return null;
        }
    }

    private String shortName(Diagnostic<? extends JavaFileObject> d) {
        JavaFileObject src = d.getSource();
        if (src == null) {
            return "?";
        }
        String n = src.getName().replace('\\', '/');
        int i = n.lastIndexOf('/');
        return i >= 0 ? n.substring(i + 1) : n;
    }

    private void collectJava(File dir, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File k : kids) {
            if (k.isDirectory()) {
                collectJava(k, out);
            } else if (k.getName().endsWith(".java")) {
                out.add(k);
            }
        }
    }

    /** Construit le classpath de compilation (API Paper + jar du loader + libs). */
    private List<File> buildClasspath(File sourceDir) {
        LinkedHashSet<File> cp = new LinkedHashSet<>();

        // 1. classpath du process
        String jcp = System.getProperty("java.class.path");
        if (jcp != null) {
            for (String p : jcp.split(File.pathSeparator)) {
                if (!p.isEmpty()) {
                    cp.add(new File(p));
                }
            }
        }
        // 2. le jar du loader (contient fr.loader.api.LoadedPlugin)
        addCodeSource(cp, getClass());
        // 3. le jar de l'API Bukkit/Paper
        try {
            addCodeSource(cp, Class.forName("org.bukkit.Bukkit"));
        } catch (Throwable ignored) {
        }
        // 4. URLs des classloaders parents
        addClassLoaderUrls(cp, getClass().getClassLoader());
        // 5. libs propres au plugin : <dossier>/libs/*.jar
        File libs = new File(sourceDir, "libs");
        if (libs.isDirectory()) {
            File[] jars = libs.listFiles((d, n) -> n.endsWith(".jar"));
            if (jars != null) {
                cp.addAll(Arrays.asList(jars));
            }
        }
        // 6. libs globales : plugins/PluginLoader/libraries/*.jar
        if (extraClasspath != null) {
            cp.addAll(extraClasspath);
        }

        List<File> result = new ArrayList<>();
        for (File f : cp) {
            if (f.exists()) {
                result.add(f);
            }
        }
        return result;
    }

    private void addCodeSource(Set<File> cp, Class<?> c) {
        try {
            URL loc = c.getProtectionDomain().getCodeSource().getLocation();
            if (loc != null) {
                cp.add(new File(loc.toURI()));
            }
        } catch (Exception ignored) {
        }
    }

    private void addClassLoaderUrls(Set<File> cp, ClassLoader cl) {
        while (cl != null) {
            if (cl instanceof URLClassLoader) {
                for (URL u : ((URLClassLoader) cl).getURLs()) {
                    try {
                        cp.add(new File(u.toURI()));
                    } catch (Exception ignored) {
                    }
                }
            }
            cl = cl.getParent();
        }
    }
}
