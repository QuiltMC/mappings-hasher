# Mappings Hasher
This is a tool that creates a hashed mapping from a given mapping.
There are multiple reasons why you'd want to use this mapping rather than the raw mappings:
- If the raw mappings are under a more restrictive license,
  the hashed mappings contain only very little information about them.
  Keep in mind that this doesn't necessarily mean you'll have rights to the hashed mappings.
  I am not a lawyer.
- The hashed mapping are likely more stable than the original ones.
  This is mostly because of built-in hardening against package renames and descriptor changes.
- The hashed mappings look distinct from the original mappings.
  This is useful if you want to use the hashed mappings as a base for another mapping set.
- You can always create these mappings from scratch and automatically.
  Other intermediary mappings need to be kept updated manually starting at some base version.

## Generating mappings
The repository as is, is designed for generating hashed Minecraft mappings.
You simply invoke the jar with the version id from the version manifest as the argument.
For example, in order to create the hashed mappings for the first release candidate for 1.17.1 you run:

    java <jar-file> 1.17.1-rc1

This will download the mappings, the client jar and all required libraries and cache them for future runs.
Then it will create the output mappings in `mappings/hashed-<version>.tiny`.

## Hashing rules
This section describes how the program generates the hashed names.

### Hash format
The generated hashed names are of the following form:
- Classes: `C_<hash>`
- Methods: `m_<hash>`
- Fields: `f_<hash>`

The `<hash>` are the least significant 8 characters of the base-26 representation of the members "raw name".

If a class, method or field is unobfuscated, no mapping is generated.

### Raw names
The raw name is string created from the original mapping.
The formatting was chosen in a way to harden against instability in the original raw mapping set.

#### Classes
The raw name of a class is its full name from the original mapping, e.g. `net/example/Outer$Inner`.
The use of slashed names is natural since that is how the class names are in JVM bytecode.

In order to harden the mapping set against package names, the package name is stripped wherever possible.
If the simple class name is unique in the provided jar, the raw name is instead its simple name, e.g. `Outer$Inner`.
Conversely, if there is a class `net/example/A` and a class `net/test/A`, the full name must be used.

If the class is unobfuscated, that means annotated as such, the raw name equals its name in the original mapping.

#### Methods
The raw name of a method is `m;<raw-class-name>.<method-name>;<method-descriptor>`.
The `<raw-class-name>` is described above, the method name and descriptor are taken from the original mapping.
The `m;` prefix is required since methods and fields look otherwise identical if the descriptor is stripped.

In order to harden the mapping set against descriptor changes, the descriptor is stripped wherever possible.
If the method name is unique in its containing class, the descriptor in the raw name is left empty.

If the method is unobfuscated, that means annotated as such, the raw name equals its name in the original mapping.
The special method names "<init>" and "<clinit>" are also treated as unobfuscated.

Methods require additional logic since they can override methods in other classes.
If a method overrides another, it is required that their raw names are equal.
If a method overrides *multiple* other methods, they all need to have the same name.
For this reason, a "name set" is formed.
This set contains the raw names of all "top level methods" that need to have the same name.
"Top level" means that those methods don't override another method.
From this set the lexically biggest name is chosen and used as the raw name for all other methods in the set.

#### Fields
The raw name of a field is `f;<raw-class-name>.<field-name>;<field-descriptor>`.
The `<raw-class-name>` is described above, the field name and descriptor are taken from the original mapping.
The `f;` prefix is required since methods and fields look otherwise identical if the descriptor is stripped.

In order to harden the mapping set against descriptor changes, the descriptor is stripped wherever possible.
If the field name is unique in its containing class, the descriptor in the raw name is left empty.

> Note that while java doesn't allow fields to only differ in their descriptor, the JVM does.

## Inner workings
Internally, the `MappingsHasher` class does most of the heavy-lifting.
It is supplied with a `MappingSet` and a default package for all obfuscated classes.

Next, all the libraries have to be added via `MappingsHasher.addLibrary(JarFile library)`.
This is necessary in order for the hasher to correctly detect when methods override library methods.
The hasher will treat these methods as unobfuscated.

One can specify additional annotations that mark classes, methods and fields as unobfuscated.
Annotating a method, field or nested class will also mark the containing class as unobfuscated.
Annotating a class will mark all its methods and fields (but not nested classes) as unobfuscated.
Note that these annotations should be specified in their hashed form.

Finally, invoking `MappingsHasher.generate(JarFile jar)` with the obfuscated jar will return a new `MappingSet`.
First, it extracts all the class and member information from the jar,
while recursively resolving super classes and interfaces.
This considers all provided jars, as well as the classpath (mostly for the java platform).
Then the hasher iterates all classes and their members again and creates the hashe mappings.

