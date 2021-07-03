package org.quiltmc.mappings_hasher.asm;

import org.objectweb.asm.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

public class ClassResolver {
    private final Map<String, ClassReader> classToReader = new HashMap<>();
    private final Map<String, Boolean> classToObfuscated = new HashMap<>();
    private final Map<String, ClassInfo> classInfoCache = new HashMap<>();

    public ClassResolver() { }

    public Set<ClassInfo> extractClassInfo(JarFile jar) {
        addJar(jar, true);

        Set<ClassInfo> classes = new HashSet<>();
        jar.stream().forEach(jarEntry -> {
            if (jarEntry.getName().endsWith(".class")) {
                String className = jarEntry.getName().substring(0, jarEntry.getName().lastIndexOf('.'));
                ClassInfo info = getClassInfo(className);
                classes.add(info);
            }
        });

        return classes;
    }

    public void addLibrary(JarFile library) {
        addJar(library, false);
    }

    private void addJar(JarFile jar, boolean obfuscated) {
        jar.stream().forEach(entry -> {
            if (entry.getName().endsWith(".class")) {
                String className = entry.getName().substring(0, entry.getName().lastIndexOf('.'));
                try {
                    classToReader.put(className, new ClassReader(jar.getInputStream(entry)));
                    classToObfuscated.put(className, obfuscated);
                }
                catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            }
        });
    }

    private ClassInfo getClassInfo(String name) {
        if (classInfoCache.containsKey(name)) {
            return classInfoCache.get(name);
        }

        ClassReader reader = classToReader.get(name);
        if (reader == null) {
            try {
                // Try to load from class path (for Java Platform)
                reader = new ClassReader(name);
            }
            catch (IOException exception) {
                throw new RuntimeException("Class not found: " + name);
            }
        }

        boolean obfuscated = classToObfuscated.getOrDefault(name, false);
        ClassVisitor visitor = new ClassVisitor(this);
        if (!obfuscated) {
            visitor.dontObfuscate();
        }
        reader.accept(visitor,ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        classInfoCache.put(name, visitor.getClassInfo());
        return visitor.getClassInfo();
    }

    private static class ClassVisitor extends org.objectweb.asm.ClassVisitor {
        private final ClassResolver resolver;
        private boolean dontObfuscate;

        private ClassInfo classInfo;

        public ClassVisitor(ClassResolver resolver) {
            super(Opcodes.ASM7);
            this.resolver = resolver;
            this.dontObfuscate = false;
        }

        public void dontObfuscate() {
            this.dontObfuscate = true;
        }

        public ClassInfo getClassInfo() {
            return this.classInfo;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.classInfo = new ClassInfo(name, access);

            if (this.dontObfuscate) {
                this.classInfo.dontObfuscate();
            }

            // This is only null for java/lang/Object
            if (superName != null) {
                this.classInfo.superClasses().add(resolver.getClassInfo(superName));
            }

            for (String interfaceName : interfaces) {
                this.classInfo.superClasses().add(resolver.getClassInfo(interfaceName));
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            String annotationClassName = descriptor.substring(1, descriptor.length() - 1);
            this.classInfo.annotations().add(annotationClassName);
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            FieldInfo fieldInfo = new FieldInfo(this.classInfo, name, descriptor);
            classInfo.fields().add(fieldInfo);

            if (this.dontObfuscate) {
                fieldInfo.dontObfuscate();
            }
            return new FieldVisitor(this.api) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    String annotationClassName = descriptor.substring(1, descriptor.length() - 1);
                    fieldInfo.annotations().add(annotationClassName);
                    return null;
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodInfo methodInfo = new MethodInfo(this.classInfo, name, descriptor, access);
            this.classInfo.methods().add(methodInfo);
            if(this.dontObfuscate) {
                methodInfo.dontObfuscate();
            }

            return new MethodVisitor(this.api) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    String annotationClassName = descriptor.substring(1, descriptor.length() - 1);
                    methodInfo.annotations().add(annotationClassName);
                    return null;
                }
            };
        }
    }
}
