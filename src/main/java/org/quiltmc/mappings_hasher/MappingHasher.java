package org.quiltmc.mappings_hasher;

import org.cadixdev.bombe.type.signature.FieldSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.quiltmc.mappings_hasher.asm.ClassInfo;
import org.quiltmc.mappings_hasher.asm.ClassResolver;
import org.quiltmc.mappings_hasher.asm.FieldInfo;
import org.quiltmc.mappings_hasher.asm.MethodInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;

public class MappingHasher {
    private final MappingSet original;
    private final String defaultPackage;
    private final ClassResolver classResolver = new ClassResolver();
    private final Set<String> dontObfuscateAnnotations = new HashSet<>();
    private final Set<String> hashedDontObfuscateAnnotations = new HashSet<>();

    public MappingHasher(MappingSet original, String defaultPackage) {
        this.original = original;
        this.defaultPackage = defaultPackage;
    }

    public void addLibrary(JarFile jar) {
        classResolver.addLibrary(jar);
    }

    public void addDontObfuscateAnnotation(String annotation, boolean hashed) {
        if (hashed) {
            this.hashedDontObfuscateAnnotations.add(annotation);
        }
        else {
            dontObfuscateAnnotations.add(annotation);
        }
    }

    public MappingSet generate(JarFile jar) {
        // Extract class information (for method overrides mostly)
        Set<ClassInfo> classes = classResolver.extractClassInfo(jar);

        // The class generating hashed names from class information and the original mappings
        HashedNameProvider nameProvider = new HashedNameProvider(classes, original, defaultPackage);

        // Resolve obfuscated annotations
        Set<String> dontObfuscateAnnotations = new HashSet<>(this.dontObfuscateAnnotations);
        for (ClassInfo classInfo : classes) {
            if (this.hashedDontObfuscateAnnotations.contains(nameProvider.getClassName(classInfo))) {
                dontObfuscateAnnotations.add(classInfo.name());
            }
        }
        // Apply "Don't Obfuscate" annotations
        for (ClassInfo classInfo : classes) {
            if (classInfo.annotations().stream().anyMatch(dontObfuscateAnnotations::contains)) {
                classInfo.dontObfuscate();
                classInfo.methods().forEach(MethodInfo::dontObfuscate);
                classInfo.fields().forEach(FieldInfo::dontObfuscate);
            }
            for (MethodInfo methodInfo : classInfo.methods()) {
                if (methodInfo.annotations().stream().anyMatch(dontObfuscateAnnotations::contains)) {
                    methodInfo.dontObfuscate();
                    classInfo.dontObfuscate();
                }
            }
            for (FieldInfo fieldInfo : classInfo.fields()) {
                if (fieldInfo.annotations().stream().anyMatch(dontObfuscateAnnotations::contains)) {
                    fieldInfo.dontObfuscate();
                    classInfo.dontObfuscate();
                }
            }
        }

        MappingSet hashed = MappingSet.create();
        for (ClassInfo classInfo : classes) {
            // Create class Mapping
            ClassMapping<?, ?> classHashed = hashed.getOrCreateClassMapping(classInfo.name());
            classHashed.setDeobfuscatedName(nameProvider.getClassName(classInfo));

            for (FieldInfo fieldInfo : classInfo.fields()) {
                // Create field mapping
                FieldMapping fieldHashed = classHashed.createFieldMapping(FieldSignature.of(fieldInfo.name(), fieldInfo.descriptor()));
                fieldHashed.setDeobfuscatedName(nameProvider.getFieldName(fieldInfo));
            }

            for (MethodInfo methodInfo : classInfo.methods()) {
                // Create method mapping
                MethodMapping methodHashed = classHashed.createMethodMapping(methodInfo.name(), methodInfo.descriptor());
                methodHashed.setDeobfuscatedName(nameProvider.getMethodName(methodInfo));
            }
        }

        return hashed;
    }
}
