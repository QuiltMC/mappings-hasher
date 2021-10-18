package org.quiltmc.mappings_hasher;

import net.fabricmc.lorenztiny.TinyMappingsWriter;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.TextMappingsReader;
import org.cadixdev.lorenz.io.proguard.ProGuardReader;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "mappings-hasher", mixinStandardHelpOptions = true)
public class Main implements Callable<Integer> {
    @Parameters(description = "URL pointing to the input mappings.")
    URL input;

    @Parameters(description = "The name of the generated mappings.")
    Path output;

    @Override
    public Integer call() {
        try {
            InputStream mappingsStream = input.openConnection().getInputStream();
            TextMappingsReader mappingsReader = new ProGuardReader(new InputStreamReader(mappingsStream));
            MappingsHasher mappingsHasher = new MappingsHasher(mappingsReader.read().reverse(), "net/minecraft/unmapped");

            Files.createDirectories(output.getParent());
            Files.deleteIfExists(output);
            Files.createFile(output);

            MappingSet result = mappingsHasher.generate();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(output)));
            TinyMappingsWriter mappingsWriter = new TinyMappingsWriter(writer, "official", "hashed");
            mappingsWriter.write(result);
            writer.flush();
            writer.close();
        }
        catch (IOException e){
            System.err.println("IO error: " + e);
            return 1;
        }

        return 0;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }
}
