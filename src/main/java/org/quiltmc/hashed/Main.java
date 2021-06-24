package org.quiltmc.hashed;

import net.fabricmc.lorenztiny.TinyMappingsWriter;
import org.cadixdev.bombe.type.signature.FieldSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.TextMappingsReader;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.InnerClassMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.quiltmc.hashed.asm.ClassInfo;
import org.quiltmc.hashed.asm.ClassResolver;
import org.quiltmc.hashed.asm.FieldInfo;
import org.quiltmc.hashed.asm.MethodInfo;
import org.quiltmc.hashed.web.Library;
import org.quiltmc.hashed.web.Version;
import org.quiltmc.hashed.web.VersionManifest;
import org.quiltmc.json5.JsonReader;

import java.io.*;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Main {
    public static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    public static MessageDigest SHA256;
    static {
        try {
            SHA256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("No such algorithm: SHA-256");
        }
    }

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException {

        if (args.length != 1) {
            System.out.println("Usage: <command> <version>");
            return;
        }

        System.out.println("Reading version manifest...");
        URL manifestUrl = new URL(MANIFEST_URL);
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

        System.out.println("Loading mojang mappings...");
        File mojmapFile = version.downloads().get("client_mappings").getOrDownload();
        InputStream mojmapStream = Files.newInputStream(mojmapFile.toPath());
        TextMappingsReader mappingsReader = new ProGuardReader(new InputStreamReader(mojmapStream));
        MappingSet obf_to_mojmap = mappingsReader.read().reverse();

        Set<String> dontObfuscateAnnotations = new HashSet<>();
        dontObfuscateAnnotations.add("net/minecraft/obfuscate/DontObfuscate");
        Set<String> dontObfuscateClassAnnotations = new HashSet<>();
        obf_to_mojmap.getTopLevelClassMappings().stream().filter(c ->
                "C_prlazzma".equals("C_" + createHashedName(c.getFullDeobfuscatedName()).substring(0, 8))
        ).forEach(c -> dontObfuscateClassAnnotations.add(c.getFullObfuscatedName()));
        ClassResolver resolver = new ClassResolver(dontObfuscateAnnotations, dontObfuscateClassAnnotations);

        System.out.println("Loading client jar...");
        File clientJar = version.downloads().get("client").getOrDownload();
        resolver.addJar(clientJar, true);

        System.out.println("Loading libs...");
        for (Library lib : version.libraries()) {
            File libJar = lib.getOrDownload();
            resolver.addJar(libJar, false);
        }

        System.out.println("Resolving classes...");
        HashMap<String, ClassInfo> classes = new HashMap<>();
        resolver.foreachClassInfoInJar(clientJar, classInfo -> {
            classes.put(classInfo.name(), classInfo);
        });

        Map<MethodInfo, Set<MethodInfo>> equalities = new HashMap<>();

        // Direct equalities: C extends A and B, all 3 methods should be named (AxB)
        for (ClassInfo classInfo : classes.values()) {
            for (MethodInfo methodInfo : classInfo.methods().values()) {
                if (methodInfo.superMethods().size() > 1) {
                    for (MethodInfo overrideInfo : methodInfo.superMethods()) {
                        Set<MethodInfo> equals = equalities.computeIfAbsent(overrideInfo, i -> new HashSet<>());
                        equals.addAll(methodInfo.superMethods());
                    }
                }
            }
        }

        // Indirect equalities: D extends A and B, E extends B and C, all 5 methods should be named (AxBxC)
        // TODO: Not sure if this is correct (or even necessary)
        for (Map.Entry<MethodInfo, Set<MethodInfo>> entry : equalities.entrySet()) {
            Set<MethodInfo> checked = new HashSet<>();
            Set<MethodInfo> toCheck = new HashSet<>(entry.getValue());
            entry.getValue().clear();
            while (!toCheck.isEmpty()) {
                Set<MethodInfo> currentCheck = new HashSet<>(toCheck);
                toCheck.clear();
                for (MethodInfo info : currentCheck) {
                    if (checked.contains(info)) {
                        continue;
                    }
                    entry.getValue().addAll(equalities.get(info));
                    toCheck.addAll(equalities.get(info));
                    checked.add(info);
                }
            }
        }

        // Resolve name sets
        Map<MethodInfo, Set<MethodInfo>> nameSets = new HashMap<>();
        for (ClassInfo classInfo : classes.values()) {
            for (MethodInfo methodInfo : classInfo.methods().values()) {
                Set<MethodInfo> nameSet = nameSets.computeIfAbsent(methodInfo, m -> new HashSet<>());
                if (equalities.containsKey(methodInfo)) {
                    nameSet.addAll(equalities.get(methodInfo));
                }
                else if (!methodInfo.superMethods().isEmpty()) {
                    for (MethodInfo superMethod : methodInfo.superMethods()) {
                        if (equalities.containsKey(superMethod)) {
                            nameSet.addAll(equalities.get(superMethod));
                        }
                        else {
                            nameSet.add(superMethod);
                        }
                    }
                }
                else {
                    nameSet.add(methodInfo);
                }
            }
        }

        // Collecting unique simple class names
        HashMap<String, Set<ClassInfo>> simpleToInfo = new HashMap<>();
        for (ClassInfo classInfo : classes.values()) {
            ClassMapping<?, ?> classMojmap = obf_to_mojmap.getClassMapping(classInfo.name()).orElse(null);
            if (classMojmap == null) {
                throw new RuntimeException("Missing mapping for class " + classInfo.name());
            }

            String fullName = classMojmap.getFullDeobfuscatedName();
            String simpleName = fullName.substring(fullName.lastIndexOf('/') + 1);
            Set<ClassInfo> sameSimple = simpleToInfo.computeIfAbsent(simpleName, s -> new HashSet<>());
            sameSimple.add(classInfo);
        }

        System.out.println("Creating hashed mappings...");
        MappingSet obf_to_hashed = MappingSet.create();
        for (ClassInfo classInfo : classes.values()) {
            ClassMapping<?, ?> classMojmap = obf_to_mojmap.getClassMapping(classInfo.name()).orElse(null);
            if (classMojmap == null) {
                throw new RuntimeException("Missing mapping for class " + classInfo.name());
            }

            ClassMapping<?, ?> classHashed = obf_to_hashed.getOrCreateClassMapping(classInfo.name());
            if (classInfo.isObfuscated()) {
                String classNameRaw;
                String fullName = classMojmap.getFullDeobfuscatedName();
                String simpleName = fullName.substring(fullName.lastIndexOf('/') + 1);
                if (simpleToInfo.get(simpleName).size() == 1 ) {
                    // Use simple name whenever it is unique
                    // This prevents package changes from affecting the hashed mapping
                    classNameRaw = simpleName;
                }
                else {
                    classNameRaw = fullName;
                }
                String className = "C_" + createHashedName(classNameRaw).substring(0, 8);
                classHashed.setDeobfuscatedName(className);
            }

            // Field mapping
            for (FieldInfo fieldInfo : classInfo.fields().values()) {
                FieldMapping fieldMojmap = classMojmap.getFieldMapping(fieldInfo.name()).orElse(null);
                if (fieldMojmap == null) {
                    throw new RuntimeException("Missing mapping for field " + fieldInfo.name());
                }

                if (!fieldInfo.isObfuscated()) {
                    continue;
                }

                String fieldNameRaw = fieldMojmap.getFullDeobfuscatedName();
                String fieldName = "f_" + createHashedName(fieldNameRaw).substring(0, 8);
                FieldMapping fieldHashed = classHashed.createFieldMapping(FieldSignature.of(fieldInfo.name(), fieldInfo.descriptor()));
                fieldHashed.setDeobfuscatedName(fieldName);
            }

            // Method mapping
            for (MethodInfo methodInfo : classInfo.methods().values()) {
                if ("<init>".equals(methodInfo.name()) || "<clinit>".equals(methodInfo.name())) {
                    continue;
                }

                Set<MethodInfo> nameSet = nameSets.get(methodInfo);
                if (nameSet.stream().anyMatch(m -> !m.isObfuscated())) {
                    continue;
                }

                MethodMapping methodHashed = classHashed.createMethodMapping(methodInfo.name(), methodInfo.descriptor());
                Set<String> rawNames = new HashSet<>();
                for (MethodInfo m : nameSet) {
                    ClassMapping<?, ?> classMapping = obf_to_mojmap.getClassMapping(m.owner().name()).orElse(null);
                    if (classMapping == null) {
                        throw new RuntimeException("Missing mapping for class " + m.owner().name());
                    }
                    MethodMapping methodMapping = classMapping.getMethodMapping(m.name(), m.descriptor()).orElse(null);
                    if (methodMapping == null) {
                        throw new RuntimeException("Missing mapping for method " + m.getFullName());
                    }
                    rawNames.add(methodMapping.getFullDeobfuscatedName());
                }

                String methodName = "m_" + createHashedName(rawNames.toArray(new String[] {})).substring(0, 8);
                methodHashed.setDeobfuscatedName(methodName);
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

    public static String createHashedName(String... rawNames) {
        BigInteger totalInt = BigInteger.ZERO;
        for (String rawName : rawNames) {
            byte[] bytes = SHA256.digest(rawName.getBytes());
            BigInteger integer = new BigInteger(bytes);
            totalInt = totalInt.xor(integer);
        }
        return BaseConverter.toBase26(totalInt);
    }
}
