package dev.hypercore.plugin;

import java.net.URL;
import java.net.URLClassLoader;

final class ExternalPluginClassLoader extends URLClassLoader {
    private static final String[] PARENT_FIRST_PREFIXES = {
        "java.",
        "javax.",
        "jdk.",
        "sun.",
        "dev.hypercore.",
        "net.minecraft.",
        "net.minecraftforge.",
        "net.fabricmc.",
        "com.mojang.",
        "org.slf4j.",
        "org.apache.logging.log4j.",
        "com.google.gson."
    };

    ExternalPluginClassLoader(URL pluginJar, ClassLoader parent) {
        super(new URL[] { pluginJar }, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (isParentFirst(name)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try {
                    loaded = findClass(name);
                } catch (ClassNotFoundException ignored) {
                    loaded = super.loadClass(name, false);
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    private static boolean isParentFirst(String name) {
        for (String prefix : PARENT_FIRST_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
