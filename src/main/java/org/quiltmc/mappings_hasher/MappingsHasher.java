package org.quiltmc.mappings_hasher;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
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

    public MappingSet generate(JarFile jar, Predicate<ClassInfo> classFilter) {
        // Extract class information (for method overrides mostly)
        Set<ClassInfo> classes = classResolver.extractClassInfo(jar, classFilter);

        // The class generating hashed names from class information and the original mappings
        HashedNameProvider nameProvider = new HashedNameProvider(classes, original, defaultPackage);

        // Create the mappings
        MappingSet hashed = MappingSet.create();
        for (ClassInfo classInfo : classes) {
            // Create class mapping
            ClassMapping<?, ?> classHashed = hashed.getOrCreateClassMapping(classInfo.name());

            // Use identity mapping for non-obfuscated classes
            classHashed.setDeobfuscatedName(nameProvider.getClassName(classInfo).orElse(classInfo.name()));

            for (MethodInfo methodInfo : classInfo.methods()) {
                Optional<String> hashedName = nameProvider.getMethodName(methodInfo);

                // Create method mapping if required
                if (hashedName.isPresent()) {
                    MethodMapping methodHashed = classHashed.createMethodMapping(methodInfo.name(), methodInfo.descriptor());
                    methodHashed.setDeobfuscatedName(hashedName.get());
                }
            }

            for (FieldInfo fieldInfo : classInfo.fields()) {
                Optional<String> hashedName = nameProvider.getFieldName(fieldInfo);

                // Create field mapping if required+
                if (hashedName.isPresent()) {
                    FieldMapping fieldHashed = classHashed.createFieldMapping(FieldSignature.of(fieldInfo.name(), fieldInfo.descriptor()));
                    fieldHashed.setDeobfuscatedName(hashedName.get());
                }
            }
        }

        return hashed;
    }
}
