package org.quiltmc.hashed;

import org.quiltmc.json5.JsonReader;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class VersionManifest {
    private final Latest latest;
    private final List<Version> versions;

    private VersionManifest(Latest latest, List<Version> versions) {
        this.latest = latest;
        this.versions = versions;
    }

    public Latest latest() {
        return latest;
    }

    public List<Version> versions() {
        return versions;
    }

    public static VersionManifest fromJson(JsonReader reader) throws IOException {
        Latest latest = null;
        List<Version> versions = new ArrayList<>();

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "latest":
                    latest = Latest.fromJson(reader);
                    break;
                case "versions":
                    reader.beginArray();
                    while (reader.hasNext()) {
                        Version version = Version.fromJson(reader);
                        versions.add(version);
                    }
                    reader.endArray();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        return new VersionManifest(latest, versions);
    }

    public static class Version {
        private final String id;
        private final String type;
        private final URL url;

        private Version(String id, String type, URL url) {
            this.id = id;
            this.type = type;
            this.url = url;
        }

        public String id() {
            return id;
        }

        public String type() {
            return type;
        }

        public URL url() {
            return url;
        }

        public static Version fromJson(JsonReader reader) throws IOException {
            String id = null;
            String type = null;
            URL url = null;

            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                switch (name) {
                    case "id":
                        id = reader.nextString();
                        break;
                    case "type":
                        type = reader.nextString();
                        break;
                    case "url":
                        url =  new URL(reader.nextString());
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();

            return new Version(id, type, url);
        }
    }

    public static class Latest {
        private final String release;
        private final String snapshot;

        private Latest(String release, String snapshot) {
            this.release = release;
            this.snapshot = snapshot;
        }

        public String release() {
            return release;
        }

        public String snapshot() {
            return snapshot;
        }

        public static Latest fromJson(JsonReader reader) throws IOException {
            String release = null;
            String snapshot = null;

            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                switch (name) {
                    case "release":
                        release = reader.nextString();
                        break;
                    case "snapshot":
                        snapshot = reader.nextString();
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();

            return new Latest(release, snapshot);
        }
    }
}
