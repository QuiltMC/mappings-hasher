package org.quiltmc.mappings_hasher;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.InnerClassMapping;
import org.cadixdev.lorenz.model.Mapping;
import org.cadixdev.lorenz.model.TopLevelClassMapping;

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

    public MappingSet generate() {
        MappingSet mappings = MappingSet.create();

        Collection<ClassMapping<?, ?>> incompleteMappings = new HashSet<>(original.getTopLevelClassMappings());
        Set<String> doubledNames = new HashSet<>();

        {
            Set<String> seenNames = new HashSet<>();
            for (ClassMapping<?, ?> classMapping : incompleteMappings) {
                if (seenNames.contains(classMapping.getSimpleDeobfuscatedName())) {
                    doubledNames.add(classMapping.getSimpleDeobfuscatedName());
                } else {
                    seenNames.add(classMapping.getSimpleDeobfuscatedName());
                }
            }
        }

        while (!incompleteMappings.isEmpty()) {
            Collection<ClassMapping<?, ?>> newMappings = new HashSet<>();
            for (ClassMapping<?, ?> oldMapping : incompleteMappings) {
                newMappings.addAll(oldMapping.getInnerClassMappings());

                ClassMapping<?, ?> newMapping;

                if (oldMapping instanceof TopLevelClassMapping) {
                    String hashedName;

                    if (hasMatchingName(oldMapping)) {
                        hashedName = oldMapping.getFullDeobfuscatedName();
                    } else {
                        hashedName = defaultPackage + "/C_";
                        if (doubledNames.contains(oldMapping.getSimpleDeobfuscatedName())) {
                            hashedName += getHashedString(oldMapping.getFullDeobfuscatedName());
                        } else {
                            hashedName += getHashedString(oldMapping.getSimpleDeobfuscatedName());
                        }
                    }

                    newMapping = mappings.createTopLevelClassMapping(oldMapping.getObfuscatedName(), hashedName);
                } else {
                    InnerClassMapping innerClassMapping = (InnerClassMapping) oldMapping;
                    String name = innerClassMapping.getDeobfuscatedName();

                    if (innerClassMapping.getFullDeobfuscatedName().equals(innerClassMapping.getFullObfuscatedName())) {
                        continue;
                    }

                    newMapping = mappings.getOrCreateClassMapping(innerClassMapping.getFullObfuscatedName());
                    newMapping.setDeobfuscatedName("C_" + getHashedString(name));
                }

                oldMapping.getMethodMappings().forEach(methodMapping -> {
                    if (hasMatchingName(methodMapping)) {
                        return;
                    }

                    newMapping.getOrCreateMethodMapping(methodMapping.getObfuscatedName(), methodMapping.getObfuscatedDescriptor()).setDeobfuscatedName("m_" + getHashedString(methodMapping.getDeobfuscatedName()));
                });
                oldMapping.getFieldMappings().forEach(fieldMapping -> {
                    if (hasMatchingName(fieldMapping)) {
                        return;
                    }

                    newMapping.getOrCreateFieldMapping(fieldMapping.getObfuscatedName(), fieldMapping.getType().get()).setDeobfuscatedName("f_" + getHashedString(fieldMapping.getDeobfuscatedName()));
                });
            }
            incompleteMappings = newMappings;
        }
        return mappings;
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

    private boolean hasMatchingName(Mapping<?, ?> mapping) {
        return mapping.getObfuscatedName().length() > 1 && mapping.getDeobfuscatedName().equals(mapping.getObfuscatedName());
    }
}
