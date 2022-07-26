package com.rockthejvm.part3concurrency

import cats.effect.{IO, IOApp}
import com.rockthejvm.utils.*

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.language.postfixOps

object BlockingIOs extends IOApp.Simple {

  val someSleeps: IO[Unit] = for {
    _ <- IO.sleep(1 second).debug // SEMANTIC BLOCKING - no actual thread is blocked
    _ <- IO.sleep(1 second).debug
  } yield ()

  // really blocking IOs
  val aBlockingIO: IO[Int] = IO.blocking {
    Thread.sleep(1000)
    println(s"[${Thread.currentThread().getName}] computed a blocking code")
    42
  } // will evaluate on a thread from ANOTHER thread pool specific for blocking calls

  // yielding
  val iosOnManyThreads: IO[Unit] = for {
    _ <- IO("first").debug
    _ <- IO.cede // a signal to yield control over a thread
    _ <- IO("second").debug
    _ <- IO.cede
    _ <- IO("third").debug
  } yield ()

  def testThousandEffectsSwitch(): IO[Int] = {
    val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))
    (1 to 1_000).map(IO.pure).reduce(_.debug >> IO.cede >> _.debug).evalOn(ec)
  }

  /*
  - blocking calls & IO.sleep yield control over the calling thread automatically
   */

  override def run: IO[Unit] = testThousandEffectsSwitch().void
}
