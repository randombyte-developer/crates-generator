package de.randombyte.crates

import java.nio.file.Paths;
import java.io.BufferedWriter;
import java.io.File;
import kotlin.system.exitProcess

val AUDIO_FILE_EXTENSIONS = listOf("mp3", "m4a", "opus")

fun BufferedWriter.writeLine(line: String) {
  write(line)
  newLine()
}

fun getAudioFilesRecursively(folder: File): List<String> {
  return folder.walk().filter { it.isFile && it.extension in AUDIO_FILE_EXTENSIONS }.map { it.absolutePath }.toList()
}

fun main(args: Array<String>) {

  if (args.size < 2) {
    println("Arguments: '<folderPaths...> <crateFilesDestination>'")
    exitProcess(0)
  }

  val folderPaths = args.dropLast(1)
  val crateFilesDestinationPath = Paths.get(args.last())

  folderPaths.forEach { folderStringPath ->
    val folderPath = Paths.get(folderStringPath)
    val crateTrackPaths = folderPath.toFile()
      .walk()
      .filter { file -> file.isDirectory && file.toPath() != folderPath } // prevent writing down the top level folder
      .map { dir -> dir.name to getAudioFilesRecursively(dir) }

    crateTrackPaths.forEach { (crateName, trackPaths) ->
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
