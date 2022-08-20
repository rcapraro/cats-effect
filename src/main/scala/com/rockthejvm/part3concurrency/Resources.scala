package com.rockthejvm.part3concurrency

import cats.effect.kernel.Outcome
import cats.effect.{IO, IOApp, Resource}
import com.rockthejvm.utils.*

import java.io.{File, FileReader}
import java.util.Scanner
import scala.concurrent.duration.*
import scala.language.postfixOps

object Resources extends IOApp.Simple {

  // use-case: manage a connection lifecycle
  class Connection(url: String) {
    def open: IO[String] = IO(s"Opening connection to $url").debug

    def close: IO[String] = IO(s"Closing connection to $url").debug
  }

  val asyncFetchUrl: IO[Unit] = for {
    fib <- (new Connection("rockthejvm.com").open *> IO.sleep((Int.MaxValue) seconds)).start
    _   <- IO.sleep(1 second) *> fib.cancel
  } yield ()
  // problem: leaking resources

  val correctAsyncFetchUrl: IO[Unit] = for {
    conn <- IO(new Connection("rockthejvm.com"))
    fib  <- (conn.open *> IO.sleep((Int.MaxValue) seconds)).onCancel(conn.close.void).start
    _    <- IO.sleep(1 second) *> fib.cancel
  } yield ()

  /*
  bracket pattern: someIO.bracket(useResourceCb)(releaseResourceCb)
  bracket is equivalent to try-catches (pure FP)
   */

  val bracketFetchUrl: IO[Unit] = IO(new Connection("rockthejvm.com"))
    .bracket(conn => conn.open *> IO.sleep((Int.MaxValue) seconds))(conn => conn.close.void)

  val bracketProgram: IO[Unit] = for {
    fib <- bracketFetchUrl.start
    _   <- IO.sleep(1 second) *> fib.cancel
  } yield ()

  /** Exercises: read the current file with the bracket pattern.
    *   - open a scanner
    *   - read the file line by line, every 100 millis
    *   - close the scanner
    *   - if canceled/throws, close the scanner
    */
  def openFileScanner(path: String): IO[Scanner] =
    IO(new Scanner(new FileReader(new File(path))))

  def readLineByLine(scanner: Scanner): IO[Unit] =
    if (scanner.hasNextLine) {
      IO(scanner.nextLine()).debug >> IO.sleep(100 millis) >> readLineByLine(scanner)
    } else {
      IO.unit
    }

  def bracketReadFile(path: String): IO[Unit] =
    IO(s"Opening file at $path") >>
      openFileScanner(path).bracket { scanner =>
        readLineByLine(scanner)
      } { scanner =>
        IO(s"Closing file at $path").debug >> IO(scanner.close())
      }

  /** Resources
    */
  def connectionFromConfig(path: String): IO[Unit] = {
    openFileScanner(path).bracket { scanner =>
      // acquire a connection
      IO(new Connection(scanner.nextLine())).bracket { conn =>
        conn.open >> IO.never
      }(conn => conn.close.void)
    }(scanner => IO("Closing file").debug >> IO(scanner.close()))
    // nesting resources id tedious
  }

  // defines the acquisition and the release, not the usage
  val connectionResource: Resource[IO, Connection] = Resource.make(IO(new Connection("rockthejvm.com")))(conn => conn.close.void)

  // ... at a later part of your code
  val resourceFetchUrl: IO[Unit] = for {
    fib <- connectionResource.use(conn => conn.open >> IO.never).start
    _   <- IO.sleep(1 second) >> fib.cancel
  } yield ()

  // resource are equivalent to brackets
  val simpleResource: IO[String]          = IO("some resource")
  val usingResource: String => IO[String] = string => IO(s"using the string $string").debug
  val releaseResource: String => IO[Unit] = string => IO(s"finalizing the string $string").debug.void

  val usingResourceWithBracket: IO[String]  = simpleResource.bracket(usingResource)(releaseResource)
  val usingResourceWithResource: IO[String] = Resource.make(simpleResource)(releaseResource).use(usingResource)

  /** Exercises: read the current file with the resource pattern.
    */
  def fileScannerResource(path: String): Resource[IO, Scanner] = Resource.make(openFileScanner(path)) { scanner =>
    IO(s"Closing file at $path").debug >> IO(scanner.close())
  }

  def resourceReadFile(path: String): IO[Unit] =
    IO(s"Opening file at $path") >>
      fileScannerResource(path).use(scanner => readLineByLine(scanner))

  def cancelReadFile(path: String): IO[Unit] = for {
    fib <- resourceReadFile(path).start
    _   <- IO.sleep(2 seconds) >> IO(s"Canceling the reading of file at $path").debug >> fib.cancel
  } yield ()

  // nested resources
  def connectionFromConfigurationResource(path: String): Resource[IO, Connection] =
    Resource
      .make(IO(s"Opening file at $path").debug >> openFileScanner(path))(scanner => IO(s"Closing file at $path").debug >> IO(scanner.close()))
      .flatMap(scanner => Resource.make(IO(new Connection(scanner.nextLine())))(conn => conn.close.void))

  // equivalent
  def connectionFromConfigurationClean(path: String): Resource[IO, Connection] = for {
    scanner <- Resource.make(IO(s"Opening file at $path").debug >> openFileScanner(path))(scanner => IO(s"Closing file at $path").debug >> IO(scanner.close()))
    conn    <- Resource.make(IO(new Connection(scanner.nextLine())))(conn => conn.close.void)
  } yield conn

  val openConnection: IO[String] =
    connectionFromConfigurationClean("src/main/resources/connection.txt")
      .use(conn => conn.open >> IO.never)

  val canceledConnection: IO[Unit] = for {
    fib <- openConnection.start
    _   <- IO.sleep(1 second) >> IO("Canceling!").debug >> fib.cancel
  } yield ()
  // connection + file will close automatically

  // finalizers to regular IOs
  val ioWithFinalizer: IO[String] = IO("Some resource").debug.guarantee(IO("Clean resource").debug.void)
  val ioWithFinalizer_v2: IO[String] = IO("Some resource").debug.guaranteeCase {
    case Outcome.Succeeded(fa) => fa.flatMap(result => IO(s"Releasing resource: $result").debug).void
    case Outcome.Errored(_)    => IO("Nothing to release").debug.void
    case Outcome.Canceled()    => IO("Resource got canceled, releasing what's left").debug.void
  }

  override def run: IO[Unit] = {
    // bracketReadFile("src/main/scala/com/rockthejvm/part3concurrency/Resources.scala")
    // resourceFetchUrl.void
    // resourceReadFile("src/main/scala/com/rockthejvm/part3concurrency/Resources.scala")
    // cancelReadFile("src/main/scala/com/rockthejvm/part3concurrency/Resources.scala")
    // openConnection.void
    // canceledConnection
    ioWithFinalizer.void
  }

}
