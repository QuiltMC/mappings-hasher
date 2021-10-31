package org.quiltmc.mappings_hasher;

import org.cadixdev.bombe.type.signature.FieldSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.Mapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.quiltmc.mappings_hasher.asm.ClassInfo;
import org.quiltmc.mappings_hasher.asm.FieldInfo;
import org.quiltmc.mappings_hasher.asm.MethodInfo;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

public class HashedNameProvider {
    private final MessageDigest digest;
    private final MappingSet mappings;
    private final String defaultPackage;

    private final Map<MethodInfo, Set<MethodInfo>> methodNameSets;
    private final Map<String, Set<ClassInfo>> simpleClassNameSet;

    public HashedNameProvider(Set<ClassInfo> classes, MappingSet mappings, String defaultPackage) {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        this.mappings = mappings;
        this.defaultPackage = defaultPackage;
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

        // Merge name sets
        for (MethodInfo method : methods) {
            // Create name set if it doesn't exist yet
            Set<MethodInfo> nameSet = nameSets.computeIfAbsent(method, m -> new HashSet<>());

            // Add method itself to the set
            nameSet.add(method);

            // Merge all superMethod name sets into this name set
            for (MethodInfo superMethod : method.overrides()) {
                Set<MethodInfo> superNameSet = nameSets.computeIfAbsent(superMethod, m -> new HashSet<>());
                superNameSet.add(superMethod);
                nameSet.addAll(superNameSet);
            }

            // Redirect all methods in this name set to this name set
            for (MethodInfo setMethod : nameSet) {
                nameSets.put(setMethod, nameSet);
            }
        }

        // Only keep top-level methods in the name sets
        for (Set<MethodInfo> nameSet : nameSets.values()) {
            nameSet.removeIf(m -> m.overrides().size() > 0);
        }

        return nameSets;
    }

    private String getRawClassName(ClassInfo clazz) {
        // Get the mapping
        ClassMapping<?, ?> classMapping = mappings.getClassMapping(clazz.name())
                .orElseThrow(() -> new RuntimeException("Missing mapping for class " + clazz.name()));

        // Don't obfuscate non-obfuscated classes
        if (!isObfuscated(classMapping)) {
            return clazz.name();
        }

        // Ful name: Package + Outer Class + Inner Class
        String fullName = classMapping.getFullDeobfuscatedName();

        // Simple name: Full name without the package, e.g. net/example/Class$Inner -> Class$Inner
        String simpleName = fullName.substring(fullName.lastIndexOf('/') + 1);

        // Raw name: The simple name if unique, otherwise the full name
        return simpleClassNameSet.get(simpleName).size() == 1 ? simpleName : fullName;
    }

    public Optional<String> getClassName(ClassInfo clazz) {
        // Get the mapping
        ClassMapping<?, ?> classMapping = mappings.getClassMapping(clazz.name())
                .orElseThrow(() -> new RuntimeException("Missing mapping for class " + clazz.name()));

        // Don't obfuscate non-obfuscated classes
        if (!isObfuscated(classMapping)) {
            return Optional.empty();
        }

        // Prefix: None for inner classes, otherwise the default package (if non-empty)
        String prefix = clazz.name().contains("$") || this.defaultPackage.isEmpty() ? "" : this.defaultPackage + "/";

        // Hashed name: prefix plus class identifier plus hash of raw name
        return Optional.of(prefix + "C_" + getHashedString(getRawClassName(clazz)));
    }

    public String getRawMethodName(MethodInfo method) {
        // Get the mappings
        ClassMapping<?, ?> classMapping = mappings.getClassMapping(method.owner().name())
                .orElseThrow(() -> new RuntimeException("Missing mapping for class " + method.owner().name()));
        MethodMapping methodMapping = classMapping.getMethodMapping(method.name(), method.descriptor())
                .orElseThrow(() -> new RuntimeException("Missing mapping for method " + method.getFullName()));

        // No need for a mapping if the method isn't obfuscated
        if (!isObfuscated(methodMapping)) {
            return method.name();
        }

        // Check if there's a method with the same name (but different descriptor)
        boolean isMethodNameNonUnique = classMapping.getMethodMappings().stream()
                .filter(m -> m.getDeobfuscatedName().equals(methodMapping.getDeobfuscatedName()))
                .count() > 1;

        // Get the raw class name
        String className = getRawClassName(method.owner());

        // Get the method name
        String methodName = methodMapping.getDeobfuscatedName();

        // Omit the descriptor for unique method names
        String methodDescriptor = isMethodNameNonUnique ? methodMapping.getDeobfuscatedDescriptor() : "";

        // "m;" prefix: methods with omitted descriptors need to be different to fields
        // Note that ";" and "." are illegal in jvm identifiers, so this should be safe
        // "m;<package>/<className>.<methodName>;<methodDescriptor>"
        return "m;" + className + "." + methodName + ";" + methodDescriptor;
    }

