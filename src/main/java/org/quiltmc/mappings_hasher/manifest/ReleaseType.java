package org.quiltmc.mappings_hasher.manifest;

public enum ReleaseType {
    RELEASE,
    SNAPSHOT,
    OLD_BETA,
    OLD_ALPHA;

    public static ReleaseType fromString(String s) {
        switch (s) {
            case "release":
                return RELEASE;
            case "snapshot":
                return SNAPSHOT;
            case "old_beta":
                return OLD_BETA;
            case "old_alpha":
                return OLD_ALPHA;
            default:
                throw new IllegalArgumentException("Unknown release type \"" + s + "\"");
        }
    }
}
