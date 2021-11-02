package org.quiltmc.mappings_hasher.util;

import org.quiltmc.launchermeta.version.v1.DownloadableFile;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileDownloader {
    public Path download(DownloadableFile downloadableFile) throws IOException {
        URL url = new URL(downloadableFile.getUrl());

        InputStream stream = new BufferedInputStream(url.openStream());
        Path tempFile = Files.createTempFile(null, null);

        OutputStream out = Files.newOutputStream(tempFile);
        byte[] buffer = new byte[1024];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            out.write(buffer, 0, count);
        }
        stream.close();
        out.close();

        tempFile.toFile().deleteOnExit();
        return tempFile;
    }
}