    public Optional<String> getMethodName(MethodInfo method) {
        // Get the mappings
        ClassMapping<?, ?> classMapping = mappings.getClassMapping(method.owner().name())
                .orElseThrow(() -> new RuntimeException("Missing mapping for class " + method.owner().name()));
        MethodMapping methodMapping = classMapping.getMethodMapping(method.name(), method.descriptor())
                .orElseThrow(() -> new RuntimeException("Missing mapping for method " + method.getFullName()));

        // No need for a mapping if the method isn't obfuscated
        if (!isObfuscated(methodMapping)) {
            return Optional.empty();
        }

        // The name of this method is determined by the first of its name set
        Set<MethodInfo> nameSet = methodNameSets.get(method);
        MethodInfo nameSource = nameSet.stream().min(Comparator.comparing(this::getRawMethodName))
                .orElseThrow(() -> new RuntimeException("No name source for method " + method.getFullName()));

        // No mapping is needed if the name doesn't come from this method
        if (nameSource != method) {
            return Optional.empty();
        }

        return Optional.of("m_" + getHashedString(getRawMethodName(nameSource)));
    }

    public String getRawFieldName(FieldInfo field) {
        // Get the mappings
        ClassMapping<?, ?> classMapping = mappings.getClassMapping(field.owner().name())
                .orElseThrow(() -> new RuntimeException("Missing mapping for class " + field.owner().name()));
        FieldMapping fieldMapping = classMapping.getFieldMapping(FieldSignature.of(field.name(), field.descriptor()))
                .orElseThrow(() -> new RuntimeException("Missing mapping for field " + field.name()));

        // No need for a mapping if the field isn't obfuscated
        if (!isObfuscated(fieldMapping)) {
            return field.name();
        }

        // Check if there's a field with the same name (but different descriptor)
        // While java doesn't allow it, the jvm allows fields that only differ in their descriptor.
        boolean isFieldNameNonUnique = classMapping.getFieldMappings().stream()
                .filter(f -> f.getDeobfuscatedName().equals(fieldMapping.getDeobfuscatedName()))
                .count() > 1;

        // Get the raw class name
        String className = getRawClassName(field.owner());

        // Get the field name
        String fieldName = fieldMapping.getDeobfuscatedName();

        // Omit the descriptor for unique field names
        String fieldDescriptor = isFieldNameNonUnique ? fieldMapping.getType().get().toString() : "";

        // "f;" prefix: fields need to be different to methods with omitted descriptors
        // Note that ";" and "." are illegal in jvm identifiers, so this should be safe
        // "f;<className>.<fieldName>;<fieldDescriptor>"
        return "f;" + className + "." + fieldName + ";" + fieldDescriptor;
    }

    public Optional<String> getFieldName(FieldInfo field) {
        // Get the mappings
        ClassMapping<?, ?> classMapping = mappings.getClassMapping(field.owner().name())
                .orElseThrow(() -> new RuntimeException("Missing mapping for class " + field.owner().name()));
        FieldMapping fieldMapping = classMapping.getFieldMapping(FieldSignature.of(field.name(), field.descriptor()))
                .orElseThrow(() -> new RuntimeException("Missing mapping for field " + field.name()));

        // No need for a mapping if the field isn't obfuscated
        if (!isObfuscated(fieldMapping)) {
            return Optional.empty();
        }

        return Optional.of("f_" + getHashedString(getRawFieldName(field)));
    }

    private boolean isObfuscated(Mapping<?, ?> mapping) {
        return mapping.getDeobfuscatedName().length() == 1 ||
                !mapping.getDeobfuscatedName().equals(mapping.getObfuscatedName());
    }

    private String getHashedString(String string) {
        // Hash the string and interpret the result as a big integer
        byte[] hash = digest.digest(string.getBytes());
        BigInteger bigInteger = new BigInteger(hash);

        StringBuilder builder = new StringBuilder();
        int digits = 8; // Max: 256 * log(2) / log(base)
        int base = 26;
        for (int i = 0; i < digits; i++) {
            int digit = bigInteger.mod(BigInteger.valueOf(base)).intValue();
            bigInteger = bigInteger.divide(BigInteger.valueOf(base));

            builder.insert(0, (char)('a' + digit));
        }

        return builder.toString();
    }
}
