package org.quiltmc.hashed.asm;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ClassInfo {
    private final String name;
    private final int access;
    private boolean obfuscated;

    private final Map<String, ClassInfo> superClasses = new HashMap<>();
    private final Map<String, MethodInfo> methods = new HashMap<>();
    private final Map<String, FieldInfo> fields = new HashMap<>();

    public ClassInfo(String name, int access, boolean obfuscated) {
        this.name = name;
        this.access = access;
        this.obfuscated = obfuscated;
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

    public Map<String, ClassInfo> superClasses() {
        return superClasses;
    }

    public Map<String, MethodInfo> methods() {
        return methods;
    }

    public Map<String, FieldInfo> fields() {
        return fields;
    }

    public boolean isObfuscated() {
        return obfuscated;
    }

    public static class ClassVisitor extends org.objectweb.asm.ClassVisitor {
        private final ClassResolver resolver;
        private boolean obfuscated;
        private final Set<String> dontObfuscateAnnotations;
        private final Set<String> dontObfuscateClassAnnotations;

        private ClassInfo classInfo;

        public ClassVisitor(ClassResolver resolver, boolean obfuscated, Set<String> dontObfuscateAnnotations, Set<String> dontObfuscateClassAnnotations) {
            super(Opcodes.ASM7);
            this.resolver = resolver;
            this.obfuscated = obfuscated;
            this.dontObfuscateAnnotations = dontObfuscateAnnotations;
            this.dontObfuscateClassAnnotations = dontObfuscateClassAnnotations;
        }

        public ClassInfo getClassInfo() {
            return classInfo;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.classInfo = new ClassInfo(name, access, obfuscated);

            if (superName != null) {
                classInfo.superClasses.put(superName, resolver.getClassInfo(superName));
            }

            for (String interfaceName : interfaces) {
                classInfo.superClasses.put(interfaceName, resolver.getClassInfo(interfaceName));
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            String className = descriptor.substring(1, descriptor.length() - 1);
            if (dontObfuscateAnnotations.contains(className)) {
                classInfo.obfuscated = false;
            }
            if (dontObfuscateClassAnnotations.contains(className)) {
                this.obfuscated = false;
                classInfo.obfuscated = false;
            }
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            FieldInfo fieldInfo = new FieldInfo(classInfo, name, descriptor, obfuscated);
            classInfo.fields.put(name + descriptor, fieldInfo);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodInfo methodInfo = new MethodInfo(classInfo, name, descriptor, access, obfuscated);
            classInfo.methods.put(name + descriptor, methodInfo);

            return new MethodVisitor(Opcodes.ASM7) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    String className = descriptor.substring(1, descriptor.length() - 1);
                    if (dontObfuscateAnnotations.contains(className)) {
                        classInfo.obfuscated = false;
                        methodInfo.obfuscated = false;
                    }
                    return null;
                };
            };
        }
    }
}
