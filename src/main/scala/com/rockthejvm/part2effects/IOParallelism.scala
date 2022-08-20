package com.rockthejvm.part2effects

import cats.Parallel
import cats.effect.IO.Par
import cats.effect.{IO, IOApp}

object IOParallelism extends IOApp.Simple {

  // IOs are usually sequential
  val aniIO: IO[String]    = IO(s"[${Thread.currentThread().getName}] Ani")
  val kamranIO: IO[String] = IO(s"[${Thread.currentThread().getName}] Kamran")

  val composedIO: IO[String] = for {
    ani    <- aniIO
    kamran <- kamranIO
  } yield s"$ani and $kamran love Rock the JVM"

  // debug extension method
  import com.rockthejvm.utils.*
  // mapN extension method
  import cats.syntax.apply.*

  val meaningOfLife: IO[Int]       = IO.delay(42)
  val favoriteLanguage: IO[String] = IO.delay("Scala")

  val goalInLife: IO[String] = (meaningOfLife.debug, favoriteLanguage.debug).mapN((num, str) => s"My goal in life is $num and $str")

  // parallelism on IOs
  // convert a sequential IO to a parallel IO
  val parIO1: IO.Par[Int]    = Parallel[IO].parallel(meaningOfLife.debug)
  val parIO2: IO.Par[String] = Parallel[IO].parallel(favoriteLanguage.debug)

  import cats.effect.implicits.*
  val goalInLifeParallel: IO.Par[String] = (parIO1, parIO2).mapN((num, str) => s"My goal in life is $num and $str")

  // turn back to sequential
  val goalInLife_v2: IO[String] = Parallel[IO].sequential(goalInLifeParallel)

  // shorthand
  import cats.syntax.parallel.*
  val goalInLife_v3: IO[String] = (meaningOfLife.debug, favoriteLanguage.debug).parMapN((num, str) => s"My goal in life is $num and $str")

  // regarding failure
  val aFailure: IO[String] = IO.raiseError(new RuntimeException("First failure"))
  // compose a success and a failure
  val parallelWithFailure: IO[String] = (meaningOfLife.debug, aFailure.debug).parMapN(_ + _)
  // compose a failure with another failure
  val anotherFailure: IO[String] = IO.raiseError(new RuntimeException("Second failure"))
  val twoFailures: IO[String]    = (aFailure.debug, anotherFailure.debug).parMapN(_ + _)
  // the first effect to fail gives the failure of the result
  val twoFailuresDelayed: IO[String] = (IO(Thread.sleep(1000)) >> aFailure.debug, anotherFailure.debug).parMapN(_ + _)

  override def run: IO[Unit] =
    // goalInLife_v3.debug.void
    twoFailuresDelayed.debug.void

}
