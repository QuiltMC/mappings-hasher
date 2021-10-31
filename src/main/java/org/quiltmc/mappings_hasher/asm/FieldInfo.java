package org.quiltmc.mappings_hasher.asm;


import java.util.HashSet;
import java.util.Set;

public class FieldInfo {
    private final ClassInfo owner;
    private final String name;
    private final String descriptor;

    public FieldInfo(ClassInfo owner, String name, String descriptor) {
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
    }

    public ClassInfo owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public String descriptor() {
        return descriptor;
    }
}
