package org.quiltmc.hashed.asm;


import java.util.HashSet;
import java.util.Set;

public class FieldInfo {
    private final ClassInfo owner;
    private final String name;
    private final String descriptor;
    private boolean obfuscated;

    private final Set<String> annotations = new HashSet<>();

    public FieldInfo(ClassInfo owner, String name, String descriptor) {
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.obfuscated = true;
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

    public Set<String> annotations() {
        return annotations;
    }

    public void dontObfuscate() {
        this.obfuscated = false;
    }

    public boolean isObfuscated() {
        return obfuscated;
    }
}
