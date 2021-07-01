package org.quiltmc.hashed.asm;

import java.util.HashSet;
import java.util.Set;

public class ClassInfo {
    private final String name;
    private final int access;
    private boolean obfuscated;

    private final Set<ClassInfo> superClasses = new HashSet<>();
    private final Set<MethodInfo> methods = new HashSet<>();
    private final Set<FieldInfo> fields = new HashSet<>();

    private final Set<String> annotations = new HashSet<>();

    public ClassInfo(String name, int access) {
        this.name = name;
        this.access = access;
        this.obfuscated = true;
    }

    public String name() {
        return name;
    }

    public int access() {
        return access;
    }

    public String getPackage() {
        int index = name.lastIndexOf('/');
        return index == -1 ? "" : name.substring(0, index);
    }

    public Set<ClassInfo> superClasses() {
        return superClasses;
    }

    public Set<MethodInfo> methods() {
        return methods;
    }

    public Set<FieldInfo> fields() {
        return fields;
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
