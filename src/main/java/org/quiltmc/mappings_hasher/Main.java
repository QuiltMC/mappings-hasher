package org.quiltmc.mappings_hasher;

import net.fabricmc.lorenztiny.TinyMappingsWriter;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.TextMappingsReader;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import org.quiltmc.launchermeta.version.v1.DownloadableFile;
import org.quiltmc.launchermeta.version.v1.Library;
import org.quiltmc.launchermeta.version.v1.Rule;
import org.quiltmc.launchermeta.version.v1.Version;
import org.quiltmc.launchermeta.version_manifest.VersionEntry;
import org.quiltmc.launchermeta.version_manifest.VersionManifest;
import org.quiltmc.mappings_hasher.util.CachingFileDownloader;
import org.quiltmc.mappings_hasher.util.FileDownloader;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Command(name = "mappings-hasher")
public class Main implements Callable<Integer> {
    static class VersionSource {
        @Option(names = "--zip")
        private URL zipUrl;

        @Option(names = "--json")
        private URL jsonUrl;

        @Option(names = "--version")
        private String version;
    }

    @ArgGroup(multiplicity = "1")
    private VersionSource versionSource;

    @Option(names = "--out")
    private Path outFile;

    @Option(names = "--cache")
    private Path cacheDir;

    @Override
    public Integer call() throws IOException {
        InputStreamReader reader;

        if (versionSource.zipUrl != null) {
            System.out.println("Reading zip file...");
            ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(versionSource.zipUrl.openStream()));
            ZipEntry nextEntry = zipInputStream.getNextEntry();
            while (nextEntry != null) {
                if (nextEntry.getName().endsWith(".json")) {
                    break;
                }
                nextEntry = zipInputStream.getNextEntry();
            }

            if (nextEntry == null) {
                throw new RuntimeException("Couldn't find manifest in specified zip file...");
            }

            reader = new InputStreamReader(zipInputStream);
        }
        else if (versionSource.version != null) {
            URL manifestUrl = new URL("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json");
            InputStreamReader manifestReader = new InputStreamReader(new BufferedInputStream(manifestUrl.openStream()));
            VersionManifest manifest = VersionManifest.fromReader(manifestReader);
            Optional<VersionEntry> entry = manifest.getVersions().stream().filter(e -> e.getId().equals(versionSource.version)).findAny();
            if (entry.isPresent()) {
                reader = new InputStreamReader(new BufferedInputStream(new URL(entry.get().getUrl()).openStream()));
            }
            else {
                throw new RuntimeException("Version doesn't exist...");
            }
        }
        else if (versionSource.jsonUrl != null) {
            reader = new InputStreamReader(new BufferedInputStream(versionSource.jsonUrl.openStream()));
        }
        else {
            throw new RuntimeException("No version source specified");
        }

        System.out.println("Reading manifest...");
        Version version = Version.fromReader(reader);

        DownloadableFile clientJarDownload = version.getDownloads().getClient();
        DownloadableFile clientMappingsDownload = version.getDownloads().getClientMappings()
                .orElseThrow(() -> new RuntimeException("There exist no mappings for this version"));

        List<DownloadableFile> libraryDownloads = new ArrayList<>();
        for (Library library : version.getLibraries()) {
            // TODO: Proper rule parsing?
            if (library.getRules() != null && !library.getRules().isEmpty()) {
                boolean allowed = false;
                for (Rule rule : library.getRules()) {
                    if (rule.getAction().equals("allow")) {
                        if (!rule.getOs().isPresent()) {
                            allowed = true;
                        }
                        else if (rule.getOs().get().getName().equals(Optional.of("linux"))) {
                            allowed = true;
                        }
                    }
                    else if (rule.getAction().equals("disallow")) {
                        if (!rule.getOs().isPresent()) {
                            allowed = false;
                        }
                        else if (rule.getOs().get().getName().equals(Optional.of("linux"))) {
                            allowed = false;
                        }
                    }
                }

                if (!allowed) {
                    continue;
                }
            }

            library.getDownloads().getArtifact().ifPresent(libraryDownloads::add);
        }

        System.out.println("Downloading files...");
        FileDownloader downloader = new FileDownloader();
        if (cacheDir != null) {
            downloader = new CachingFileDownloader(cacheDir);
        }

        Path clientJar = downloader.download(clientJarDownload);
        Path clientMappings = downloader.download(clientMappingsDownload);
        List<Path> libraries = new ArrayList<>();
        for (DownloadableFile libraryDownload : libraryDownloads) {
            libraries.add(downloader.download(libraryDownload));
        }

        System.out.println("Reading mappings...");
        BufferedReader clientMappingsReader = Files.newBufferedReader(clientMappings);
        TextMappingsReader mappingsReader = new ProGuardReader(clientMappingsReader);
        MappingSet clientMappingsSet = mappingsReader.read().reverse();

        MappingsHasher mappingsHasher = new MappingsHasher(clientMappingsSet, "net/minecraft/unmapped");

        System.out.println("Reading libraries...");
        for (Path library : libraries) {
            mappingsHasher.addLibrary(new JarFile(library.toFile()));
        }

        System.out.println("Generating mappings...");
        MappingSet mappingSet = mappingsHasher.generate(new JarFile(clientJar.toFile()), classInfo -> true);

        if (outFile == null) {
            outFile = Paths.get("mappings", "hashed-" + version.getId() + ".tiny");
        }

        System.out.println("Writing mappings...");
        Files.deleteIfExists(outFile);
        Files.createDirectories(outFile.getParent());
        BufferedWriter mappingsWriter = Files.newBufferedWriter(outFile);
        new TinyMappingsWriter(mappingsWriter, "official", "hashed").write(mappingSet);
        mappingsWriter.flush();
        mappingsWriter.close();

        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
