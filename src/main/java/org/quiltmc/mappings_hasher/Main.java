package org.quiltmc.mappings_hasher;

import net.fabricmc.lorenztiny.TinyMappingsWriter;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.TextMappingsReader;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.quiltmc.mappings_hasher.manifest.LibraryEntry;
import org.quiltmc.mappings_hasher.manifest.VersionEntry;
import org.quiltmc.mappings_hasher.manifest.VersionManifest;
import org.quiltmc.json5.JsonReader;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: <command> <version>");
            return;
        }

        System.out.println("Reading version manifest...");
        URL manifestUrl = new URL("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json");
        InputStreamReader manifestReader = new InputStreamReader(manifestUrl.openConnection().getInputStream());
        JsonReader manifestJson = JsonReader.json(new BufferedReader(manifestReader));
        VersionManifest manifest = VersionManifest.fromJson(manifestJson);

        System.out.println("Reading version...");
        VersionEntry version = manifest.versions().get(args[0]);
        if (version == null) {
            System.out.println("Unknown version: " + args[0]);
            return;
        }

        version.resolve();
        if (!version.downloads().containsKey("client_mappings")) {
            System.out.println("There exist no Mojang provided mappings for this version");
            return;
        }

        System.out.println("Loading Mojang mappings...");
        File mojmapFile = version.downloads().get("client_mappings").getOrDownload();
        InputStream mojmapStream = Files.newInputStream(mojmapFile.toPath());
        TextMappingsReader mappingsReader = new ProGuardReader(new InputStreamReader(mojmapStream));
        MappingSet obf_to_mojmap = mappingsReader.read().reverse();
        MappingsHasher mappingsHasher = new MappingsHasher(obf_to_mojmap, "net/minecraft/unmapped");

        System.out.println("Loading libs...");
        for (LibraryEntry lib : version.libraries()) {
            JarFile libJar = new JarFile(lib.getOrDownload());
            mappingsHasher.addLibrary(libJar);
        }

        System.out.println("Loading client jar...");
        JarFile clientJar = new JarFile(version.downloads().get("client").getOrDownload());

        System.out.println("Generating mappings...");
        mappingsHasher.addDontObfuscateAnnotation("net/minecraft/unmapped/C_qwuptkcl", true);
        mappingsHasher.addDontObfuscateAnnotation("net/minecraft/unmapped/C_prlazzma", true);
        MappingSet obf_to_hashed = mappingsHasher.generate(clientJar);

        System.out.println("Writing mappings to file...");
        Path outPath = Paths.get("mappings", "hashed-" + version.id() + ".tiny");
        Files.createDirectories(outPath.getParent());
        Files.deleteIfExists(outPath);
        Files.createFile(outPath);

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(outPath)));
        TinyMappingsWriter mappingsWriter = new TinyMappingsWriter(writer, "official", "hashed");
        mappingsWriter.write(obf_to_hashed);
        writer.flush();
        writer.close();
    }
}
