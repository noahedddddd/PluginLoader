package fr.loader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Telecharge le depot GitHub (zip), l'extrait, et liste les dossiers = plugins. */
public class RepoManager {

    private final Logger logger;
    private final String repository; // owner/repo
    private final String branch;
    private final String token;

    public RepoManager(Logger logger, String repository, String branch, String token) {
        this.logger = logger;
        this.repository = repository;
        this.branch = branch;
        this.token = token;
    }

    /**
     * Telecharge le zip du depot, l'extrait dans workDir (vide au prealable)
     * et retourne la liste des dossiers de premier niveau (un par plugin).
     */
    public List<File> fetch(File workDir) throws IOException, InterruptedException {
        deleteRecursively(workDir);
        workDir.mkdirs();

        String url;
        HttpRequest.Builder req;
        if (token != null && !token.isEmpty()) {
            url = "https://api.github.com/repos/" + repository + "/zipball/" + branch;
            req = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "token " + token)
                    .header("Accept", "application/vnd.github+json");
        } else {
            url = "https://codeload.github.com/" + repository + "/zip/refs/heads/" + branch;
            req = HttpRequest.newBuilder(URI.create(url));
        }
        req.header("User-Agent", "PluginLoader");

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        File zipFile = new File(workDir, "repo.zip");
        HttpResponse<Path> resp = client.send(req.build(),
                HttpResponse.BodyHandlers.ofFile(zipFile.toPath()));

        if (resp.statusCode() != 200) {
            throw new IOException("Telechargement echoue (HTTP " + resp.statusCode()
                    + ") depuis " + url + " -- verifie repository/branch/token.");
        }

        File extractDir = new File(workDir, "extracted");
        extractDir.mkdirs();
        unzip(zipFile, extractDir);
        zipFile.delete();

        // GitHub encapsule tout dans un dossier unique "repo-branch/"
        File[] roots = extractDir.listFiles(File::isDirectory);
        File root = (roots != null && roots.length == 1) ? roots[0] : extractDir;

        List<File> plugins = new ArrayList<>();
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs != null) {
            Arrays.sort(dirs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File d : dirs) {
                if (d.getName().startsWith(".")) {
                    continue; // ignore .github, etc.
                }
                plugins.add(d);
            }
        }
        return plugins;
    }

    private void unzip(File zip, File dest) throws IOException {
        byte[] buffer = new byte[8192];
        Path destPath = dest.toPath().normalize();
        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(dest, entry.getName());
                // Protection zip-slip
                if (!out.toPath().normalize().startsWith(destPath)) {
                    throw new IOException("Entree zip invalide : " + entry.getName());
                }
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    public static void deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteRecursively(k);
            }
        }
        f.delete();
    }
}
