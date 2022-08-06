package com.rockthejvm.part4coordination

import cats.effect.std.CountDownLatch
import cats.effect.{IO, IOApp, Resource}
import cats.syntax.parallel.*
import com.rockthejvm.utils.*

import java.io.{File, FileWriter}
import scala.concurrent.duration.*
import scala.io.Source
import scala.language.postfixOps
import scala.util.Random

object CountdownLatches extends IOApp.Simple {

  /*
  Countdown Latches are a coordination primitive initialized with a count.
  All fibers calling await() on the Countdown Latch are (semantically) blocked.
  When the internal count of the latch reaches 0 (via release() calls from other fibers), all waiting fibers are unblocked.
  */

  def announcer(latch: CountDownLatch[IO]): IO[Unit] = for {
    _ <- IO("Starting race shortly...").debug >> IO.sleep(2 seconds)
    _ <- IO("5...").debug >> IO.sleep(1 second)
    _ <- latch.release
    _ <- IO("4...").debug >> IO.sleep(1 second)
    _ <- latch.release
    _ <- IO("3...").debug >> IO.sleep(1 second)
    _ <- latch.release
    _ <- IO("2...").debug >> IO.sleep(1 second)
    _ <- latch.release
    _ <- IO("1...").debug >> IO.sleep(1 second)
    _ <- latch.release
    _ <- IO("GO GO GO!").debug
  } yield ()

  def createRunner(id: Int, latch: CountDownLatch[IO]): IO[Unit] = for {
    _ <- IO(s"[runner $id] Waiting for signal...")
    _ <- latch.await // block this fiber until the count reaches 0
    _ <- IO(s"[runner $id] RUNNING!").debug
  } yield ()

  def sprint(): IO[Unit] = for {
    latch <- CountDownLatch[IO](5)
    announcerFib <- announcer(latch).start
    _ <- (1 to 10).toList.parTraverse(id => createRunner(id, latch))
    _ <- announcerFib.join
  } yield ()

  /*
  Exercise: simulate a file downloader on multiple threads
   */
  object FileServer {
    val fileChunksList: Array[String] = Array(
      "I love Scala.",
      "Cats Effect seems quite fun.",
      "Never would I have thought I would do low-level concurrency WITH pure FP."
    )

    def getNumberOfChunks: IO[Int] = IO(fileChunksList.length)

    def getFileChunk(n: Int): IO[String] = IO(fileChunksList(n))
  }

  def writeToFile(path: String, contents: String): IO[Unit] = {
    val fileResource = Resource.make(IO(new FileWriter(new File(path))))(writer => IO(writer.close()))
    fileResource.use { writer =>
      IO(writer.write(contents))
    }
  }

  def appendFileContents(fromPath: String, toPath: String): IO[Unit] = {
    val compositeResource = for {
      reader <- Resource.make(IO(Source.fromFile(fromPath)))(source => IO(source.close()))
      writer <- Resource.make(IO(new FileWriter(new File(toPath), true)))(writer => IO(writer.close()))
    } yield (reader, writer)

    compositeResource.use {
      case (reader, writer) => IO(reader.getLines().foreach(writer.write))
    }
  }

  /*
  - call file server API and get the number of chunks (n)
  - start a CDLatch
  - start n fibers which download a chunk of the file (use the file server's download chunk API)
  - block on the latch until each task has finished
  - after all chunks are done, stitch the files together under the same file on disk
  */
  def downloadFile(fileName: String, destinationFolder: String): IO[Unit] = for {
    n <- FileServer.getNumberOfChunks
    latch <- CountDownLatch[IO](n)
    _ <- IO(s"Download started on $n fibers").debug
    _ <- (0 until n).toList.parTraverse(id => createDownloaderTask(id, latch, fileName, destinationFolder))
    _ <- latch.await
    _ <- (0 until n).toList.parTraverse(id => appendFileContents(s"$destinationFolder/$fileName.part$id", s"$destinationFolder/$fileName"))
  } yield ()

  def createDownloaderTask(id: Int, latch: CountDownLatch[IO], fileName: String, destinationFolder: String): IO[Unit] = for {
    _ <- IO(s"[task $id] Downloading chunk...").debug
    _ <- IO.sleep((Random.nextDouble * 1000).toInt.millis)
    chunk <- FileServer.getFileChunk(id)
    _ <- writeToFile(s"$destinationFolder/$fileName.part$id", chunk)
    _ <- IO(s"[task $id] Chunk download complete").debug
    _ <- latch.release
  } yield ()


  override def run: IO[Unit] = {
    //sprint()
    downloadFile("scalaFile.txt", "src/main/resources")
  }
}
