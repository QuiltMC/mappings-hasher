package org.quiltmc.hashed;

import org.quiltmc.json5.JsonReader;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class Version {
    private final String id;
    private final Map<String, Download> downloads;

    public Version(String id, Map<String, Download> downloads) {
        this.id = id;
        this.downloads = downloads;
    }

    public String id() {
        return id;
    }

    public Map<String, Download> downloads() {
        return downloads;
    }

    public static Version fromJson(JsonReader reader) throws IOException {
        Map<String, Download> downloads = new HashMap<>();
        String id = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "id":
                    id = reader.nextString();
                    break;
                case "downloads":
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String downloadName = reader.nextName();
                        Download download = Download.fromJson(reader);
                        downloads.put(downloadName, download);
                    }
                    reader.endObject();
                    break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();

        return new Version(id, downloads);
    }

    public static class Download {
        private final URL url;
        private final String sha1;
        private final int size;

        public Download(URL url, int size, String sha1) {
            this.url = url;
            this.size = size;
            this.sha1 = sha1;
        }

        public URL url() {
            return url;
        }

        public String sha1() {
            return sha1;
        }

        public int size() {
            return size;
        }

        public static Download fromJson(JsonReader reader) throws IOException {
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

            return new Download(url, size, sha1);
        }
    }
}
