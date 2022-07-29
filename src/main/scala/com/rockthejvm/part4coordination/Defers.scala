package com.rockthejvm.part4coordination

import cats.effect.*
import cats.syntax.traverse.*
import com.rockthejvm.utils.*

import scala.concurrent.duration.*
import scala.language.postfixOps

object Defers extends IOApp.Simple {

  // deferred is a primitive for waiting for an effect, while some other effect completes with a value

  val aDeferred: IO[Deferred[IO, Int]] = Deferred[IO, Int]
  val aDeferred_v2: IO[Deferred[IO, Int]] = IO.deferred[Int]

  // get blocks the calling fiber (semantically) until some other fiber completes the Deferred with a value
  val reader: IO[Int] = aDeferred.flatMap { signal =>
    signal.get // block the fiber
  }

  val writer: IO[Boolean] = aDeferred.flatMap { signal =>
    signal.complete(42)
  }

  def demoDeferred(): IO[Unit] = {
    def consumer(signal: Deferred[IO, Int]) = for {
      _ <- IO("[Consumer] Waiting for result...").debug
      meaningOfLife <- signal.get // blocker
      _ <- IO(s"[Consumer] Got the result: $meaningOfLife").debug
    } yield ()

    def producer(signal: Deferred[IO, Int]) = for {
      _ <- IO("[Producer] Crunching numbers...").debug
      _ <- IO.sleep(1 second)
      _ <- IO("[Producer] Complete: 42").debug
      meaningOfLife <- IO(42)
      _ <- signal.complete(meaningOfLife)
    } yield ()

    for {
      signal <- Deferred[IO, Int]
      fibConsumer <- consumer(signal).start
      fibProducer <- producer(signal).start
      _ <- fibProducer.join
      _ <- fibConsumer.join
    } yield ()
  }

  // simulate downloading some content
  val fileParts: Seq[String] = List("I ", "love S", "cala", " with Cat", "s Effect!<EOF>")

  def fileNotifierWithRef(): IO[Unit] = {
    def downloadFile(contentRef: Ref[IO, String]): IO[Unit] =
      fileParts.map { part =>
        IO(s"[Downloader] Got '$part'").debug >> IO.sleep(1 second) >> contentRef.update(currentContent => currentContent + part)
      }.sequence.void

    def notifyFileComplete(contentRef: Ref[IO, String]): IO[Unit] = for {
      file <- contentRef.get
      _ <- if (file.endsWith("<EOF>")) IO("[Notifier] File download complete").debug
      else IO("[Notifier] Downloading...").debug >> IO.sleep(500 millis) >> notifyFileComplete(contentRef) // busy wait !
    } yield ()

    for {
      contentRef <- Ref[IO].of("")
      fibDownloader <- downloadFile(contentRef).start
      notifier <- notifyFileComplete(contentRef).start
      _ <- notifier.join
      _ <- fibDownloader.join
    } yield ()
  }

  // deferred works miracles for waiting
  def fileNotifierWithDeferred(): IO[Unit] = {
    def notifyFileComplete(signal: Deferred[IO, String]): IO[Unit] = for {
      _ <- IO("[Notifier] Downloading...").debug
      _ <- signal.get // blocks until the signal is  completed
      _ <- IO("[Notifier] File download complete").debug
    } yield ()

    def downloadFilePart(part: String, contentRef: Ref[IO, String], signal: Deferred[IO, String]): IO[Unit] = for {
      _ <- IO(s"[Downloader] Got '$part'").debug
      _ <- IO.sleep(1 second)
      latestContent <- contentRef.updateAndGet(currentContent => currentContent + part)
      _ <- if (latestContent.contains("<EOF>")) signal.complete(latestContent) else IO.unit
    } yield ()

    for {
      contentRef <- Ref[IO].of("")
      signal <- Deferred[IO, String]
      notifierFib <- notifyFileComplete(signal).start
      fileTasksFib <- fileParts.map(part => downloadFilePart(part, contentRef, signal)).sequence.start
      _ <- notifierFib.join
      _ <- fileTasksFib.join
    } yield ()

  }

  /**
   * Exercises:
   *  - (medium) write a small alarm notification with two simultaneous IOs
   *    - one that increments a counter every second (a clock)
   *    - one that waits for the counter to become 10, then prints a message "time's up!"
   *
   *  - (mega hard) implement racePair with Deferred.
   *    - use a Deferred which can hold an Either[outcome for ioa, outcome for iob]
   *    - start two fibers, one for each IO
   *    - on completion (with any status), each IO needs to complete that Deferred
   *      (hint: use a finalizer from the Resources lesson)
   *      (hint2: use a guarantee call to make sure the fibers complete the Deferred)
   *    - what do you do in case of cancellation (the hardest part)?
   */

  // 1
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
    }  yield ()
  }

  // 2
  type RaceResult[A, B] = Either[
    (OutcomeIO[A], FiberIO[B]), // (winner result, loser fiber)
    (FiberIO[A], OutcomeIO[B]) // (loser fiber, winner result)
  ]

  type EitherOutcome[A, B] = Either[OutcomeIO[A], OutcomeIO[B]]

  def ourRacePair[A, B](ioa: IO[A], iob: IO[B]): IO[RaceResult[A, B]] = IO.uncancelable { poll =>
    for {
      signal <- Deferred[IO, EitherOutcome[A, B]]
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
    // demoDeferred()
    // fileNotifierWithRef()
    // fileNotifierWithDeferred()
    alarm()
  }
}
