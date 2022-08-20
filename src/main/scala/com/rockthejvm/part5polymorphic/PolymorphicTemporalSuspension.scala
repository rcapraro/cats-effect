package com.rockthejvm.part5polymorphic

import cats.effect.kernel.Concurrent
import cats.effect.{IO, IOApp, Temporal}
import com.rockthejvm.utils.general.*

import scala.concurrent.duration.*
import scala.language.postfixOps

object PolymorphicTemporalSuspension extends IOApp.Simple {

  // Temporal - time-blocking effects
  trait MyTemporal[F[_]] extends Concurrent[F] {
    def sleep(time: FiniteDuration): F[Unit] // semantically blocks this fiber for a specified time
  }

  // abilities: pure, map/flatMap, raiseError, uncancelable, start, ref/deferred, +sleep
  val temporalIO: Temporal[IO] = Temporal[IO] // given Temporal[IO] in scope

  val chainOfEffects: IO[String] = IO("Loading...").debug *> IO.sleep(1 second) *> IO("Game ready!").debug

  val chainOfEffects_v2: IO[String] = temporalIO.pure("Loading").debug *> temporalIO.sleep(1 second) *> temporalIO.pure("Game ready!").debug

  /*
  Exercise - generalize the timeout form RacingIos lesson
   */
  import cats.syntax.flatMap.*
  def timeout[F[_], A](fa: F[A], duration: FiniteDuration)(using temporal: Temporal[F]): F[A] = {
    temporal.race(fa, temporal.sleep(duration)).flatMap {
      case Left(value) => temporal.pure(value)
      case Right(_)    => temporal.raiseError(RuntimeException("Computation timed out!"))
    }
  }

  override def run: IO[Unit] = ???

}
