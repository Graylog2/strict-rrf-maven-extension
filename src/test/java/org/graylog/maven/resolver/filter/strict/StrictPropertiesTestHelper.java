package org.graylog.maven.resolver.filter.strict;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper class for writing strict.properties configuration files in tests.
 */
class StrictPropertiesTestHelper {

    private StrictPropertiesTestHelper() {
        // Utility class
    }

    /**
     * Write strict.properties configuration file.
     *
     * @param tempDir the directory where the file should be written
     * @param content the content to write to the file
     * @throws IOException if an I/O error occurs
     */
    static void writeStrictProperties(Path tempDir, String content) throws IOException {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("strict.properties"), content);
    }
}
