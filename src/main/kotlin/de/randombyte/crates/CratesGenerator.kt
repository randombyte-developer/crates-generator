package de.randombyte.crates

import de.randombyte.crates.CommandLineArguments.ArgumentType.*
import java.nio.file.Paths;
import java.io.BufferedWriter;
import java.io.File;
import kotlin.system.exitProcess

val AUDIO_FILE_EXTENSIONS = listOf("mp3", "m4a", "opus")

fun BufferedWriter.writeLine(line: String) {
  write(line)
  newLine()
}

fun main(args: Array<String>) {

  val arguments = parseAndVerifyArguments(args)

  val crateFilesDestinationPath = Paths.get(arguments[CrateFilesDestination].single())

  arguments[TracksTopLevelFolders].forEach { folderStringPath ->
    val crates = File(folderStringPath)
      .walk()
      .filter { file -> file.isDirectory && file.name !in arguments[ExcludedCrates] }
      .map { dir -> dir.name to getAudioFilesRecursively(dir) }

    crates.forEach { (crateName, trackPaths) ->
      crateFilesDestinationPath.resolve("$crateName.m3u")
        .toFile()
        .apply { parentFile.mkdirs(); createNewFile() }
        .bufferedWriter().use { writer ->
          writer.writeLine("#EXTM3U")
          trackPaths.forEach { trackPath ->
            writer.writeLine("#EXTINF")
            writer.writeLine(trackPath)
          }
      }
    }
  }
}

fun parseAndVerifyArguments(args: Array<String>): CommandLineArguments {
  val arguments = CommandLineArguments.from(args)

  val emptyArguments = arguments.findEmptyArguments()
  if (emptyArguments.isNotEmpty()) {
    println("Following arguments are not provided: " + emptyArguments.joinToString { it.id })
    exitProcess(1)
  }
  if (arguments.arguments.getValue(CrateFilesDestination).size != 1) {
    println("The argument ${CrateFilesDestination.id} only supports one value!")
    exitProcess(1)
  }

  return arguments
}

class CommandLineArguments(val arguments: Map<ArgumentType, List<String>>) {

  enum class ArgumentType(val id: String, val emptyAllowed: Boolean) {
    TracksTopLevelFolders("tracks-top-level-folders", emptyAllowed = false),
    ExcludedCrates("excluded-crates", emptyAllowed = true),
    CrateFilesDestination("crate-files-destination", emptyAllowed = false);

    companion object {
      val mappedById = values().associateBy { it.id }

      fun fromId(id: String): ArgumentType? = mappedById[id]
    }
  }

  operator fun get(type: ArgumentType) = arguments.getValue(type)

  fun findEmptyArguments() = values()
    .filter { type -> !type.emptyAllowed && arguments.getValue(type).isEmpty() }

  companion object {
    fun from(arguments: Array<String>): CommandLineArguments {
      var currentArgumentType: ArgumentType? = null
      val mappedArguments = mutableMapOf<ArgumentType, List<String>>()

      for (argument in arguments) {
        if (argument.startsWith("-")) {
          currentArgumentType = ArgumentType.fromId(argument.removePrefix("-"))
        } else {
          if (currentArgumentType == null) {
            println("The first argument has to be an ArgumentType!")
            exitProcess(1)
          }

          mappedArguments[currentArgumentType] = (mappedArguments[currentArgumentType] ?: emptyList()) + argument
        }
      }

      // fill missing values with empty values
      values()
        .filter { type -> !mappedArguments.containsKey(type) }
        .forEach { type -> mappedArguments[type] = emptyList() }

      return CommandLineArguments(mappedArguments)
    }
  }
}

fun getAudioFilesRecursively(folder: File): List<String> {
  return folder.walk().filter { it.isFile && it.extension in AUDIO_FILE_EXTENSIONS }.map { it.absolutePath }.toList()
}
