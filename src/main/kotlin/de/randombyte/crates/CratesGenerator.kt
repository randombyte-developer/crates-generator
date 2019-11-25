package de.randombyte.crates

import java.nio.file.Paths;
import java.io.BufferedWriter;
import java.io.File;

fun BufferedWriter.writeLine(line: String) {
  write(line)
  newLine()
}

fun getFilesRecursively(folder: File): List<String> {
  return folder.walk().filter { it.isFile && it.extension == "mp3" }.map { it.absolutePath }.toList()
}

fun main(args: Array<String>) {

  if (args.size != 2) {
    println("Usage: './generateCrates.kts <foldersPath> <crateFilesDestination>'")
    System.exit(0)
  }
  
  val path = Paths.get(args[0])
  val crateTrackPaths = path.toFile().walk()
	  .filter { file -> file.isDirectory() }.map { dir -> dir.name to getFilesRecursively(dir) }
  
  crateTrackPaths.forEach { (crateName, trackPaths) ->
    Paths.get(args[1], crateName + ".m3u").toFile().apply { parentFile.mkdirs(); createNewFile() }.bufferedWriter().use { writer ->
      writer.writeLine("#EXTM3U")

      trackPaths.forEach { trackPath ->
        writer.writeLine("#EXTINF")
        writer.writeLine(trackPath)
      }
    }
  }
}
