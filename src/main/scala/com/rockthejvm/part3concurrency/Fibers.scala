package com.rockthejvm.part3concurrency

import cats.effect.*
import cats.effect.IO.{IOCont, Uncancelable}
import cats.effect.kernel.Outcome.{Canceled, Errored, Succeeded}

import scala.concurrent.duration.*
import scala.language.postfixOps

object Fibers extends IOApp.Simple {

  val meaningOfLife: IO[Int] = IO.pure(42)
  val favLang: IO[String]    = IO.pure("Scala")

  // runs sequentially

  import com.rockthejvm.utils.*

  def sameThreadIOs: IO[Unit] = for {
    _ <- meaningOfLife.debug
    _ <- favLang.debug
  } yield ()

  // introduce the Fiber
  def createFiber: Fiber[IO, Throwable, String] = ??? // almost impossible to create fibers manually

  // FiberIO[Int] alias for Fiber[IO, Throwable, Int]
  // The fiber is not actually started, but the fiber allocation iw wrapped in another effect
  val aFiber: IO[FiberIO[Int]] = meaningOfLife.debug.start

  def differentThreadIOs(): IO[Unit] = for {
    _ <- aFiber
    _ <- favLang.debug
  } yield ()

  // joining a fiber
  // Outcome[IO, Throwable, A] is an alias for OutcomeIO[A]
  def runOnSomeOtherThread[A](io: IO[A]): IO[OutcomeIO[A]] = for {
    fib    <- io.start
    result <- fib.join // also an effect which waits for the fiber to terminate
  } yield result

  /*
  possible outcomes:
  - success with an IO
  - failure with an exception
  - cancelled
   */

  val someIOOnAnotherThread: IO[OutcomeIO[Int]] = runOnSomeOtherThread(meaningOfLife)
  val someResultFromAnotherThread: IO[Int] = someIOOnAnotherThread.flatMap {
    case Succeeded(effect) => effect
    case Errored(_)        => IO(0)
    case Canceled()        => IO(0)
  }

  def throwOnAnotherThread(): IO[OutcomeIO[Int]] = for {
    fib    <- IO.raiseError[Int](new RuntimeException("No number for you")).start
    result <- fib.join
  } yield result

  def testCancel(): IO[OutcomeIO[String]] = {
    val task                        = IO("starting").debug >> IO.sleep(1 seconds) >> IO("done").debug
    val taskWithCancellationHandler = task.onCancel(IO("I'm being cancelled!").debug.void)

    for {
      fib    <- taskWithCancellationHandler.start              // on a separate thread
      _      <- IO.sleep(500 millis) >> IO("cancelling").debug // running on the calling thread
      _      <- fib.cancel
      result <- fib.join
    } yield result
  }

  /** Exercises:
    *   1. Write a function that runs an IO on another thread, and, depending on the result of the fiber
    *      - return the result in an IO
    *      - if errored or cancelled, return a failed IO
    *
    * 2. Write a function that takes two IOs, runs them on different fibers and returns an IO with a tuple containing both results.
    *   - if both IOs complete successfully, tuple their results
    *   - if the first IO returns an error, raise that error (ignoring the second IO's result/error)
    *   - if the first IO doesn't error but second IO returns an error, raise that error
    *   - if one (or both) canceled, raise a RuntimeException
    *
    * 3. Write a function that adds a timeout to an IO:
    *   - IO runs on a fiber
    *   - if the timeout duration passes, then the fiber is canceled
    *   - the method returns an IO[A] which contains
    *     - the original value if the computation is successful before the timeout signal
    *     - the exception if the computation is failed before the timeout signal
    *     - a RuntimeException if it times out (i.e. cancelled by the timeout)
    */

  // 1
  def processResultsFromFiber[A](io: IO[A]): IO[A] =
    (for {
      fib    <- io.debug.start
      result <- fib.join
    } yield result)
      .flatMap {
        case Succeeded(fa) => fa
        case Errored(e)    => IO.raiseError(e)
        case Canceled()    => IO.raiseError(new RuntimeException("Computation canceled!"))
      }

  def testEx1(): IO[Unit] = {
    val aComputation = IO("starting").debug >> IO.sleep(1 second) >> IO("done!").debug >> IO(42)
    processResultsFromFiber(aComputation).void
  }

  // 2
  def tupleIOs[A, B](ioa: IO[A], iob: IO[B]): IO[(A, B)] = {
    val result = for {
      fib_a    <- ioa.start
      fib_b    <- iob.start
      result_a <- fib_a.join
      result_b <- fib_b.join
    } yield (result_a, result_b)

    result.flatMap {
      case (Succeeded(fa), Succeeded(fb)) =>
        for {
          a <- fa
          b <- fb
        } yield (a, b)
      case (Errored(e), _) => IO.raiseError(e)
      case (_, Errored(e)) => IO.raiseError(e)
      case _               => IO.raiseError(new RuntimeException("Some computations canceled!"))
    }
  }

  def testEx2(): IO[Unit] = {
    val firstIO  = IO.sleep(2 seconds) >> IO(1).debug
    val secondIO = IO.sleep(3 seconds) >> IO(2).debug
    tupleIOs(firstIO, secondIO).debug.void
  }

  // 3
  def timeOut[A](io: IO[A], duration: FiniteDuration): IO[A] = {
    val computation = for {
      fib    <- io.start
      _      <- (IO.sleep(duration) >> fib.cancel).start // careful - fibers (their resource to be exact) can leak
      result <- fib.join
    } yield result

    computation.flatMap {
      case Succeeded(fa) => fa
      case Errored(e)    => IO.raiseError(e)
      case Canceled()    => IO.raiseError(new RuntimeException("Computation canceled!"))
    }
  }

  def testEx3(): IO[Unit] = {
    val aComputation = IO("starting").debug >> IO.sleep(1 second) >> IO("done!").debug >> IO(42)
    timeOut(aComputation, 500 millis).debug.void
  }

  override def run: IO[Unit] = {
    /*
    runOnSomeOtherThread(meaningOfLife) // IO(Succeeded(IO(42)))
      .debug
      .void

    throwOnAnotherThread()
      .debug
      .void

    testCancel()
      .debug
      .void
     */

    // testEx1()

    // testEx2()

    testEx3()

  }

}
