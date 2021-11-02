package org.quiltmc.mappings_hasher.util;

import org.quiltmc.launchermeta.version.v1.DownloadableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CachingFileDownloader extends FileDownloader {
    private final Path cacheDir;

    public CachingFileDownloader(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    @Override
    public Path download(DownloadableFile downloadableFile) throws IOException {
        String filename = downloadableFile.getUrl().substring(downloadableFile.getUrl().lastIndexOf('/') + 1);
        Path filePath = cacheDir.resolve(Paths.get(downloadableFile.getSha1(), filename));

        if (Files.exists(filePath)) {
            return filePath;
        }

        Path download = super.download(downloadableFile);
        Files.createDirectories(filePath.getParent());
        Files.move(download, filePath);
        return filePath;
    }
}
