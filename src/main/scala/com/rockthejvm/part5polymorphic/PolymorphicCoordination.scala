package com.rockthejvm.part5polymorphic

import cats.effect.*

object PolymorphicCoordination extends IOApp.Simple {

  // Concurrent - Ref + Deferred for ANY effect type
  trait MyConcurrent[F[_]] extends Spawn[F] {

    def deferred[A]: F[Deferred[F, A]]
  }

  val concurrentIO: Concurrent[IO] = Concurrent[IO] // fetch the given instance of Concurrent[IO]

  val aDeferred: IO[Deferred[IO, Int]] = Deferred[IO, Int] // requite the presence of a given Concurrent[IO] in scope

  val aDeferred_v2: IO[Deferred[IO, Int]] = concurrentIO.deferred[Int]

  val aRef: IO[Ref[IO, Int]] = concurrentIO.ref(42)

  // capabilities: pure, map/flatMap, raiseError, uncancelable, start (fibers), +ref/deferred

  import com.rockthejvm.utils.general.*

  import scala.concurrent.duration.*
  import scala.language.postfixOps

  def alarm(): IO[Unit] = {
    def notifyAlarm(signal: Deferred[IO, Unit]): IO[Unit] = for {
      _ <- IO("[Notifier] Counter on some other fiber, waiting...").debug
      _ <- signal.get
      _ <- IO("[Notifier] ALARM!").debug
    } yield ()


    def tickingCounter(counter: Ref[IO, Int], signal: Deferred[IO, Unit]): IO[Unit] = for {
      _ <- IO.sleep(1 second)
      count <- counter.updateAndGet(_ + 1)
      _ <- IO(s"[Counter] $count").debug
      _ <- if (count >= 10) signal.complete(()) else tickingCounter(counter, signal)
    } yield ()

    for {
      counter <- Ref[IO].of(0)
      signal <- Deferred[IO, Unit]
      notificationFib <- notifyAlarm(signal).start
      tickingCounterFib <- tickingCounter(counter, signal).start
      _ <- notificationFib.join
      _ <- tickingCounterFib.join
    } yield ()
  }

  import cats.effect.syntax.spawn.*
  import cats.syntax.flatMap.*
  import cats.syntax.functor.* // start extension method

  def polymorphicAlarm[F[_]](using concurrent: Concurrent[F]): F[Unit] = {
    def notifyAlarm(signal: Deferred[F, Unit]): F[Unit] = for {
      _ <- concurrent.pure("[Notifier] Counter on some other fiber, waiting...").debug
      _ <- signal.get
      _ <- concurrent.pure("[Notifier] ALARM!").debug
    } yield ()


    def tickingCounter(counter: Ref[F, Int], signal: Deferred[F, Unit]): F[Unit] = for {
      _ <- unsafeSleep[F, Throwable](1 second)
      count <- counter.updateAndGet(_ + 1)
      _ <- concurrent.pure(s"[Counter] $count").debug
      _ <- if (count >= 10) signal.complete(()).void else tickingCounter(counter, signal)
    } yield ()

    for {
      counter <- concurrent.ref(0)
      signal <- concurrent.deferred[Unit]
      notificationFib <- notifyAlarm(signal).start
      tickingCounterFib <- tickingCounter(counter, signal).start
      _ <- notificationFib.join
      _ <- tickingCounterFib.join
    } yield ()
  }

  /*
  Exercises
    1. Generalize racePair
    2. Generalize the Mutex concurrency primitive for any F
  */
  type RaceResult[F[_], A, B] = Either[
    (Outcome[F, Throwable, A], Fiber[F, Throwable, B]), // (winner result, loser fiber)
    (Fiber[F, Throwable, A], Outcome[F, Throwable, B]) // (loser fiber, winner result)
  ]

  type EitherOutcome[F[_], A, B] = Either[Outcome[F, Throwable, A], Outcome[F, Throwable, B]]

  import cats.effect.syntax.monadCancel.* //guaranteeCase extension method
  import cats.effect.syntax.spawn.* // start extension method

  def ourRacePair[F[_], A, B](ioa: F[A], iob: F[B])(using concurrent: Concurrent[F]): F[RaceResult[F, A, B]] =
    concurrent.uncancelable { poll =>
      for {
        signal <- concurrent.deferred[EitherOutcome[F, A, B]]
        fibA <- ioa.guaranteeCase(outcomeA => signal.complete(Left(outcomeA)).void).start
        fibB <- iob.guaranteeCase(outcomeB => signal.complete(Right(outcomeB)).void).start
        result <- poll(signal.get).onCancel { // blocking call - should be cancelable
          for {
            cancelFibA <- fibA.cancel.start
            cancelFibB <- fibB.cancel.start
            _ <- cancelFibA.join
            _ <- cancelFibB.join
          } yield ()
        }
      } yield result match {
        case Left(outcomeA) => Left((outcomeA, fibB))
        case Right(outcomeB) => Right((fibA, outcomeB))
      }
    }

  override def run: IO[Unit] = {
    polymorphicAlarm[IO]
  }

}
