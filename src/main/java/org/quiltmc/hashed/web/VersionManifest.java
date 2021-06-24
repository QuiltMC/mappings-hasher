package org.quiltmc.hashed.web;

import org.quiltmc.json5.JsonReader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class VersionManifest {
    private final Version latestRelease;
    private final Version latestSnapshot;
    private final Map<String, Version> versions;

    private VersionManifest(Map<String, Version> versions, Version latestRelease, Version latestSnapshot) {
        this.versions = versions;
        this.latestRelease = latestRelease;
        this.latestSnapshot = latestSnapshot;
    }

    public Map<String, Version> versions() {
        return versions;
    }

    public Version latestRelease() {
        return latestRelease;
    }

    public Version latestSnapshot() {
        return latestSnapshot;
    }

    public static VersionManifest fromJson(JsonReader reader) throws IOException {
        Map<String, Version> versions = new HashMap<>();
        String latestReleaseId = null;
        String latestSnapshotId = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "latest":
                    reader.beginObject();
                    while (reader.hasNext()) {
                        name = reader.nextName();
                        switch (name) {
                            case "release":
                                latestReleaseId = reader.nextString();
                                break;
                            case "snapshot":
                                latestSnapshotId = reader.nextString();
                                break;
                            default:
                                reader.skipValue();
                        }
                    }
                    reader.endObject();
                    break;
                case "versions":
                    reader.beginArray();
                    while (reader.hasNext()) {
                        Version version = Version.fromJson(reader);
                        versions.put(version.id(), version);
                    }
                    reader.endArray();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        if (latestReleaseId == null || latestSnapshotId == null) {
            throw new IOException("Missing latest release or snapshot");
        }

        Version latestRelease = versions.get(latestReleaseId);
        Version latestSnapshot = versions.get(latestSnapshotId);

        if (latestRelease == null || latestSnapshot == null) {
            throw new IOException("Invalid latest release or snapshot");
        }

        return new VersionManifest(versions, latestRelease, latestSnapshot);
    }
}
