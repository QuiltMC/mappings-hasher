package org.quiltmc.hashed.manifest;

import org.quiltmc.json5.JsonReader;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LibraryEntry implements IWebResource {
    private Path path;
    private String sha1;
    private URL url;

    private boolean allowed = true;

    private LibraryEntry() { }

    public static LibraryEntry fromJson(JsonReader reader) throws IOException {
        LibraryEntry lib = new LibraryEntry();
        lib.parseJson(reader);
        return lib;
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
        return path;
    }

    public boolean isAllowed() {
        return allowed;
    }

    private void parseJson(JsonReader reader) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "downloads":
                    parseDownloads(reader);
                    break;
                case "rules":
                    parseRules(reader);
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();
    }

    private void parseDownloads(JsonReader reader) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "artifact":
                    parseArtifact(reader);
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();
    }

    private void parseArtifact(JsonReader reader) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "path":
                    path = Paths.get("lib", reader.nextString());
                    break;
                case "sha1":
                    sha1 = reader.nextString();
                    break;
                case "url":
                    url = new URL(reader.nextString());
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();
    }

    private void parseRules(JsonReader reader) throws IOException {
        allowed = false;

        reader.beginArray();
        while (reader.hasNext()) {
            boolean temp_allowed = false;
            boolean temp_os = true;

            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                switch (name) {
                    case "action":
                        String action = reader.nextString();
                        if (action.equals("allow")) {
                            temp_allowed = true;
                        }
                        else if (action.equals("disallow")) {
                            temp_allowed = false;
                        }
                        break;
                    case "os":
                        reader.beginObject();
                        while (reader.hasNext()) {
                            name = reader.nextName();
                            if (name.equals("name")) {
                                temp_os = reader.nextString().equals("linux");
                            }
                            else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();

            if (temp_os) {
                if (temp_allowed) {
                    allowed = true;
                }
            }
        }
        reader.endArray();
    }
}
