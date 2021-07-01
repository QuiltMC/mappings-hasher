package org.quiltmc.hashed;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.quiltmc.hashed.asm.ClassInfo;
import org.quiltmc.hashed.asm.FieldInfo;
import org.quiltmc.hashed.asm.MethodInfo;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

public class HashedNameProvider {
    private final MessageDigest digest;
    private final Set<ClassInfo> classes;
    private final MappingSet mappings;

    private final Map<MethodInfo, Set<MethodInfo>> methodNameSets;
    private final Map<String, Set<ClassInfo>> simpleClassNameSet;

    public HashedNameProvider(Set<ClassInfo> classes, MappingSet mappings) {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        this.classes = classes;
        this.mappings = mappings;
        this.simpleClassNameSet = computeSimpleClassNameSet(classes, mappings);
        this.methodNameSets = computeMethodNameSets(classes.stream().flatMap(c -> c.methods().stream()).collect(Collectors.toSet()));
    }

    private static Map<String, Set<ClassInfo>> computeSimpleClassNameSet(Set<ClassInfo> classes, MappingSet mappings) {
        Map<String, Set<ClassInfo>> simpleClassNameSet = new HashMap<>();

        for (ClassInfo clazz : classes) {
            ClassMapping<?, ?> classMapping = mappings.getClassMapping(clazz.name())
                    .orElseThrow(() -> new RuntimeException("Missing mapping for class " + clazz.name()));

            // Simple name: Full name without the package, e.g. net/example/Class$Inner -> Class$Inner
            String fullName = classMapping.getFullDeobfuscatedName();
            String simpleName = fullName.substring(fullName.lastIndexOf('/') + 1);

            simpleClassNameSet.computeIfAbsent(simpleName, s -> new HashSet<>()).add(clazz);
        }

        return simpleClassNameSet;
    }

    private static Map<MethodInfo, Set<MethodInfo>> computeMethodNameSets(Set<MethodInfo> methods) {
        Map<MethodInfo, Set<MethodInfo>> nameSets = new HashMap<>();

        // Add override information to name sets
        for (MethodInfo method : methods) {
            for (MethodInfo override : method.overrides()) {
                Set<MethodInfo> nameSet = nameSets.computeIfAbsent(override, m -> new HashSet<>());
                nameSet.addAll(method.overrides());
            }
            Set<MethodInfo> nameSet = nameSets.computeIfAbsent(method, m -> new HashSet<>());
            nameSet.addAll(method.overrides());
        }

        // Resolve name sets
        for (MethodInfo method : methods) {
            Set<MethodInfo> nameSet = nameSets.computeIfAbsent(method, m -> new HashSet<>());

            // Methods that don't override and aren't overridden are only dependent on themselves
            if (nameSet.isEmpty()) {
                nameSet.add(method);
                continue;
            }

            // All methods in a name set must have the same name set, we check this recursively
            Set<MethodInfo> toCheck = new HashSet<>(nameSet);
            while (!toCheck.isEmpty()) {
                Set<MethodInfo> currentSet = toCheck;
                toCheck = new HashSet<>();

                for (MethodInfo current : currentSet) {
                    Set<MethodInfo> currentNameSet = nameSets.get(current);
                    for (MethodInfo nextToCheck : currentNameSet) {
                        if (!nameSet.contains(nextToCheck)) {
                            nameSet.add(nextToCheck);
                            toCheck.add(nextToCheck);
                        }
                    }
                }
            }
        }

        return nameSets;
    }

    private String getRawClassName(ClassInfo clazz) {
        // Don't look up unobfuscated names
        if (!clazz.isObfuscated()) {
            return clazz.name();
        }

        // Get the mapping
        ClassMapping<?, ?> classMapping = mappings.getClassMapping(clazz.name())
                .orElseThrow(() -> new RuntimeException("Missing mapping for class " + clazz.name()));

        // Simple name: Full name without the package, e.g. net/example/Class$Inner -> Class$Inner
        String fullName = classMapping.getFullDeobfuscatedName();
        String simpleName = fullName.substring(fullName.lastIndexOf('/') + 1);

        // Use the simple name for unique classes, otherwise the full names
        if (simpleClassNameSet.get(simpleName).size() > 1) {
            return fullName;
        }
        else {
            return simpleName;
        }
    }

    public String getClassName(ClassInfo clazz) {
        // Don't hash unobfuscated names
        if (!clazz.isObfuscated()) {
            return clazz.name();
        }

        return "net/minecraft/unmapped/C_" + getHashedString(getRawClassName(clazz));
    }

