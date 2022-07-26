package com.rockthejvm.part3concurrency

import cats.effect.kernel.Outcome.{Canceled, Errored, Succeeded}
import cats.effect.{FiberIO, IO, IOApp, OutcomeIO}

import scala.concurrent.duration.*
import scala.language.postfixOps

object RacingIOs extends IOApp.Simple {

  import com.rockthejvm.utils.*

  def runWithSleep[A](value: A, duration: FiniteDuration): IO[A] =
    (IO(s"Starting computation: $value").debug >>
      IO.sleep(duration) >>
      IO(s"Computation done for $value done") >>
      IO(value)).onCancel(IO(s"Computation CANCELED for $value").debug.void)

  def testRace(): IO[String] = {
    val meaningOfLife = runWithSleep(42, 1 second)
    val favLang = runWithSleep("Scala", 2 seconds)
    val first: IO[Either[Int, String]] = IO.race(meaningOfLife, favLang)
    /*
    - both IOs run on separate fibers
    - the first one to finish will complete the result
    - the loser will be canceled
     */
    first.flatMap {
      case Left(mol) => IO(s"Meaning of life won: $mol")
      case Right(favLang) => IO(s"Favorite language won $favLang")
    }
  }

  def testRacePair(): IO[Any] = {
    val meaningOfLife = runWithSleep(42, 1 second)
    val favLang = runWithSleep("Scala", 2 seconds)
    val raceResult: IO[Either[
      (OutcomeIO[Int], FiberIO[String]), // (winner result, loser fiber)
      (FiberIO[Int], OutcomeIO[String]) // (loser fiber, winner result)
    ]] = IO.racePair(meaningOfLife, favLang)

    raceResult.flatMap {
      case Left((outMol, fibLang)) => fibLang.cancel >> IO("MOL won").debug >> IO(outMol).debug
      case Right((fibMol, outLang)) => fibMol.cancel >> IO("Language won").debug >> IO(outLang).debug
    }
  }

  /**
   * Exercises
   * 1 - Implement a timeout pattern with race
   * 2 - A method to return a LOSING effect from a race (hint: use racePair)
   * 3 - Implement race in terms of racePair
   */

  // 1
  def timeout[A](io: IO[A], duration: FiniteDuration): IO[A] = {
    IO.race(io, IO.sleep(duration)).flatMap {
      case Left(value) => IO(value)
      case Right(_) => IO.raiseError(RuntimeException("Computation timed out!"))
    }
  }

  val importantTask: IO[String] = IO.sleep(2 seconds) >> IO("42").debug
  val testTimeOut: IO[String] = timeout(importantTask, 3 seconds)
  val testTimeout_v2: IO[String] = importantTask.timeout(3 seconds)

  // 2
  def unRace[A, B](ioa: IO[A], iob: IO[B]): IO[Either[A, B]] =
    IO.racePair(ioa, iob).flatMap {
      case Left((_, fibB)) => fibB.join.flatMap {
        case Succeeded(resultEffect) => resultEffect.map(result => Right(result))
        case Errored(e) => IO.raiseError(e)
        case Canceled() => IO.raiseError(new RuntimeException("Loser canceled!"))
      }
      case Right((fibA, _)) => fibA.join.flatMap {
        case Succeeded(resultEffect) => resultEffect.map(result => Left(result))
        case Errored(e) => IO.raiseError(e)
        case Canceled() => IO.raiseError(new RuntimeException("Loser canceled!"))
      }
    }

  // 3
  def simpleRace[A, B](ioa: IO[A], iob: IO[B]): IO[Either[A, B]] =
    IO.racePair(ioa, iob).flatMap {
      case Left((outA, fibB)) => outA match {
        case Succeeded(effectA) => fibB.cancel >> effectA.map(a => Left(a))
        case Errored(e) => fibB.cancel >> IO.raiseError(e)
        case Canceled() => fibB.join.flatMap {
          case Succeeded(effectB) => effectB.map(b => Right(b))
          case Errored(e) => IO.raiseError(e)
          case Canceled() => IO.raiseError(new RuntimeException("Both computations canceled!"))
        }
      }
      case Right((fibA, outB)) => outB match {
        case Succeeded(effectB) => fibA.cancel >> effectB.map(b => Right(b))
        case Errored(e) => fibA.cancel >> IO.raiseError(e)
        case Canceled() => fibA.join.flatMap {
          case Succeeded(effectA) => effectA.map(a => Left(a))
          case Errored(e) => IO.raiseError(e)
          case Canceled() => IO.raiseError(new RuntimeException("Both computations canceled!"))
        }
      }
    }

  override def run: IO[Unit] = {
    // testRacePair().void
    // timeout(IO.sleep(2 seconds) >> IO("42").debug, 3 seconds).debug.void
    unRace(IO.sleep(2 seconds) >> IO(42).debug, IO.sleep(1 second) >> IO("scala")).debug.void
  }
}
