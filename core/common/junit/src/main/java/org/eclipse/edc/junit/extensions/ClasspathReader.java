/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.junit.extensions;

import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.spi.EdcException;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Read the classpath entries of the gradle modules.
 */
public class ClasspathReader {

    private static final File PROJECT_ROOT = TestUtils.findBuildRoot();
    private static final File GRADLE_WRAPPER = new File(PROJECT_ROOT, TestUtils.GRADLE_WRAPPER);

    /**
     * Get classpath entries for the passed modules
     *
     * @param modules the modules.
     * @return the classpath entries.
     */
    public static URL[] classpathFor(String... modules) {
        try {
            // Run a Gradle custom task to determine the runtime classpath of the module to run
            var printClasspath = Arrays.stream(modules).map(it -> it + ":printClasspath");
            var commandStream = Stream.of(GRADLE_WRAPPER.getCanonicalPath(), "-q");
            var command = Stream.concat(commandStream, printClasspath).toArray(String[]::new);

            var exec = Runtime.getRuntime().exec(command);

            try (var reader = exec.inputReader()) {
                return reader.lines().map(line -> line.split(":|\\s"))
                        .flatMap(Arrays::stream)
                        .filter(s -> !s.isBlank())
                        .map(File::new)
                        .flatMap(ClasspathReader::resolveClasspathEntry)
                        .toArray(URL[]::new);
            }

        } catch (IOException e) {
            throw new EdcException(e);
        }
    }

    /**
     * Replace Gradle subproject JAR entries with subproject build directories in classpath. This ensures modified
     * classes are picked up without needing to rebuild dependent JARs.
     *
     * @param classpathFile classpath entry file.
     * @return resolved classpath entries for the input argument.
     */
    private static Stream<URL> resolveClasspathEntry(File classpathFile) {
        try {
            if (isJar(classpathFile) && isUnderRoot(classpathFile)) {
                var buildDir = classpathFile.getCanonicalFile().getParentFile().getParentFile();
                return Stream.of(
                        new File(buildDir, "/classes/java/main").toURI().toURL(),
                        new File(buildDir, "../src/main/resources").toURI().toURL()
                );
            } else {
                var sanitizedClassPathEntry = classpathFile.getCanonicalPath().replace("build/resources/main", "src/main/resources");
                return Stream.of(new File(sanitizedClassPathEntry).toURI().toURL());
            }

        } catch (IOException e) {
            throw new EdcException(e);
        }
    }

    private static boolean isUnderRoot(File classPathFile) throws IOException {
        return classPathFile.getCanonicalPath().startsWith(ClasspathReader.PROJECT_ROOT.getCanonicalPath() + File.separator);
    }

    private static boolean isJar(File classPathFile) throws IOException {
        return classPathFile.getCanonicalPath().toLowerCase(Locale.ROOT).endsWith(".jar");
    }

}
