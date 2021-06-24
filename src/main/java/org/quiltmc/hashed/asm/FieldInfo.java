package org.quiltmc.hashed.asm;


public class FieldInfo {
    private final ClassInfo owner;
    private final String name;
    private final String descriptor;
    private final boolean obfuscated;

    public FieldInfo(ClassInfo owner, String name, String descriptor, boolean obfuscated) {
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.obfuscated = obfuscated;
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

    public boolean isObfuscated() {
        return obfuscated;
    }
}
