package com.ambientbrightness.core

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArchitectureStructureTest {

    @Test
    fun `every source file lives in the folder its package names and imports no Android type`() {
        val violations = folderPackageViolations() + androidImportViolations()

        assertTrue(violations.isEmpty(), violations.joinToString(System.lineSeparator()))
    }

    private fun folderPackageViolations(): List<String> =
        buildList {
            sourceFiles().forEach { file ->
                val declared = declaredPackage(file)
                val expected = expectedPackage(file)
                when {
                    expected == null -> add(
                        "${display(file)} is outside $expectedPackageBase, so no package can be " +
                            "derived from its folder (declares ${declared ?: "no package"})",
                    )

                    declared == null -> add(
                        "${display(file)} has no package declaration; its folder requires '$expected'",
                    )

                    declared != expected -> add(
                        "${display(file)} declares package '$declared' but its folder requires " +
                            "'$expected'",
                    )
                }
            }
        }

    private fun androidImportViolations(): List<String> =
        buildList {
            sourceFiles().forEach { file ->
                codeLines(file)
                    .filter { androidImport.containsMatchIn(it) }
                    .forEach { add("${display(file)} imports an Android type: $it") }
            }
        }

    private fun sourceFiles(): List<File> {
        assertTrue(
            sourceRoot.isDirectory,
            "source root '${display(sourceRoot)}' is not a directory of the working directory " +
                "'${display(File(".").absoluteFile)}': the guard must not pass vacuously",
        )
        return sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun expectedPackage(file: File): String? {
        val base = packageBaseDirectory.absoluteFile.normalize().toPath()
        val directory = file.absoluteFile.normalize().toPath().parent ?: return null
        if (!directory.startsWith(base)) return null
        val folder = base.relativize(directory).map { it.toString() }
        return (expectedPackageBase.split('.') + folder).joinToString(".")
    }

    private fun declaredPackage(file: File): String? =
        codeLines(file).firstNotNullOfOrNull { packageDeclaration.find(it)?.groupValues?.get(1) }

    private fun codeLines(file: File): List<String> {
        val lines = mutableListOf<String>()
        var insideBlockComment = false
        file.readLines(Charsets.UTF_8).forEach { rawLine ->
            val line = rawLine.trim()
            when {
                insideBlockComment -> if (line.contains("*/")) insideBlockComment = false
                line.isEmpty() || line.startsWith("//") -> Unit
                line.startsWith("/*") -> if (!line.contains("*/")) insideBlockComment = true
                else -> lines += line
            }
        }
        return lines
    }

    private fun display(file: File): String = file.path.replace(File.separatorChar, '/')

    private companion object {
        const val expectedPackageBase = "com.ambientbrightness"

        val sourceRoot = File("src/main/kotlin")
        val packageBaseDirectory = File("src/main/kotlin/com/ambientbrightness")
        val packageDeclaration = Regex("""^package\s+([A-Za-z_][A-Za-z0-9_.]*)""")
        val androidImport = Regex("""^import\s+android\.""")
    }
}
