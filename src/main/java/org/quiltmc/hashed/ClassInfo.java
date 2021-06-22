package org.quiltmc.hashed;

import org.cadixdev.bombe.type.FieldType;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.*;

public class ClassInfo {
    public String name;
    public final List<String> superClasses = new ArrayList<>();
    public final Map<String, MethodInfo> methods = new HashMap<>();
    public final List<FieldInfo> fields = new ArrayList<>();

    public String getPackage() {
        return name.substring(0, name.lastIndexOf('/') + 1);
    }

    public static class MethodInfo {
        public ClassInfo owner;
        public String name;
        public String descriptor;
        public Set<MethodInfo> overrides;
        public int access;

        public String fullName() {
            return owner.name + "." + name + descriptor;
        }

        public void resolveOverrides(Map<String, ClassInfo> classes) {
            if (overrides != null) {
                return;
            }

            overrides = new HashSet<>();
            // Private methods can't override other methods
            if ((access & Opcodes.ACC_PRIVATE) != 0) {
                return;
            }
            // Static methods can't override other methods
            if ((access & Opcodes.ACC_STATIC) != 0) {
                return;
            }

            Set<ClassInfo> superToCheck = new HashSet<>();
            for (String className : owner.superClasses) {
                if (classes.containsKey(className)) {
                    superToCheck.add(classes.get(className));
                }
            }

            while (!superToCheck.isEmpty()) {
                Set<ClassInfo> currentSuper = new HashSet<>(superToCheck);
                superToCheck.clear();

                for (ClassInfo classInfo : currentSuper) {
                    // Check for method with same name+desc
                    MethodInfo overrideInfo = classInfo.methods.get(name + descriptor);
                    if (overrideInfo != null) {
                        overrideInfo.resolveOverrides(classes);
                        boolean canOverride;
                        // Can't override static methods
                        if ((overrideInfo.access & Opcodes.ACC_STATIC) != 0) {
                            canOverride = false;
                        }
                        // Can override protected methods
                        else if ((overrideInfo.access & Opcodes.ACC_PUBLIC) != 0) {
                            canOverride = true;
                        }
                        // Can override protected methods
                        else if ((overrideInfo.access & Opcodes.ACC_PROTECTED) != 0) {
                            canOverride = true;
                        }
                        // Can't override private methods
                        else if ((overrideInfo.access & Opcodes.ACC_PRIVATE) != 0) {
                            canOverride = false;
                        }
                        // Can override package private methods if in same package
                        else if (classInfo.getPackage().equals(owner.getPackage())) {
                            canOverride = true;
                        }
                        else {
                            canOverride = false;
                        }

                        if (canOverride) {
                            Set<MethodInfo> superOverrides = overrideInfo.overrides;
                            if (superOverrides.isEmpty()) {
                                overrides.add(overrideInfo);
                            }
                            else {
                                overrides.addAll(overrideInfo.overrides);
                            }
                        }
                        else {
                            for (String className : classInfo.superClasses) {
                                if (classes.containsKey(className)) {
                                    superToCheck.add(classes.get(className));
                                }
                            }
                        }
                    }
                    else {
                        for (String className : classInfo.superClasses) {
                            if (classes.containsKey(className)) {
                                superToCheck.add(classes.get(className));
                            }
                        }
                    }
                }
            }
        }
    }

    public static class FieldInfo {
        public ClassInfo owner;
        public String name;
        public String descriptor;
    }

    public static class ClassInfoVisitor extends ClassVisitor {
        private final ClassInfo info = new ClassInfo();

        public ClassInfoVisitor() {
            super(Opcodes.ASM7);
        }

        public ClassInfo getClassInfo () {
            return info;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[]interfaces) {
            info.name = name;
            info.superClasses.add(superName);
            info.superClasses.addAll(Arrays.asList(interfaces));

            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions){
            MethodInfo method = new MethodInfo();
            method.owner = info;
            method.name = name;
            method.descriptor = descriptor;
            method.access = access;
            info.methods.put(name + descriptor, method);

            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value){
            FieldInfo field = new FieldInfo();
            field.owner = info;
            field.name = name;
            field.descriptor = descriptor;
            info.fields.add(field);

            return super.visitField(access, name, descriptor, signature, value);
        }
    }
}
