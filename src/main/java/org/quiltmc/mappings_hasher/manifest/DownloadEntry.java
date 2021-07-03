package org.quiltmc.mappings_hasher.manifest;

import org.quiltmc.json5.JsonReader;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

public class DownloadEntry implements IWebResource {
    private final URL url;
    private final String sha1;
    private final int size;
    private final Path path;

    public DownloadEntry(URL url, int size, String sha1, Path path) {
        this.url = url;
        this.size = size;
        this.sha1 = sha1;
        this.path = path;
    }

    @Override
    public URL url() {
        return url;
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public String sha1() {
        return sha1;
    }

    public int size() {
        return size;
    }

    public static DownloadEntry fromJson(JsonReader reader, Path path) throws IOException {
        URL url = null;
        String sha1 = null;
        int size = -1;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "url":
                    url = new URL(reader.nextString());
                    break;
                case "sha1":
                    sha1 = reader.nextString();
                    break;
                case "size":
                    size = reader.nextInt();
                    break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();

        return new DownloadEntry(url, size, sha1, path);
    }
}
