package de.randombyte.crates

import de.randombyte.crates.CommandLineArguments.ArgumentType
import de.randombyte.crates.CommandLineArguments.ArgumentType.*
import java.nio.file.Paths
import java.io.BufferedWriter
import java.io.File
import java.sql.DriverManager
import kotlin.system.exitProcess

val AUDIO_FILE_EXTENSIONS = listOf("mp3", "m4a", "opus", "flac", "wav", "ogg")

fun BufferedWriter.writeLine(line: String) {
  write(line)
  newLine()
}

fun main(args: Array<String>) {
  when (args.getOrNull(0)) {
    "generate" -> generateCrates(args.drop(1))
    "clear" -> clearCrates(args.drop(1))
    else -> println("Unknown command! Use 'generate' or 'clear'.")
  }
}

fun clearCrates(args: List<String>) {
  val osName = System.getProperty("os.name")

  val userHome = File(System.getProperty("user.home"))
  val mixxxFolder = if (osName.contains("Windows")) {
    userHome.resolve("AppData").resolve("Local").resolve("Mixxx")
  } else {
    userHome.resolve(".mixxx")
  }

  val mixxxDb = mixxxFolder.resolve("mixxxdb.sqlite")
  if (!mixxxDb.exists()) {
    println("Database at '${mixxxDb.absolutePath}' doesn't exist!")
    exitProcess(1)
  }

  val connection = DriverManager.getConnection("jdbc:sqlite:${mixxxDb.absolutePath}").use { connection ->
    connection.createStatement().executeUpdate("DELETE FROM crates;")
  }
}

fun generateCrates(args: List<String>) {
  val arguments = parseAndVerifyArguments(args)

  val filesWithPriority = writeM3uFiles(arguments, PriorityTopLevelFolders, excludeFiles = emptyList())
  val normalFiles = writeM3uFiles(arguments, TracksTopLevelFolders, excludeFiles = filesWithPriority)

  val priorityFilesWithoutDuplicate = filesWithPriority.filter { priorityFile -> priorityFile !in normalFiles }

  if (priorityFilesWithoutDuplicate.isNotEmpty()) {
    println("Priority files without any duplicate (they overrode nothing):\n")
    priorityFilesWithoutDuplicate.forEach { println(it) }
  }
}

fun writeM3uFiles(arguments: CommandLineArguments, topLevelsFoldersArgument: ArgumentType, excludeFiles: List<String>): List<String> {
  val crateFilesDestinationPath = Paths.get(arguments[CrateFilesDestination].single())

  val allFileNames = mutableListOf<String>()

  arguments[topLevelsFoldersArgument].forEach { folderStringPath ->
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
          trackPaths.forEach innerFor@ { trackPath ->
            val fileName = File(trackPath).nameWithoutExtension
            allFileNames += fileName
            if (fileName in excludeFiles) return@innerFor

            writer.writeLine("#EXTINF")
            writer.writeLine(trackPath)
          }
        }
    }
  }

  return allFileNames
}

fun parseAndVerifyArguments(args: List<String>): CommandLineArguments {
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
    TracksTopLevelFolders("-tracks-top-level-folders", emptyAllowed = false),
    PriorityTopLevelFolders("-priority-top-level-folders", emptyAllowed = true),
    ExcludedCrates("-excluded-crates", emptyAllowed = true),
    CrateFilesDestination("-crate-files-destination", emptyAllowed = false);

    companion object {
      val mappedById = values().associateBy { it.id }

      fun fromId(id: String): ArgumentType? = mappedById[id]
    }
  }

  operator fun get(type: ArgumentType) = arguments.getValue(type)

  fun findEmptyArguments() = values()
    .filter { type -> !type.emptyAllowed && arguments.getValue(type).isEmpty() }

  companion object {
    fun from(arguments: List<String>): CommandLineArguments {
      var currentArgumentType: ArgumentType? = null
      val mappedArguments = mutableMapOf<ArgumentType, List<String>>()

      for (argument in arguments) {

        val argumentType = ArgumentType.fromId(argument)
        if (argumentType != null) {
          currentArgumentType = argumentType
          continue
        }

        if (currentArgumentType == null) {
          println("The first argument has to be an ArgumentType!")
          exitProcess(1)
        }

        mappedArguments[currentArgumentType] = (mappedArguments[currentArgumentType] ?: emptyList()) + argument
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
