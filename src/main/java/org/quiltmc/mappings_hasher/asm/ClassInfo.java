package org.quiltmc.mappings_hasher.asm;

import java.util.*;
import java.util.stream.Collectors;

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

    public boolean isSubClassOf(ClassInfo superClass) {
        return superClass == this || superClasses.contains(superClass)
                || superClasses.stream().anyMatch(s -> s.isSubClassOf(superClass));
    }

    public boolean canInherit(MethodInfo methodInfo) {
        if (methodInfo.isStatic() || methodInfo.isPrivate() || !this.isSubClassOf(methodInfo.owner())) {
            return false;
        }

        return methodInfo.isPublic() || methodInfo.isProtected()
                || methodInfo.owner().getPackage().equals(this.getPackage());
    }

    private Set<MethodInfo> getAllMethods() {
        Set<MethodInfo> methods = new HashSet<>(this.methods);
        for (ClassInfo superClass : superClasses) {
            methods.addAll(superClass.getAllMethods());
        }
        return methods;
    }

    public void finish() {
        Map<String, List<MethodInfo>> visibleMethodsByFullName = new HashMap<>();
        for (MethodInfo method : getAllMethods()) {
            if (canInherit(method)) {
                visibleMethodsByFullName
                        .computeIfAbsent(method.name() + method.descriptor(), m -> new ArrayList<>()).add(method);
            }
        }

        for (List<MethodInfo> nameSet : visibleMethodsByFullName.values()) {
            MethodInfo first = nameSet.get(0);
            for (MethodInfo method : nameSet) {
                first.mergeNameSetWith(method);
            }
        }
    }
}
