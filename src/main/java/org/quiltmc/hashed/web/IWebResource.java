package org.quiltmc.hashed.web;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public interface IWebResource {
    String sha1();

    URL url();

    Path path();

    default File getOrDownload() throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 doesn't exist");
        }

        Path filePath = Paths.get("cache").resolve(path());
        Files.createDirectories(filePath.getParent());
        if (Files.exists(filePath)) {
            InputStream stream = Files.newInputStream(filePath);
            byte[] buffer = new byte[1024];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
            stream.close();

            byte[] bytes = digest.digest();
            StringBuilder sha1 = new StringBuilder();
            for (byte b : bytes) {
                sha1.append(String.format("%02x", b));
            }
            if (sha1.toString().equals(sha1())) {
                System.out.println("Found " + path() + " in cache with matching sha1");
                return filePath.toFile();
            }
            System.out.println("Found " + path() + " in cache with different sha1, downloading from \"" + url() + "\"");
        }
        else {
            System.out.println(path() + " not found in cache, downloading from \"" + url() + "\"");
        }

        InputStream stream = url().openConnection().getInputStream();

        Files.deleteIfExists(filePath);
        Files.createFile(filePath);
        OutputStream out = Files.newOutputStream(filePath);
        byte[] buffer = new byte[1024];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            out.write(buffer, 0, count);
            digest.update(buffer, 0, count);
        }
        stream.close();
        out.close();

        byte[] bytes = digest.digest();
        StringBuilder sha1 = new StringBuilder();
        for (byte b : bytes) {
            sha1.append(String.format("%02x", b));
        }
        if (sha1.toString().equals(sha1())) {
            return filePath.toFile();
        }
        throw new IOException("Download of file \"" + url() + "\" failed, sha1 mismatch");
    }
}
