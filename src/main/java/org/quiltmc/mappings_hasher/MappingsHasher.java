package org.quiltmc.mappings_hasher;

import org.cadixdev.bombe.type.signature.FieldSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.objectweb.asm.Opcodes;
import org.quiltmc.mappings_hasher.asm.ClassInfo;
import org.quiltmc.mappings_hasher.asm.ClassResolver;
import org.quiltmc.mappings_hasher.asm.FieldInfo;
import org.quiltmc.mappings_hasher.asm.MethodInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;

public class MappingsHasher {
    private final MappingSet original;
    private final String defaultPackage;
    private final ClassResolver classResolver = new ClassResolver();
    private final Set<String> dontObfuscateAnnotations = new HashSet<>();
    private final Set<String> hashedDontObfuscateAnnotations = new HashSet<>();

    public MappingsHasher(MappingSet original, String defaultPackage) {
        this.original = original;
        this.defaultPackage = defaultPackage;
    }

    public void addLibrary(JarFile jar) {
        classResolver.addLibrary(jar);
    }

    public void addDontObfuscateAnnotation(String annotation, boolean hashed) {
        if (hashed) {
            this.hashedDontObfuscateAnnotations.add(annotation);
        } else {
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
            String simpleClassName = getSimpleClassName(classInfo.name());
            String simpleNamedClassName = getSimpleClassName(nameProvider.getRawClassName(classInfo));

            if (simpleClassName.equals(simpleNamedClassName) || Character.isDigit(simpleClassName.charAt(0))) {
                classInfo.dontObfuscate();
                if (classInfo.annotations().stream().anyMatch(dontObfuscateAnnotations::contains)) {
                    classInfo.methods().forEach(MethodInfo::dontObfuscate);
                    classInfo.fields().forEach(FieldInfo::dontObfuscate);
                }
            }

            for (MethodInfo methodInfo : classInfo.methods()) {
                String simpleMethodName = methodInfo.name();
                String namedMethodName = nameProvider.getRawMethodName(methodInfo);
                String simpleNamedMethodName = namedMethodName.substring(namedMethodName.lastIndexOf(".") + 1, namedMethodName.length() - 1);
                if (simpleMethodName.equals(simpleNamedMethodName) || methodInfo.annotations().stream().anyMatch(dontObfuscateAnnotations::contains)) {
                    if ((classInfo.access() & Opcodes.ACC_INTERFACE) != 0 && classInfo.methods().size() > 1 && classInfo.annotations().stream().noneMatch(annotation -> annotation.equals("java/lang/FunctionalInterface"))) {
                        methodInfo.dontObfuscate();
                    }
                }
            }
            for (FieldInfo fieldInfo : classInfo.fields()) {
                String simpleFieldName = fieldInfo.name();
                String namedFieldName = nameProvider.getRawFieldName(fieldInfo);
                String simpleNamedFieldName = namedFieldName.substring(namedFieldName.lastIndexOf(".") + 1, namedFieldName.length() - 1);
                if (simpleFieldName.equals(simpleNamedFieldName) || fieldInfo.annotations().stream().anyMatch(dontObfuscateAnnotations::contains)) {
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

    private String getSimpleClassName(String name) {
        return name.contains("$") ?
                name.substring(name.lastIndexOf("$") + 1) :
                (name.contains("/") ?
                        name.substring(name.lastIndexOf("/") + 1) :
                        name);
    }
}
