package fr.loader;

import fr.loader.api.LoadedPlugin;

import java.net.URLClassLoader;

/** Represente un plugin compile + instancie, pret a etre active. */
public class LoadedModule {

    final String name;
    final LoadedPlugin instance;
    final URLClassLoader classLoader;

    LoadedModule(String name, LoadedPlugin instance, URLClassLoader classLoader) {
        this.name = name;
        this.instance = instance;
        this.classLoader = classLoader;
    }
}
