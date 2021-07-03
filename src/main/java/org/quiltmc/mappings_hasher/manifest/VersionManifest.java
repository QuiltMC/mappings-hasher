package org.quiltmc.mappings_hasher.manifest;

import org.quiltmc.json5.JsonReader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class VersionManifest {
    private final VersionEntry latestRelease;
    private final VersionEntry latestSnapshot;
    private final Map<String, VersionEntry> versions;

    private VersionManifest(Map<String, VersionEntry> versions, VersionEntry latestRelease, VersionEntry latestSnapshot) {
        this.versions = versions;
        this.latestRelease = latestRelease;
        this.latestSnapshot = latestSnapshot;
    }

    public Map<String, VersionEntry> versions() {
        return versions;
    }

    public VersionEntry latestRelease() {
        return latestRelease;
    }

    public VersionEntry latestSnapshot() {
        return latestSnapshot;
    }

    public static VersionManifest fromJson(JsonReader reader) throws IOException {
        Map<String, VersionEntry> versions = new HashMap<>();
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
                        VersionEntry version = VersionEntry.fromJson(reader);
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

        VersionEntry latestRelease = versions.get(latestReleaseId);
        VersionEntry latestSnapshot = versions.get(latestSnapshotId);

        if (latestRelease == null || latestSnapshot == null) {
            throw new IOException("Invalid latest release or snapshot");
        }

        return new VersionManifest(versions, latestRelease, latestSnapshot);
    }
}
