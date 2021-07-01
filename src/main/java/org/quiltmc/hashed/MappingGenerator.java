package org.quiltmc.hashed;

import org.cadixdev.bombe.type.signature.FieldSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.TextMappingsReader;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.quiltmc.hashed.asm.ClassInfo;
import org.quiltmc.hashed.asm.ClassResolver;
import org.quiltmc.hashed.asm.FieldInfo;
import org.quiltmc.hashed.asm.MethodInfo;
import org.quiltmc.hashed.web.Library;
import org.quiltmc.hashed.web.Version;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;

public class MappingGenerator {
    private final Version version;
    public MappingGenerator(Version version) {
        this.version = version;
    }

    public MappingSet generate() throws IOException {
        this.version.resolve();

        System.out.println("Loading libs...");
        ClassResolver resolver = new ClassResolver();
        for (Library lib : version.libraries()) {
            JarFile libJar = new JarFile(lib.getOrDownload());
            resolver.addLibrary(libJar);
        }

        System.out.println("Loading client jar...");
        JarFile clientJar = new JarFile(version.downloads().get("client").getOrDownload());
        Set<ClassInfo> classes = resolver.extractClassInfo(clientJar);

        System.out.println("Loading Mojang mappings...");
        File mojmapFile = version.downloads().get("client_mappings").getOrDownload();
        InputStream mojmapStream = Files.newInputStream(mojmapFile.toPath());
        TextMappingsReader mappingsReader = new ProGuardReader(new InputStreamReader(mojmapStream));
        MappingSet obf_to_mojmap = mappingsReader.read().reverse();

        HashedNameProvider nameProvider = new HashedNameProvider(classes, obf_to_mojmap);


        // Annotations used to mark classes, fields, or methods as not obfuscated
        Set<String> hashedDontObfuscateAnnotations = new HashSet<>(Arrays.asList(
                "net/minecraft/unmapped/C_qwuptkcl",
                "net/minecraft/unmapped/C_prlazzma"
        ));
        // Resolve obfuscated annotations
        Set<String> dontObfuscateAnnotations = new HashSet<>();
        for (ClassInfo classInfo : classes) {
            if (hashedDontObfuscateAnnotations.contains(nameProvider.getClassName(classInfo))) {
                dontObfuscateAnnotations.add(classInfo.name());
            }
        }
        // Apply annotations
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

        System.out.println("Creating hashed mappings...");
        MappingSet obf_to_hashed = MappingSet.create();
        for (ClassInfo classInfo : classes) {

            String[] classHierarchy = classInfo.name().split("\\$");
            ClassMapping<?, ?> classHashed = obf_to_hashed.getOrCreateTopLevelClassMapping(classHierarchy[0]);
            for (int i = 1; i < classHierarchy.length; i++) {
                classHashed = classHashed.getOrCreateInnerClassMapping(classHierarchy[i]);
            }

            classHashed.setDeobfuscatedName(nameProvider.getClassName(classInfo));

            // Field mapping
            for (FieldInfo fieldInfo : classInfo.fields()) {
                FieldMapping fieldHashed = classHashed.createFieldMapping(FieldSignature.of(fieldInfo.name(), fieldInfo.descriptor()));
                fieldHashed.setDeobfuscatedName(nameProvider.getFieldName(fieldInfo));
            }

            // Method mapping
            for (MethodInfo methodInfo : classInfo.methods()) {

                MethodMapping methodHashed = classHashed.createMethodMapping(methodInfo.name(), methodInfo.descriptor());
                methodHashed.setDeobfuscatedName(nameProvider.getMethodName(methodInfo));
            }
        }

        return obf_to_hashed;
    }
}
