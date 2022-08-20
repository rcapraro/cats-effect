package com.rockthejvm.part5polymorphic

import cats.effect.*
import com.rockthejvm.utils.*

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

object PolymorphicAsync extends IOApp.Simple {

  // Async - asynchronous computations, "suspended" in F
  trait MyAsync[F[_]] extends Sync[F] with Temporal[F] {

    // fundamental description of async computation
    def executionContext: F[ExecutionContext]

    def async[A](cb: (Either[Throwable, A] => Unit) => F[Option[F[Unit]]]): F[A]

    def async_[A](cb: (Either[Throwable, A] => Unit) => Unit): F[A] =
      async(callback => map(pure(cb(callback)))(_ => None))

    def evalOn[A](fa: F[A], ec: ExecutionContext): F[A]

    def never[A]: F[A] = async_(_ => ()) // callback is never invoked
  }

  val asyncIO = Async[IO] // fetch the given/implicit Async[IO]

  // pure, map/flatMap, raiseError, uncancelable, start, ref/deferred, sleep, delay/defer/blocking, +
  val ec: IO[ExecutionContext] = asyncIO.executionContext

  // power: async_ + async: FFI (Foreign Function Interface)
  val threadPool: ExecutorService = Executors.newFixedThreadPool(10)
  type Callback[A] = Either[Throwable, A] => Unit
  val asyncMeaningOfLife: IO[Int] = IO.async_ { (cb: Callback[Int]) =>
    // start computation on some other threadpool
    threadPool.execute { () =>
      println(s"[${Thread.currentThread().getName}] Computing an async MOL")
      cb(Right(42))
    }
  }

  val asyncMeaningOfLife_v2: IO[Int] = asyncIO.async_ { (cb: Callback[Int]) =>
    // start computation on some other threadpool
    threadPool.execute { () =>
      println(s"[${Thread.currentThread().getName}] Computing an async MOL")
      cb(Right(42))
    }
  } // same

  val asyncMeaningOfLifeComplex: IO[Int] = IO.async { (cb: Callback[Int]) =>
    IO {
      threadPool.execute { () =>
        println(s"[${Thread.currentThread().getName}] Computing an async MOL")
        cb(Right(42))
      }
    }.as(
      Some(IO("Cancelled").debug.void)
    ) // <-- finalizer in case the computation gets cancelled
  }

  val asyncMeaningOfLifeComplex_v2: IO[Int] = asyncIO.async { (cb: Callback[Int]) =>
    IO {
      threadPool.execute { () =>
        println(s"[${Thread.currentThread().getName}] Computing an async MOL")
        cb(Right(42))
      }
    }.as(
      Some(IO("Cancelled").debug.void)
    ) // <-- finalizer in case the computation gets cancelled
  }   // same

  val myExecutionContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(threadPool)
  val asyncMeaningOfLife_v3: Unit = asyncIO
    .evalOn(IO(42).debug, myExecutionContext)
    .guarantee(IO(threadPool.shutdown()))

  // never
  val neverIO: IO[Unit] = asyncIO.never

  /*
  Exercises
  1 - implement never and async in terms of the big async.
  2 - tuple two effect with different requirements.
   */

  def firstEffect[F[_]: Concurrent, A](a: A): F[A] = Concurrent[F].pure(a)
  def secondEffect[F[_]: Sync, A](a: A): F[A]      = Sync[F].pure(a)

  import cats.syntax.functor.* // map extension method
  import cats.syntax.flatMap.* // flatMap extension method
  def tupledEffect[F[_]: Async, A](a: A): F[(A, A)] = for {
    f1 <- firstEffect(a)
    f2 <- secondEffect(a)
  } yield (f1, f2)

  override def run: IO[Unit] = ???

}
