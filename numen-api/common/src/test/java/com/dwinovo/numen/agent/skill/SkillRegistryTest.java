package com.dwinovo.numen.agent.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SkillRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void readsNestedSupportFile() throws Exception {
        Path skillDir = createSkill(tempDir.resolve("skills"), "demo");
        Path supportFile = skillDir.resolve("references/guide.md");
        Files.createDirectories(supportFile.getParent());
        Files.writeString(supportFile, "safe content");

        assertEquals("safe content", scan(skillDir).readSupportFile("demo", "references/guide.md"));
    }

    @Test
    void rejectsLexicalTraversalAndAbsolutePaths() throws Exception {
        Path skillDir = createSkill(tempDir.resolve("skills"), "demo");
        Path outside = tempDir.resolve("outside.txt");
        Files.writeString(outside, "secret");
        SkillRegistry registry = scan(skillDir);

        assertStaysInside(registry, "../outside.txt");
        assertStaysInside(registry, outside.toAbsolutePath().toString());
    }

    @Test
    void missingFileKeepsHelpfulError() throws Exception {
        Path skillDir = createSkill(tempDir.resolve("skills"), "demo");
        Files.writeString(skillDir.resolve("available.txt"), "available");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> scan(skillDir).readSupportFile("demo", "missing.txt"));

        assertTrue(error.getMessage().contains("no such file in skill demo: missing.txt"));
        assertTrue(error.getMessage().contains("available.txt"));
    }

    @Test
    void rejectsSymbolicLinkToFileOutsideSkillDirectory() throws Exception {
        Path skillDir = createSkill(tempDir.resolve("skills"), "demo");
        Path outside = tempDir.resolve("outside.txt");
        Files.writeString(outside, "secret");
        Path link = skillDir.resolve("escape.txt");
        createSymbolicLinkOrSkip(link, outside);

        try {
            assertStaysInside(scan(skillDir), "escape.txt");
        } finally {
            Files.deleteIfExists(link);
        }
    }

    @Test
    void rejectsSymbolicLinkToDirectoryOutsideSkillDirectory() throws Exception {
        Path skillDir = createSkill(tempDir.resolve("skills"), "demo");
        Path outsideDir = Files.createDirectory(tempDir.resolve("outside"));
        Files.writeString(outsideDir.resolve("secret.txt"), "secret");
        Path link = skillDir.resolve("escape");
        createSymbolicLinkOrSkip(link, outsideDir);

        try {
            assertStaysInside(scan(skillDir), "escape/secret.txt");
        } finally {
            Files.deleteIfExists(link);
        }
    }

    @Test
    void allowsSymbolicLinkWhoseTargetRemainsInsideSkillDirectory() throws Exception {
        Path skillDir = createSkill(tempDir.resolve("skills"), "demo");
        Path target = skillDir.resolve("target.txt");
        Files.writeString(target, "safe content");
        Path link = skillDir.resolve("alias.txt");
        createSymbolicLinkOrSkip(link, target);

        try {
            assertEquals("safe content", scan(skillDir).readSupportFile("demo", "alias.txt"));
        } finally {
            Files.deleteIfExists(link);
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void rejectsWindowsDirectoryJunctionOutsideSkillDirectory() throws Exception {
        Path skillDir = createSkill(tempDir.resolve("skills"), "demo");
        Path outsideDir = Files.createDirectory(tempDir.resolve("outside"));
        Files.writeString(outsideDir.resolve("secret.txt"), "secret");
        Path junction = skillDir.resolve("escape");
        Process process = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J",
                junction.toString(), outsideDir.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), Charset.defaultCharset());
        assumeTrue(process.waitFor() == 0, "cannot create junction for test: " + output);

        try {
            assertStaysInside(scan(skillDir), "escape/secret.txt");
        } finally {
            Files.deleteIfExists(junction);
        }
    }

    @Test
    void readsSupportFileFromBundledZipFileSystem() throws Exception {
        Path archive = tempDir.resolve("skills.zip");
        URI uri = URI.create("jar:" + archive.toUri());
        try (FileSystem zip = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
            Path skillDir = createSkill(zip.getPath("/skills"), "demo");
            Files.writeString(skillDir.resolve("guide.txt"), "bundled content");

            assertEquals("bundled content", scan(skillDir).readSupportFile("demo", "guide.txt"));
        }
    }

    private static Path createSkill(Path skillsRoot, String name) throws IOException {
        Path skillDir = Files.createDirectories(skillsRoot.resolve(name));
        Files.writeString(skillDir.resolve(SkillRegistry.SKILL_FILENAME), """
                ---
                name: %s
                description: Test skill
                ---
                Test instructions.
                """.formatted(name));
        return skillDir;
    }

    private static SkillRegistry scan(Path skillDir) throws Exception {
        Constructor<SkillRegistry> constructor = SkillRegistry.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        SkillRegistry registry = constructor.newInstance();
        assertEquals(1, registry.scan(skillDir.getParent()));
        return registry;
    }

    private static void assertStaysInside(SkillRegistry registry, String relPath) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> registry.readSupportFile("demo", relPath));
        assertEquals("file must stay inside the skill directory", error.getMessage());
    }

    private static void createSymbolicLinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException error) {
            assumeTrue(false, "symbolic links are unavailable: " + error);
        }
    }
}
