package org.quiltmc.hashed;

import net.fabricmc.lorenztiny.TinyMappingsWriter;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.TextMappingsReader;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.objectweb.asm.*;
import org.quiltmc.json5.JsonReader;

import java.io.*;
import java.math.BigInteger;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.JarFile;

public class Main {
    public static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");

        if (args.length != 1) {
            System.out.println("Usage: <command> <version>");
            return;
        }

        System.out.println("Reading version manifest...");
        URL manifestUrl = new URL(MANIFEST_URL);
        InputStreamReader manifestReader = new InputStreamReader(manifestUrl.openConnection().getInputStream());
        JsonReader manifestJson = JsonReader.json(new BufferedReader(manifestReader));
        VersionManifest manifest = VersionManifest.fromJson(manifestJson);

        Optional<VersionManifest.Version> versionEntry = manifest.versions().stream().filter(v -> v.id().equals(args[0])).findFirst();
        if (!versionEntry.isPresent()) {
            System.out.println("Unknown version: " + args[0]);
            return;
        }

        System.out.println("Reading version json...");
        URL versionUrl = versionEntry.get().url();
        InputStreamReader versionReader = new InputStreamReader(versionUrl.openConnection().getInputStream());
        JsonReader versionJson = JsonReader.json(new BufferedReader(versionReader));
        Version version = Version.fromJson(versionJson);

        System.out.println("Loading jar...");
        URL jarUrl = new URL("jar:" + version.downloads().get("client").url() + "!/");
        JarURLConnection jarURLConnection = (JarURLConnection) jarUrl.openConnection();
        JarFile jar = jarURLConnection.getJarFile();

