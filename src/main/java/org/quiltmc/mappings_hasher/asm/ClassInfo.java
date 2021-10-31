package org.quiltmc.mappings_hasher.asm;

import java.util.HashSet;
import java.util.Set;

public class ClassInfo {
    private final String name;
    private final int access;

    private final Set<ClassInfo> superClasses = new HashSet<>();
    private final Set<MethodInfo> methods = new HashSet<>();
    private final Set<FieldInfo> fields = new HashSet<>();

    public ClassInfo(String name, int access) {
        this.name = name;
        this.access = access;
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
}
