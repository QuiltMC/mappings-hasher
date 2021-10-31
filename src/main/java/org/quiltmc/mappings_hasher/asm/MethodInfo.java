package org.quiltmc.mappings_hasher.asm;

import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.Set;

public class MethodInfo {
    private final ClassInfo owner;
    private final String name;
    private final String descriptor;
    private final int access;

    private final Set<MethodInfo> overrides;

    public MethodInfo(ClassInfo owner, String name, String descriptor, int access) {
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.access = access;

        // Check which methods this method overrides
        this.overrides = computeOverrides();
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

    public Set<MethodInfo> overrides() {
        return overrides;
    }

    public String getFullName() {
        return owner.name() + "/" + name + descriptor;
    }

    public boolean isStatic() {
        return (access & Opcodes.ACC_STATIC) != 0;
    }

    public boolean isPublic() {
        return (access & Opcodes.ACC_PUBLIC) != 0;
    }

    public boolean isProtected() {
        return (access & Opcodes.ACC_PROTECTED) != 0;
    }

    public boolean isPrivate() {
        return (access & Opcodes.ACC_PRIVATE) != 0;
    }

    private Set<MethodInfo> computeOverrides() {
        Set<MethodInfo> overrides = new HashSet<>();

        // Static methods can't override
        if (this.isStatic()) {
            return overrides;
        }

        // Private methods can't override
        if (this.isPrivate()) {
            return overrides;
        }

        // Recursively check super classes
        Set<ClassInfo> superToCheck = new HashSet<>(owner.superClasses());
        while (!superToCheck.isEmpty()) {
            Set<ClassInfo> currentSupers = new HashSet<>(superToCheck);
            superToCheck.clear();

            for (ClassInfo superClass : currentSupers) {
                // Check for properly named method in super class
                MethodInfo superMethod = superClass.methods().stream()
                        .filter(m -> m.name.equals(name) && m.descriptor.equals(descriptor))
                        .findFirst().orElse(null);

                // Only allow instance methods
                if (superMethod != null && !superMethod.isStatic()) {
                    // Can override public and protected methods, and non-private methods in same package
                    if (superMethod.isPublic() || superMethod.isProtected() ||
                            !superMethod.isPrivate() && owner.getPackage().equals(superClass.getPackage())) {
                        // Direct override
                        overrides.add(superMethod);

                        // Indirect overrides
                        overrides.addAll(superMethod.overrides);

                        // If override was found, no need to check further super classes
                        continue;
                    }
                }

                // If no match, check in super classes
                superToCheck.addAll(superClass.superClasses());
            }
        }

        return overrides;
    }
}
