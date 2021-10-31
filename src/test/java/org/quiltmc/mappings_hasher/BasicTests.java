package org.quiltmc.mappings_hasher;

import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import org.junit.jupiter.api.*;
import org.quiltmc.json5.JsonReader;
import org.quiltmc.mappings_hasher.Main;
import org.quiltmc.mappings_hasher.manifest.LibraryEntry;
import org.quiltmc.mappings_hasher.manifest.VersionEntry;
import org.quiltmc.mappings_hasher.manifest.VersionManifest;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BasicTests {
    @Test
    @Order(1)
    public void hash_1_17_1() throws IOException {
        Main.main("1.17.1");
    }

    @Test
    @Order(2)
    public void remap_1_17_1() throws IOException {
        Path input = Paths.get("cache", "versions", "1.17.1", "client.jar");
        Path output = Paths.get("mappings", "remapped.jar");

        Files.deleteIfExists(output);

        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(TinyUtils.createTinyMappingProvider(Paths.get("mappings", "hashed-1.17.1.tiny"), "official", "hashed"))
                .rebuildSourceFilenames(true)
                .ignoreConflicts(true).build();

        OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build();
        outputConsumer.addNonClassFiles(input, NonClassCopyMode.FIX_META_INF, remapper);
        remapper.readInputs(input);


        URL manifestUrl = new URL("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json");
        InputStreamReader manifestReader = new InputStreamReader(manifestUrl.openConnection().getInputStream());
        JsonReader manifestJson = JsonReader.json(new BufferedReader(manifestReader));
        VersionManifest manifest = VersionManifest.fromJson(manifestJson);
        VersionEntry version = manifest.versions().get("1.17.1");
        version.resolve();

        List<Path> libs = new ArrayList<>();
        for (LibraryEntry lib : version.libraries()) {
            libs.add(lib.getOrDownload().toPath());
        }

        remapper.readClassPath(libs.toArray(new Path[0]));
        remapper.apply(outputConsumer);
        outputConsumer.close();
        remapper.finish();
    }

    @Test
    @Order(3)
    public void load_1_17_1() throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        URL manifestUrl = new URL("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json");
        InputStreamReader manifestReader = new InputStreamReader(manifestUrl.openConnection().getInputStream());
        JsonReader manifestJson = JsonReader.json(new BufferedReader(manifestReader));
        VersionManifest manifest = VersionManifest.fromJson(manifestJson);
        VersionEntry version = manifest.versions().get("1.17.1");
        version.resolve();

        List<URL> jars = new ArrayList<>();
        for (LibraryEntry lib : version.libraries()) {
            jars.add(lib.getOrDownload().toURI().toURL());
        }

        File clientJarFile = new File("mappings/remapped.jar");
        jars.add(clientJarFile.toURI().toURL());

        ClassLoader loader = new URLClassLoader(jars.toArray(new URL[0]));

        JarFile clientJar = new JarFile(clientJarFile);
        List<String> classNames = clientJar.stream().map(ZipEntry::getName).filter(name -> name.endsWith(".class"))
                .map(name -> name.substring(0, name.length() - 6)).collect(Collectors.toList());

        Class<?> dataMain = loader.loadClass("net.minecraft.data.Main");
        dataMain.getMethod("main", String[].class).invoke(null, (Object)new String[] {"--all"});

        for (String name : classNames) {
            Class.forName(name.replace('/', '.'), false, loader);
        }
    }
}
