package org.chuck.chugin;

import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckVM;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads dynamic Chugins (.jar files) from the chugins/ directory.
 */
public class ChuginLoader {
    private static final Map<String, Class<? extends ChuckObject>> chuginRegistry = new HashMap<>();

    public static void loadChugins(String directory) {
        File dir = new File(directory);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".jar"));
        if (files == null) return;

        for (File file : files) {
            try {
                loadChugin(file);
            } catch (Exception e) {
                System.err.println("Failed to load chugin: " + file.getName() + " - " + e.getMessage());
            }
        }
    }

    private static void loadChugin(File jarFile) throws Exception {
        URL[] urls = { jarFile.toURI().toURL() };
        @SuppressWarnings("resource")
        URLClassLoader classLoader = new URLClassLoader(urls, ChuginLoader.class.getClassLoader());

        try (JarFile jar = new JarFile(jarFile)) {
            java.util.Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class") && !entry.isDirectory()) {
                    String className = entry.getName().replace('/', '.').substring(0, entry.getName().length() - 6);
                    try {
                        Class<?> cls = classLoader.loadClass(className);
                        if (ChuckObject.class.isAssignableFrom(cls) && !cls.isInterface() && (cls.getModifiers() & java.lang.reflect.Modifier.ABSTRACT) == 0) {
                            @SuppressWarnings("unchecked")
                            Class<? extends ChuckObject> chuckCls = (Class<? extends ChuckObject>) cls;
                            chuginRegistry.put(cls.getSimpleName(), chuckCls);
                            // System.out.println("Loaded Chugin type: " + cls.getSimpleName());
                        }
                    } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                    }
                }
            }
        }
    }

    public static ChuckObject instantiateChugin(String type, float sampleRate, ChuckVM vm) {
        Class<? extends ChuckObject> cls = chuginRegistry.get(type);
        if (cls == null) return null;

        try {
            // Try constructors in priority order:
            // 1. (float sampleRate, ChuckVM vm)
            // 2. (float sampleRate)
            // 3. (ChuckVM vm)
            // 4. ()
            
            try {
                Constructor<? extends ChuckObject> c = cls.getConstructor(float.class, ChuckVM.class);
                return c.newInstance(sampleRate, vm);
            } catch (NoSuchMethodException e1) {
                try {
                    Constructor<? extends ChuckObject> c = cls.getConstructor(float.class);
                    return c.newInstance(sampleRate);
                } catch (NoSuchMethodException e2) {
                    try {
                        Constructor<? extends ChuckObject> c = cls.getConstructor(ChuckVM.class);
                        return c.newInstance(vm);
                    } catch (NoSuchMethodException e3) {
                        return cls.getDeclaredConstructor().newInstance();
                    }
                }
            }
        } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
            System.err.println("Failed to instantiate chugin type " + type + ": " + e.getMessage());
            return null;
        }
    }

    public static boolean hasType(String type) {
        return chuginRegistry.containsKey(type);
    }
}
