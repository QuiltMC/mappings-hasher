package org.quiltmc.mappings_hasher;

import java.util.Set;
import java.util.jar.JarFile;

import org.cadixdev.bombe.type.signature.FieldSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.quiltmc.mappings_hasher.asm.ClassInfo;
import org.quiltmc.mappings_hasher.asm.ClassResolver;
import org.quiltmc.mappings_hasher.asm.FieldInfo;
import org.quiltmc.mappings_hasher.asm.MethodInfo;

public class MappingsHasher {
    private final MappingSet original;
    private final String defaultPackage;
    private final ClassResolver classResolver = new ClassResolver();

    public MappingsHasher(MappingSet original, String defaultPackage) {
        this.original = original;
        this.defaultPackage = defaultPackage;
    }

    public void addLibrary(JarFile jar) {
        classResolver.addLibrary(jar);
    }

    public MappingSet generate(JarFile jar) {
        // Extract class information (for method overrides mostly)
        Set<ClassInfo> classes = classResolver.extractClassInfo(jar);

        // The class generating hashed names from class information and the original mappings
        HashedNameProvider nameProvider = new HashedNameProvider(classes, original, defaultPackage);

        // Apply "Don't Obfuscate" annotations
        for (ClassInfo classInfo : classes) {
            ClassMapping<?, ?> classMapping = original.getClassMapping(classInfo.name()).orElseThrow(() -> new RuntimeException("Missing mapping for class " + classInfo.name()));

            if (classInfo.name().equals(classMapping.getFullDeobfuscatedName())) {
                classInfo.dontObfuscate();
            }

            for (MethodInfo methodInfo : classInfo.methods()) {
                MethodMapping methodMapping = classMapping.getMethodMapping(methodInfo.name(), methodInfo.descriptor()).orElseThrow(() -> new RuntimeException("Missing mapping for method " + methodInfo.name()));

                if (methodInfo.name().equals(methodMapping.getDeobfuscatedName())) {
                    methodInfo.dontObfuscate();
                }
            }

            for (FieldInfo fieldInfo : classInfo.fields()) {
                FieldMapping fieldMapping = classMapping.getFieldMapping(fieldInfo.name()).orElseThrow(() -> new RuntimeException("Missing mapping for field " + fieldInfo.name()));
                if (fieldInfo.name().equals(fieldMapping.getDeobfuscatedName())) {
                    fieldInfo.dontObfuscate();
                }
            }
        }

        MappingSet hashed = MappingSet.create();
        for (ClassInfo classInfo : classes) {
            // Create class mapping
            ClassMapping<?, ?> classHashed = hashed.getOrCreateClassMapping(classInfo.name());
            classHashed.setDeobfuscatedName(nameProvider.getClassName(classInfo));

            for (MethodInfo methodInfo : classInfo.methods()) {
                // Create method mapping
                MethodMapping methodHashed = classHashed.createMethodMapping(methodInfo.name(), methodInfo.descriptor());
                methodHashed.setDeobfuscatedName(nameProvider.getMethodName(methodInfo));
            }

            for (FieldInfo fieldInfo : classInfo.fields()) {
                // Create field mapping
                FieldMapping fieldHashed = classHashed.createFieldMapping(FieldSignature.of(fieldInfo.name(), fieldInfo.descriptor()));
                fieldHashed.setDeobfuscatedName(nameProvider.getFieldName(fieldInfo));
            }
        }

        return hashed;
    }
}
