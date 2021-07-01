package org.quiltmc.hashed;

import net.fabricmc.lorenztiny.TinyMappingsWriter;
import org.cadixdev.bombe.type.signature.FieldSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.TextMappingsReader;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.cadixdev.lorenz.model.*;
import org.quiltmc.hashed.asm.ClassInfo;
import org.quiltmc.hashed.asm.ClassResolver;
import org.quiltmc.hashed.asm.FieldInfo;
import org.quiltmc.hashed.asm.MethodInfo;
import org.quiltmc.hashed.web.Library;
import org.quiltmc.hashed.web.Version;
import org.quiltmc.hashed.web.VersionManifest;
import org.quiltmc.json5.JsonReader;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: <command> <version>");
            return;
        }

        System.out.println("Reading version manifest...");
        URL manifestUrl = new URL("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json");
        InputStreamReader manifestReader = new InputStreamReader(manifestUrl.openConnection().getInputStream());
        JsonReader manifestJson = JsonReader.json(new BufferedReader(manifestReader));
        VersionManifest manifest = VersionManifest.fromJson(manifestJson);

        System.out.println("Reading version...");
        Version version = manifest.versions().get(args[0]);
        if (version == null) {
            System.out.println("Unknown version: " + args[0]);
            return;
        }
        version.resolve();

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

        System.out.println("Writing mappings to file...");
        Path outPath = Paths.get("mappings", version.id() + ".tiny");
        Files.createDirectories(outPath.getParent());
        Files.deleteIfExists(outPath);
        Files.createFile(outPath);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(outPath)));
        TinyMappingsWriter mappingsWriter = new TinyMappingsWriter(writer, "official", "hashed");
        mappingsWriter.write(obf_to_hashed);
        writer.flush();
        writer.close();
    }
}
