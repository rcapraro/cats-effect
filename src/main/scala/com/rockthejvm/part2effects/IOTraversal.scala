package com.rockthejvm.part2effects

import cats.effect.{IO, IOApp}

import scala.concurrent.Future
import scala.util.Random

object IOTraversal extends IOApp.Simple{

  import scala.concurrent.ExecutionContext.Implicits.global

  def heavyComputation(string: String): Future[Int] = Future {
    Thread.sleep(Random.nextInt(1000))
    string.split(" ").length
  }

  val workload: List[String] = List(
    "I quite like CE",
    "Scala is great",
    "looking forward to some awesome stuff"
  )

  def clunkyFutures(): Unit = {
    val futures: List[Future[Int]] = workload.map(heavyComputation)
    // Future[List[Int]] would be hard to obtain
    futures.foreach(_.foreach(println))
  }

  import cats.Traverse
  import cats.instances.list.*
  val listTraverse: Traverse[List] = Traverse[List]

  def traverseFutures(): Unit = {
    // traverse
    import cats.instances.list.*
    val listTraverse = Traverse[List]
    val singleFuture: Future[List[Int]] = listTraverse.traverse(workload)(heavyComputation)
    // this stores ALL the result
    singleFuture.foreach(println)
  }

  import com.rockthejvm.utils.*
  // traverse for IO
  def computeAsIO(string: String): IO[Int] = IO {
    Thread.sleep(Random.nextInt(1000))
    string.split(" ").length
  }.debug

  val ios: List[IO[Int]] = workload.map(computeAsIO)
  val singleIO: IO[List[Int]] = listTraverse.traverse(workload)(computeAsIO)

  // parallel traversal
  import cats.syntax.parallel.*
  val parallelSingleIO: IO[List[Int]] = workload.parTraverse(computeAsIO)

  /**
   * Exercises
   */
  // hint use the Traverse API
  def sequence[A](listOfIOs: List[IO[A]]): IO[List[A]] =
    listTraverse.traverse(listOfIOs)(identity)

  // hard version
  def sequence_v2[F[_]: Traverse, A](wrapperOfIOs: F[IO[A]]): IO[F[A]] =
    Traverse[F].traverse(wrapperOfIOs)(identity)

  // parallel version
  def parSequence[A](listOfIOs: List[IO[A]]): IO[List[A]] =
    listOfIOs.parTraverse(identity)

  // hard version
  def parSequence_v2[F[_]: Traverse, A](wrapperOfIOs: F[IO[A]]): IO[F[A]] = {
    wrapperOfIOs.parTraverse(identity)
  }

  // existing sequence API
  val singleIO_v2: IO[List[Int]] = listTraverse.sequence(ios)
  val parallelSingleIO_v2: IO[List[Int]] = ios.parSequence // extension method from the 'cats.syntax.parallel.*' package

  override def run: IO[Unit] =
    parallelSingleIO.map(_.sum).debug.void

}
