package org.quiltmc.mappings_hasher;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.*;

public class MappingsHasher {
    private final MappingSet original;
    private final String defaultPackage;
    private final MessageDigest digest;

    public MappingsHasher(MappingSet original, String defaultPackage) {
        this.original = original;
        this.defaultPackage = defaultPackage;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private void hashClassMapping(ClassMapping<?, ?> classMapping) {
        // Recurse to inner classes
        for (InnerClassMapping innerClassMapping : classMapping.getInnerClassMappings()) {
            hashClassMapping(innerClassMapping);
        }

        // Hash methods
        for (MethodMapping methodMapping : classMapping.getMethodMappings()) {
            if (isObfuscated(methodMapping)) {
                methodMapping.setDeobfuscatedName("m_" + getHashedString(methodMapping.getSimpleDeobfuscatedName()));
            }
        }

        // Hash fields
        for (FieldMapping fieldMapping : classMapping.getFieldMappings()) {
            if (isObfuscated(fieldMapping)) {
                fieldMapping.setDeobfuscatedName("f_" + getHashedString(fieldMapping.getSimpleDeobfuscatedName()));
            }
        }

        // Keep non-obfuscated class names as-is
        if (!isObfuscated(classMapping)) {
            return;
        }

        if (classMapping instanceof InnerClassMapping) {
            // Replace inner class names with their hashed name
            classMapping.setDeobfuscatedName("C_" + getHashedString(classMapping.getDeobfuscatedName()));
        }
        else {
            // Replace top level classes with their hashed full name
            classMapping.setDeobfuscatedName(defaultPackage + "/C_" + getHashedString(classMapping.getFullDeobfuscatedName()));
        }
    }

    public MappingSet generate() {
        // Create copy of original set
        MappingSet newMappings = original.copy();

        // Extract duplicate simple names
        Set<String> simpleNames = new HashSet<>();
        Set<String> nonUniqueNames = newMappings.getTopLevelClassMappings().stream()
                .map(ClassMapping::getSimpleDeobfuscatedName).filter(name -> !simpleNames.add(name)).collect(Collectors.toSet());

        // Strip package for unique simple names
        for (TopLevelClassMapping classMapping : newMappings.getTopLevelClassMappings()) {
            //Skip non-obfuscated mappings
            if (!isObfuscated(classMapping)) {
                continue;
            }

            if (!nonUniqueNames.contains(classMapping.getSimpleDeobfuscatedName())) {
                classMapping.setDeobfuscatedName(classMapping.getSimpleDeobfuscatedName());
            }
        }

        // Hash class mappings
        for (TopLevelClassMapping classMapping : newMappings.getTopLevelClassMappings()) {
            hashClassMapping(classMapping);
        }

        return newMappings;
    }

    private boolean isObfuscated(Mapping<?, ?> mapping) {
        // Names with a single character are assumed to be obfuscated
        // Reasoning: A field with name "b" could happen to be obfuscated to "b"
        // Yes, that actually happened
        if (mapping.getDeobfuscatedName().length() == 1) {
            return true;
        }

        // If the mapping is not an identity mapping, the name is obfuscated
        return !mapping.getDeobfuscatedName().equals(mapping.getObfuscatedName());
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

            builder.append((char) ('a' + digit));
        }

        return builder.toString();
    }
}