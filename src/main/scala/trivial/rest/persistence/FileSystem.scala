package trivial.rest.persistence

import java.nio.charset.StandardCharsets._
import java.nio.file.Files._
import java.nio.file.StandardCopyOption._
import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Path, Paths}

import scala.reflect.io.{Directory, File}

// Copied from https://github.com/alltonp/little/blob/master/src/main/scala/im/mange/little/file/Filepath.scala
object FileSystem {
  def save(content: String, path: Path)   = write(path, content.getBytes(UTF_8), CREATE, WRITE, TRUNCATE_EXISTING)
  def append(content: String, path: Path) = write(path, content.getBytes(UTF_8), CREATE, WRITE, APPEND)

  // TODO - CAS - 06/05/15 - De-boilerplagerise
  def move(source: Path, target: Path): Path = Files.move(source, target, ATOMIC_MOVE)
  def move(source: File, target: File): Path = move(Paths.get(source.path), Paths.get(target.path))
  def move(source: Directory, target: Directory): Path = move(Paths.get(source.path), Paths.get(target.path))

  def copy(source: Path, target: Path): Path = Files.copy(source, target, COPY_ATTRIBUTES)
  def copy(source: File, target: File): Path = copy(Paths.get(source.path), Paths.get(target.path))
  def copy(source: Directory, target: Directory): Path = copy(Paths.get(source.path), Paths.get(target.path))

  def create(path: Path) = createFile(path)
  def load(path: Path)   = File(path.toFile).slurp()
}