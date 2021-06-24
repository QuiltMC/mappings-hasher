package org.quiltmc.hashed.asm;

import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.Set;

public class MethodInfo {
    private final ClassInfo owner;
    private final String name;
    private final String descriptor;
    private final int access;
    boolean obfuscated;

    private final Set<MethodInfo> superMethods = new HashSet<>();

    public MethodInfo(ClassInfo owner, String name, String descriptor, int access, boolean obfuscated) {
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.access = access;
        this.obfuscated = obfuscated;

        // Check which methods this method overrides
        this.resolveOverrides();
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

    public int access() {
        return access;
    }

    public boolean isObfuscated() {
        return obfuscated;
    }

    public Set<MethodInfo> superMethods() {
        return superMethods;
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

    private void resolveOverrides() {
        // Static methods can't override
        if (this.isStatic()) {
            return;
        }

        // Private methods can't override
        if (this.isPrivate()) {
            return;
        }

        // Recursively check super classes
        Set<ClassInfo> superToCheck = new HashSet<>(owner.superClasses().values());
        while (!superToCheck.isEmpty()) {
            Set<ClassInfo> currentSupers = new HashSet<>(superToCheck);
            superToCheck.clear();
            for (ClassInfo superClass : currentSupers) {
                // Check for properly named method
                MethodInfo superMethod = superClass.methods().get(name + descriptor);

                // Can't override non-existing or static methods
                if (superMethod != null && !superMethod.isStatic()) {
                    // Can override public and protected methods, and non-private methods in same package
                    if (superMethod.isPublic() || superMethod.isProtected() ||
                            !superMethod.isPrivate() && owner.getPackage().equals(superClass.getPackage())) {
                        if (!superMethod.superMethods.isEmpty()) {
                            superMethods.addAll(superMethod.superMethods);
                        }
                        else {
                            superMethods.add(superMethod);
                        }
                        continue;
                    }
                }

                // If no match, check in super classes
                superToCheck.addAll(superClass.superClasses().values());
            }
        }
    }
}
