package org.quiltmc.mappings_hasher;

import org.junit.jupiter.api.*;
import picocli.CommandLine;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BasicTests {
    @Test
    public void hash_1_17_1() throws IOException, URISyntaxException {
        int exitCode = new CommandLine(new Main()).execute("--version=1.17.1", "--cache=cache");
        Assertions.assertEquals(0, exitCode);

        Path outPath = Paths.get("mappings", "hashed-1.17.1.tiny");
        Assertions.assertTrue(Files.exists(outPath));
        Path expectedPath = Paths.get(getClass().getResource("/mappings/hashed-1.17.1.tiny").toURI());
        assertEqualContent(expectedPath, outPath);
    }

    private static void assertEqualContent(Path expected, Path actual) throws IOException {
        Assertions.assertLinesMatch(Files.readAllLines(expected), Files.readAllLines(actual), "Content of " + actual + " is not equal to " + expected);
    }
}
