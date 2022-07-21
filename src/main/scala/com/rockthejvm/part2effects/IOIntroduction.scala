package com.rockthejvm.part2effects

import cats.effect.IO

import scala.io.StdIn

object IOIntroduction {

  // IO
  val ourFirstIO: IO[Int] = IO.pure(42) // argument that should not have side effect
  val oDelayedIO: IO[Int] = IO.delay({
    println("I'm producing an Integer")
    51
  })

  // apply == delay
  val aDelayedIO_v2: IO[Int] = IO {
    println("I'm producing an Integer")
    51
  }

  // map and flatMap
  val improvedMeaningOfLife: IO[Int] = ourFirstIO.map(_ * 2)
  val printedMeaningOfLife: IO[Unit] = ourFirstIO.flatMap(mol => IO.delay(println(mol)))

  def smallProgram(): IO[Unit] = for {
    line1 <- IO(StdIn.readLine())
    line2 <- IO(StdIn.readLine())
    _ <- IO.delay(println(s"$line1 $line2"))
  } yield ()

  // mapN from cats Apply - combine IO effects as Tuples

  import cats.syntax.apply.*

  val combinedMeaningOfLife: IO[Int] = (ourFirstIO, improvedMeaningOfLife).mapN(_ + _)

  def smallProgram_v2(): IO[Unit] =
    (IO(StdIn.readLine()), IO(StdIn.readLine())).mapN(_ + _).map(println)

  /**
   * Exercises
   */

  // 1 - sequence two IOs and take the result of the LAST one
  // hint: use flatMap
  def sequenceTakeLast[A, B](ioa: IO[A], iob: IO[B]): IO[B] =
    ioa.flatMap(_ => iob)

  def sequenceTakeLast_v2[A, B](ioa: IO[A], iob: IO[B]): IO[B] =
    ioa *> iob // andThen

  def sequenceTakeLast_v3[A, B](ioa: IO[A], iob: IO[B]): IO[B] =
    ioa >> iob // andThen with by-name call

  // 2 - sequence two IOs and take the result of the FIRST one
  // hint: use flatMap
  def sequenceTakeFirst[A, B](ioa: IO[A], iob: IO[B]): IO[A] =
    ioa.flatMap(a => iob.map(_ => a))

  def sequenceTakeFirst_v2[A, B](ioa: IO[A], iob: IO[B]): IO[A] =
    ioa <* iob

  // 3 - repeat an IO effect forever
  // hint: use flatMap + recursion
  def forever[A](io: IO[A]): IO[A] = io.flatMap(_ => forever(io))

  // gives stack_overflow
  def forever_v2[A](io: IO[A]): IO[A] = io *> forever_v2(io)

  // better - lazy
  def forever_v3[A](io: IO[A]): IO[A] = io >> forever_v3(io)

  // with tail recursion
  def forever_v4[A](io: IO[A]): IO[A] = io.foreverM

  // 4 - convert an IO to a different type
  // hint: use map
  def convert[A, B](ioa: IO[A], value: B): IO[B] =
    ioa.map(_ => value)

  def convert_v2[A, B](ioa: IO[A], value: B): IO[B] =
    ioa.as(value)

  // 5 - discard value inside an IO, just return Unit
  def asUnit[A](ioa: IO[A]): IO[Unit] = convert(ioa, ())

  def asUnit_v2[A](ioa: IO[A]): IO[Unit] = ioa.as(()) // not readable

  def asUnit_v3[A](ioa: IO[A]): IO[Unit] = ioa.void // much better

  // 6 - fix stack recursion
  def sum(n: Int): Int =
    if (n <= 0) 0
    else n + sum(n - 1)

  def sumIO(n: Int): IO[Int] =
    if (n <= 0) IO(0)
    else for {
      lastNumber <- IO(n)
      prevSum <- sumIO(n - 1)
    } yield prevSum + lastNumber
  // based on FlatMap (TC) chains so stack safe !

  // 7 (hard) - write a fibonacci IO that does not crash on recursion
  // hints: use recursion, ignore exponential complexity, use flatMap heavily
  def fibonacciIO(n: Int): IO[BigInt] =
    if (n < 2) IO(1)
    else for {
      last <- IO.defer(fibonacciIO(n - 1)) // same as IO(...).flatten (or IO.delay(...).flatten)
      prev <- IO.defer(fibonacciIO(n - 2))
    } yield last + prev

  def main(args: Array[String]): Unit = {
    import cats.effect.unsafe.implicits.global // "platform"
    // "end of the world"
    // println(smallProgram_v2().unsafeRunSync())

    /*forever_v3(IO({
      println("forever...")
      Thread.sleep(500)
    })).unsafeRunSync()*/

    println(sumIO(20000).unsafeRunSync())
    println(fibonacciIO(10).unsafeRunSync())

  }

}
