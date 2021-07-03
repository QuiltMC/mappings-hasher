package org.quiltmc.mappings_hasher.manifest;

import org.quiltmc.json5.JsonReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VersionEntry implements IWebResource {
    private String id;
    private ReleaseType type;
    private URL url;
    private String sha1;

    private Map<String, DownloadEntry> downloads;
    private List<LibraryEntry> libraries;

    private VersionEntry() { }

    public static VersionEntry fromJson(JsonReader reader) throws IOException {
        VersionEntry version = new VersionEntry();
        version.parseJson(reader);
        return version;
    }

    public void resolve() throws IOException {
        InputStream versionReader = Files.newInputStream(this.getOrDownload().toPath());
        JsonReader versionJson = JsonReader.json(new BufferedReader(new InputStreamReader(versionReader)));
        this.parseJson(versionJson);
        versionReader.close();
    }

    public String id() {
        return id;
    }

    public ReleaseType type() {
        return type;
    }

    public Map<String, DownloadEntry> downloads() {
        return downloads;
    }

    public List<LibraryEntry> libraries() {
        return libraries;
    }

    private void parseJson(JsonReader reader) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "id":
                    id = reader.nextString();
                    break;
                case "downloads":
                    parseDownloads(reader);
                    break;
                case "type":
                    type = ReleaseType.fromString(reader.nextString());
                    break;
                case "url":
                    url =  new URL(reader.nextString());
                    break;
                case "sha1":
                    sha1 =  reader.nextString();
                    break;
                case "libraries":
                    parseLibraries(reader);
                    break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
    }

    private void parseDownloads(JsonReader reader) throws IOException {
        if (downloads == null) {
            downloads = new HashMap<>();
        }

        reader.beginObject();
        while (reader.hasNext()) {
            String downloadName = reader.nextName();
            String filename;
            if (downloadName.endsWith("_mappings")) {
                filename = downloadName + ".txt";
            }
            else {
                filename = downloadName + ".jar";
            }
            filename = filename.substring(filename.lastIndexOf('/') + 1);
            DownloadEntry download = DownloadEntry.fromJson(reader, Paths.get("versions", id, filename));
            downloads.put(downloadName, download);
        }
        reader.endObject();
    }

    private void parseLibraries(JsonReader reader) throws IOException {
        if (libraries == null) {
            libraries = new ArrayList<>();
        }

        reader.beginArray();
        while (reader.hasNext()) {
            LibraryEntry lib = LibraryEntry.fromJson(reader);
            if (lib.isAllowed()) {
                libraries.add(lib);
            }
        }
        reader.endArray();
    }

    @Override
    public String sha1() {
        return sha1;
    }

    @Override
    public URL url() {
        return url;
    }

    @Override
    public Path path() {
        return Paths.get("versions", id, id + ".json");
    }
}
