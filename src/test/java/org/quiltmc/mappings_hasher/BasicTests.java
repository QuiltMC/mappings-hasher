package org.quiltmc.mappings_hasher;

import org.junit.jupiter.api.*;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BasicTests {
    @Test
    public void hash_1_17_1() throws IOException {
        int exitCode = new CommandLine(new Main()).execute("--version=1.17.1", "--cache=cache");
        Assertions.assertEquals(0, exitCode);

        Path outPath = Paths.get("mappings", "hashed-1.17.1.tiny");
        Assertions.assertTrue(Files.exists(outPath));
        assertEqualContent(getClass().getResourceAsStream("/mappings/hashed-1.17.1.tiny"), Files.newInputStream(outPath));
    }

    private static void assertEqualContent(InputStream expected, InputStream actual) throws IOException {
        BufferedInputStream first = new BufferedInputStream(expected);
        BufferedInputStream second = new BufferedInputStream(actual);

        int firstByte;
        int secondByte;
        do {
            firstByte = first.read();
            secondByte = second.read();
            Assertions.assertEquals(firstByte, secondByte);
        } while (firstByte != -1);
    }
}