        System.out.println("Collecting classes, fields and methods...");
        Map<String, ClassInfo> classes = new HashMap<>();
        jar.stream().filter(e -> e.getName().endsWith(".class")).forEach(entry -> {
            try {
                InputStream stream = jar.getInputStream(entry);
                ClassReader classReader = new ClassReader(stream);

                ClassInfo.ClassInfoVisitor classVisitor = new ClassInfo.ClassInfoVisitor();
                classReader.accept(classVisitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                ClassInfo info = classVisitor.getClassInfo();
                classes.put(info.name, info);
            } catch (IOException e) {
                System.out.println("Error reading class file: " + entry.getName());
            }
        });

        System.out.println("Resolve overrides...");
        for (ClassInfo classInfo : classes.values()) {
            for (ClassInfo.MethodInfo methodInfo : classInfo.methods.values()) {
                methodInfo.resolveOverrides(classes);
            }
        }

        System.out.println("Resolve equalities...");
        Map<ClassInfo.MethodInfo, Set<ClassInfo.MethodInfo>> equalities = new HashMap<>();
        for (ClassInfo classInfo : classes.values()) {
            for (ClassInfo.MethodInfo methodInfo : classInfo.methods.values()) {
                if (methodInfo.overrides.size() > 1) {
                    for (ClassInfo.MethodInfo overrideInfo : methodInfo.overrides) {
                        Set<ClassInfo.MethodInfo> equals = equalities.computeIfAbsent(overrideInfo, i -> new HashSet<>());
                        equals.addAll(methodInfo.overrides);
                    }
                }
            }
        }

        System.out.println("Loading mojang mappings...");
        URL mappingsUrl = version.downloads().get("client_mappings").url();
        TextMappingsReader mappingsReader = new ProGuardReader(new InputStreamReader(mappingsUrl.openConnection().getInputStream()));
        MappingSet obf_to_mojmap = mappingsReader.read().reverse();

        System.out.println("Creating hashed mappings...");
        MappingSet obf_to_hashed = MappingSet.create();
        for (ClassInfo classInfo : classes.values()) {
            ClassMapping<?, ?> classMojmap = obf_to_mojmap.getClassMapping(classInfo.name).orElse(null);
            if (classMojmap == null) {
                System.err.println("Missing mapping for class " + classInfo.name);
                continue;
            }

            // TODO: Handle package-unique classes
            String classNameRaw = classMojmap.getFullDeobfuscatedName();
            byte[] classBytes = sha256.digest(classNameRaw.getBytes());
            BigInteger classInt = new BigInteger(classBytes);
            String className = "C_" + BaseConverter.toBase26(classInt).substring(0, 8);
            ClassMapping<?, ?> classHashed = obf_to_hashed.getOrCreateClassMapping(classInfo.name);
            classHashed.setDeobfuscatedName(className);

            for (ClassInfo.FieldInfo fieldInfo : classInfo.fields) {
                FieldMapping fieldMojmap = classMojmap.getFieldMapping(fieldInfo.name).orElse(null);
                if (fieldMojmap == null) {
                    System.err.println("Missing mapping for field " + fieldInfo.name);
                    continue;
                }

                String fieldNameRaw = fieldMojmap.getFullDeobfuscatedName();
                byte[] fieldBytes = sha256.digest(fieldNameRaw.getBytes());
                BigInteger fieldInt = new BigInteger(fieldBytes);
                String fieldName = "f_" + BaseConverter.toBase26(fieldInt).substring(0, 8);
                FieldMapping fieldHashed = classMojmap.createFieldMapping(fieldInfo.name);
                fieldHashed.setDeobfuscatedName(fieldName);
            }

            for (ClassInfo.MethodInfo methodInfo : classInfo.methods.values()) {
                Set<String> rawNames = new HashSet<>();
                if (!methodInfo.overrides.isEmpty()) {
                    for (ClassInfo.MethodInfo overrideInfo : methodInfo.overrides) {
                        if (equalities.containsKey(overrideInfo)) {
                            for (ClassInfo.MethodInfo equalityInfo : equalities.get(overrideInfo)) {
                                ClassMapping<?, ?> equalityClassMojmap = obf_to_mojmap.getClassMapping(equalityInfo.owner.name).orElse(null);
                                if (equalityClassMojmap == null) {
                                    System.err.println("Missing mapping for class " + equalityInfo.owner.name);
                                    continue;
                                }

                                MethodMapping equalMethodMojmap = equalityClassMojmap.getMethodMapping(equalityInfo.name, equalityInfo.descriptor).orElse(null);
                                if (equalMethodMojmap == null) {
                                    System.err.println("Missing mapping for method " + equalityInfo.owner.name + "/" + equalityInfo.name + equalityInfo.descriptor);
                                    continue;
                                }

                                rawNames.add(equalMethodMojmap.getFullDeobfuscatedName());
                            }
                        }
                        else {
                            ClassMapping<?, ?> overrideClassMojmap = obf_to_mojmap.getClassMapping(overrideInfo.owner.name).orElse(null);
                            if (overrideClassMojmap == null) {
                                System.err.println("Missing mapping for class " + overrideInfo.owner.name);
                                continue;
                            }

                            MethodMapping overrideMethodMojmap = overrideClassMojmap.getMethodMapping(overrideInfo.name, overrideInfo.descriptor).orElse(null);
                            if (overrideMethodMojmap == null) {
                                System.err.println("Missing mapping for method " + overrideInfo.owner.name + "/" + overrideInfo.name + overrideInfo.descriptor);
                                continue;
                            }

                            rawNames.add(overrideMethodMojmap.getFullDeobfuscatedName());
                        }
                    }
                }
                else if (equalities.containsKey(methodInfo)) {
                    for (ClassInfo.MethodInfo equalityInfo : equalities.get(methodInfo)) {
                        ClassMapping<?, ?> equalityClassMojmap = obf_to_mojmap.getClassMapping(equalityInfo.owner.name).orElse(null);
                        if (equalityClassMojmap == null) {
                            System.err.println("Missing mapping for class " + equalityInfo.owner.name);
                            continue;
                        }

                        MethodMapping equalMethodMojmap = equalityClassMojmap.getMethodMapping(equalityInfo.name, equalityInfo.descriptor).orElse(null);
                        if (equalMethodMojmap == null) {
                            System.err.println("Missing mapping for method " + equalityInfo.owner.name + "/" + equalityInfo.name + equalityInfo.descriptor);
                            continue;
                        }

                        rawNames.add(equalMethodMojmap.getFullDeobfuscatedName());
                    }
                }
                else {
                    MethodMapping methodMojmap = classMojmap.getMethodMapping(methodInfo.name, methodInfo.descriptor).orElse(null);
                    if (methodMojmap == null) {
                        System.err.println("Missing mapping for method " + methodInfo.owner.name + "/" + methodInfo.name + methodInfo.descriptor);
                        continue;
                    }
                    rawNames.add(methodMojmap.getFullDeobfuscatedName());
                }

                BigInteger methodInt = BigInteger.ZERO;
                for (String rawName : rawNames) {
                    byte[] bytes = sha256.digest(rawName.getBytes());
                    BigInteger integer = new BigInteger(bytes);
                    methodInt = methodInt.xor(integer);
                }
                String methodName = "m_" + BaseConverter.toBase26(methodInt).substring(0, 8);
                MethodMapping methodHashed = classHashed.createMethodMapping(methodInfo.name, methodInfo.descriptor);
                if ("<init>".equals(methodInfo.name) || "<clinit>".equals(methodInfo.name)) {
                    methodHashed.setDeobfuscatedName(methodInfo.name);
                }
                else {
                    methodHashed.setDeobfuscatedName(methodName);
                }
            }
        }

        System.out.println("Writing mappings to file...");
        Path outPath = Paths.get(version.id() + ".tiny");
        Files.deleteIfExists(outPath);
        Files.createFile(outPath);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(outPath)));
        TinyMappingsWriter mappingsWriter = new TinyMappingsWriter(writer, "official", "hashed");
        mappingsWriter.write(obf_to_hashed);
        writer.flush();
    }
}
