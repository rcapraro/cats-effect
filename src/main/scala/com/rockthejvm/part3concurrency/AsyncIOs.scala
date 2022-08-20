package com.rockthejvm.part3concurrency

import cats.effect.{IO, IOApp}
import com.rockthejvm.part3concurrency.AsyncIOs.threadPool
import com.rockthejvm.utils.*

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object AsyncIOs extends IOApp.Simple {

  // IOs can run asynchronously on fibers, without having to manually manage their lifecycle
  val threadPool: ExecutorService = Executors.newFixedThreadPool(8)

  given ec: ExecutionContext = ExecutionContext.fromExecutorService(threadPool)

  type Callback[A] = Either[Throwable, A] => Unit

  def computeMeaningOfLife(): Int = {
    Thread.sleep(1_000)
    println(s"[${Thread.currentThread().getName}] Computing the meaning of life on some other thread...")
    42
  }

  def computeMeaningOfLifeEither(): Either[Throwable, Int] = Try(computeMeaningOfLife()).toEither

  def computeMolOnThreadPool(): Unit = threadPool.execute(() => computeMeaningOfLife())

  // lift computation to an IO
  // async is a FFI (Foreign Function Interface)
  val asyncMolIO: IO[Int] = IO.async_ { cb => // CE thread blocks (semantically) until this cb is invoked (by some other thread)
    threadPool.execute { () => // computation not managed by CE
      val result = computeMeaningOfLifeEither()
      cb(result) // CE thread is notified with the result
    }
  }

  /** Exercise: lift an async computation on ec to an IO.
    */
  def asyncToIO[A](computation: () => A)(using ec: ExecutionContext): IO[A] =
    IO.async_(cb => ec.execute(() => cb(Try(computation()).toEither)))

  val asyncMolIO_v2: IO[Int] = asyncToIO(computeMeaningOfLife)

  /** Exercise: Lift an async computation as a Future, to an IO.
    */

  def convertFutureToIO[A](future: => Future[A]): IO[A] =
    IO.async_(cb => future.onComplete(tryResult => cb(tryResult.toEither)))

  lazy val molFuture: Future[Int] = Future(computeMeaningOfLife())
  val asyncMolIO_v3: IO[Int]      = convertFutureToIO(molFuture)
  val asyncMolIO_v4: IO[Int]      = IO.fromFuture(IO(molFuture))

  /** Exercise: Create a never-ending IO.
    */
  val neverEndingIO: IO[Int]    = IO.async_[Int] { _ => () } // no callback, no finish
  val neverEndingIO_v2: IO[Int] = IO.never[Int]

  // Full async call
  def demoAsyncCancellation(): IO[Unit] = {
    val asyncMeaningOfLifeIO_v2: IO[Int] = IO.async { (cb: Callback[Int]) =>
      /*
      finalizer in case computation gets cancelled.
      finalizers are of type IO[Unit]
      not specifying finalizer => Option[IO[Unit]]
      creating Option is an effect => IO[Option[IO[Unit]]]
       */
      // return IO[Option[IO[Unit]]]
      IO {
        threadPool.execute { () =>
          val result = computeMeaningOfLifeEither()
          cb(result)
        }
      }.as(Some(IO("Cancelled!").debug.void))
    }

    for {
      fib <- asyncMeaningOfLifeIO_v2.start
      _   <- IO.sleep(500 millis) >> IO("Cancelling...").debug >> fib.cancel
      _   <- fib.join
    } yield ()
  }

  override def run: IO[Unit] = {
    // asyncMolIO_v4.debug >> IO(threadPool.shutdown())
    demoAsyncCancellation().debug >> IO(threadPool.shutdown())
  }

}
