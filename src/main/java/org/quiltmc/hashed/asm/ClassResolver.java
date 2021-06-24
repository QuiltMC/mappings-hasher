package org.quiltmc.hashed.asm;

import org.objectweb.asm.ClassReader;
import org.quiltmc.hashed.Main;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClassResolver {
    private Map<String, ClassReader> classToReader = new HashMap<>();
    private Map<String, Boolean> classToObfuscated = new HashMap<>();
    private Map<String, ClassInfo> classInfoCache = new HashMap<>();

    private final Set<String> dontObfuscateAnnotations;
    private final Set<String> dontObfuscateClassAnnotations;

    public ClassResolver(Set<String> dontObfuscateAnnotations, Set<String> dontObfuscateClassAnnotations) {
        this.dontObfuscateAnnotations = dontObfuscateAnnotations;
        this.dontObfuscateClassAnnotations = dontObfuscateClassAnnotations;
    }

    public void addJar(File file, boolean obfuscated) throws IOException {
        JarFile jar = new JarFile(file);
        jar.stream().forEach(entry -> {
            if (entry.getName().endsWith(".class")) {
                String className = entry.getName().substring(0, entry.getName().lastIndexOf('.'));
                try {
                    classToReader.put(className, new ClassReader(jar.getInputStream(entry)));
                    classToObfuscated.put(className, obfuscated);
                }
                catch (IOException exception) {
                    System.err.println("Error reading jar...");
                }
            }
        });
    }

    public void foreachClassInfoInJar(File file, Consumer<ClassInfo> consumer) throws IOException {
        JarFile jar = new JarFile(file);
        jar.stream().forEach(jarEntry -> {
            if (jarEntry.getName().endsWith(".class")) {
                String className = jarEntry.getName().substring(0, jarEntry.getName().lastIndexOf('.'));
                ClassInfo info = getClassInfo(className);
                consumer.accept(info);
            }
        });
    }

    public ClassInfo getClassInfo(String name) {
        if (classInfoCache.containsKey(name)) {
            return classInfoCache.get(name);
        }

        ClassReader reader = classToReader.get(name);
        if (reader == null) {
            try {
                reader = new ClassReader(name);
            }
            catch (IOException exception) {
                throw new RuntimeException("Couldn't find class " + name);
            }
        }

        boolean obfuscated = classToObfuscated.getOrDefault(name, false);
        ClassInfo.ClassVisitor visitor = new ClassInfo.ClassVisitor(this, obfuscated, dontObfuscateAnnotations, dontObfuscateClassAnnotations);
        reader.accept(visitor,ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        classInfoCache.put(name, visitor.getClassInfo());
        return visitor.getClassInfo();
    }
}