    private String getRawMethodName(MethodInfo method) {
        // Don't look up unobfuscated names
        if (!method.isObfuscated()) {
            return method.name();
        }

        // Get the mappings
        ClassMapping<?, ?> classMapping = mappings.getClassMapping(method.owner().name())
                .orElseThrow(() -> new RuntimeException("Missing mapping for class " + method.owner().name()));
        MethodMapping methodMapping = classMapping.getMethodMapping(method.name(), method.descriptor())
                .orElseThrow(() -> new RuntimeException("Missing mapping for method " + method.getFullName()));

        String className = getRawClassName(method.owner());
        boolean isMethodNameUnique = classMapping.getMethodMappings().stream()
                .filter(m -> m.getDeobfuscatedName().equals(methodMapping.getDeobfuscatedName()))
                .count() == 1;
        String methodName = methodMapping.getDeobfuscatedName();
        String methodDescriptor = isMethodNameUnique ? "" : methodMapping.getDeobfuscatedDescriptor();

        // "m;" prefix: methods with omitted descriptors need to be different to fields
        // Note that ";" and "." are illegal in jvm identifiers, so this should be safe
        // "m;<package>/<className>.<methodName>;<methodDescriptor>"
        return "m;" + className + "." + methodName + ";" + methodDescriptor;
    }

    public String getMethodName(MethodInfo method) {
        // Handle special method names
        if (method.name().equals("<init>") || method.name().equals("<clinit>")) {
            return method.name();
        }

        Set<MethodInfo> nameSet = methodNameSets.get(method);

        // Initialize with the lexicographically "smallest" string
        String rawName = "";
        for (MethodInfo current : nameSet) {
            // If there's an unobfuscated method in the name set, use that name directly
            if (!current.isObfuscated()) {
                return current.name();
            }

            String currentRawName = getRawMethodName(current);

            // Take the lexicographically "biggest" string
            if (currentRawName.compareTo(rawName) > 0) {
                rawName = currentRawName;
            }
        }

        return "m_" + getHashedString(rawName);
    }

    private String getRawFieldName(FieldInfo field) {
        // Don't hash unobfuscated names
        if (!field.isObfuscated()) {
            return field.name();
        }

        // Get the mappings
        ClassMapping<?, ?> classMapping = mappings.getClassMapping(field.owner().name())
                .orElseThrow(() -> new RuntimeException("Missing mapping for class " + field.owner().name()));
        FieldMapping fieldMapping = classMapping.getFieldMapping(field.name())
                .orElseThrow(() -> new RuntimeException("Missing mapping for field " + field.name()));

        String className = getRawClassName(field.owner());
        String fieldName = fieldMapping.getDeobfuscatedName();
        // While java doesn't allow it, the jvm allows fields that only differ in their descriptor.
        boolean isFieldNameUnique = classMapping.getFieldMappings().stream()
                .filter(f -> f.getDeobfuscatedName().equals(fieldMapping.getDeobfuscatedName()))
                .count() == 1;
        String fieldDescriptor = isFieldNameUnique ? "" : fieldMapping.getType().get().toString();

        // "f;" prefix: fields need to be different to methods with omitted descriptors
        // Note that ";" and "." are illegal in jvm identifiers, so this should be safe
        // "f;<className>.<fieldName>;<fieldDescriptor>"
        return "f;" + className + "." + fieldName + ";" + fieldDescriptor;
    }

    public String getFieldName(FieldInfo field) {
        // Don't hash unobfuscated names
        if (!field.isObfuscated()) {
            return field.name();
        }

        return "f_" + getHashedString(getRawFieldName(field));
    }

    private String getHashedString(String string) {
        // Hash the string and interpret the result as a big integer
        byte[] hash = digest.digest(string.getBytes());
        BigInteger bigInteger = new BigInteger(hash);

        // bits/digit = log(26) / log(2) ~ 4.7
        // bits/hash = 256
        // digits/hash = floor(bits/hash / bits/digit) = floor(54.46) = 54
        StringBuilder builder = new StringBuilder();
        int digits = 8; // Max: 256 * log(2) / log(base)
        int base = 26;
        for (int i = 0; i < digits; i++) {
            int digit = bigInteger.mod(BigInteger.valueOf(base)).intValue();
            bigInteger = bigInteger.divide(BigInteger.valueOf(base));

            builder.append((char)('a' + digit));
        }

        return builder.toString();
    }
}
