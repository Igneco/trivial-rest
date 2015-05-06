package trivial.rest.persistence

import java.nio.charset.StandardCharsets._
import java.nio.file.Files._
import java.nio.file.StandardCopyOption._
import java.nio.file.StandardOpenOption._
import java.nio.file.{Paths, Files, Path}

import scala.reflect.io.File

// Copied from https://github.com/alltonp/little/blob/master/src/main/scala/im/mange/little/file/Filepath.scala
object FileSystem {
  def save(content: String, path: Path)   = write(path, content.getBytes(UTF_8), CREATE, WRITE, TRUNCATE_EXISTING)
  def append(content: String, path: Path) = write(path, content.getBytes(UTF_8), CREATE, WRITE, APPEND)

  def move(source: Path, target: Path): Path = Files.move(source, target, ATOMIC_MOVE)
  def move(source: File, target: File): Path = move(Paths.get(source.path), Paths.get(target.path))

  def create(path: Path) = createFile(path)
  def load(path: Path)   = File(path.toFile).slurp()
}