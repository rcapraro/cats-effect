package com.rockthejvm.part1recap

object CatsTypeClasses {

  /*
  - applicative
  - functor
  - flatMap
  - monad
  - apply
  - applicativeError/monadError
  - traverse
   */

  // functor - "mappable" data structure
  trait MyFunctor[F[_]] {
    def map[A, B](initialValue: F[A])(f: A => B): F[B]
  }

  import cats.Functor
  import cats.instances.list.*

  val listFunctor: Functor[List] = Functor[List]

  // generalizable "mapping" APIs
  def increment[F[_]](container: F[Int])(using functor: Functor[F]): F[Int] =
    functor.map(container)(_ + 1)

  // enable extension methods

  import cats.syntax.functor.*

  def increment_v2[F[_]: Functor](container: F[Int]): F[Int] =
    container.map(_ + 1)

  // applicatives - the ability to "wrap" types

  trait MyApplicative[F[_]] extends MyFunctor[F] {
    def pure[A](value: A): F[A]
  }

  import cats.Applicative

  val applicativeList: Applicative[List] = Applicative[List]
  val aSimpleList: List[Int]             = applicativeList.pure(42)

  import cats.syntax.applicative.* // imports the pure extension method

  val aSimpleList_v2: List[Int] = 42.pure[List]

  // flatMap - the ability to chain multiple wrapper computations
  trait MyFlatMap[F[_]] extends MyFunctor[F] {
    def flatMap[A, B](container: F[A])(f: A => F[B]): F[B]
  }

  import cats.FlatMap

  val flatMapList: FlatMap[List] = FlatMap[List]

  import cats.syntax.flatMap.* // flatmap extension method

  def crossProduct[F[_]: FlatMap, A, B](fa: F[A], fb: F[B]): F[(A, B)] =
    fa.flatMap(a => fb.map(b => (a, b)))

  // Monad - applicative + flatMap
  trait MyMonad[F[_]] extends MyApplicative[F] with MyFlatMap[F] {
    override def map[A, B](initialValue: F[A])(f: A => B): F[B] =
      flatMap(initialValue)(a => pure(f(a)))
  }

  import cats.Monad
  val monadList: Monad[List] = Monad[List]
  def crossProduct_v2[F[_]: Monad, A, B](fa: F[A], fb: F[B]): F[(A, B)] =
    for {
      a <- fa
      b <- fb
    } yield (a, b)

  // error-like type classes in cats

  trait MyApplicativeError[F[_], E] extends MyApplicative[F] {
    def raiseError[A](e: E): F[A]
  }

  import cats.ApplicativeError
  type ErrorOr[A] = Either[String, A]

  val appErrorEither: ApplicativeError[ErrorOr, String] = ApplicativeError[ErrorOr, String]
  val desirableValue: ErrorOr[Int]                      = appErrorEither.pure(42)
  val failedValue: ErrorOr[Int]                         = appErrorEither.raiseError("Something failed")

  import cats.syntax.applicativeError.* // raiseError extension method
  val failedValue_v2: ErrorOr[Int] = "Something failed".raiseError[ErrorOr, Int]

  trait MyMonadError[F[_], E] extends MyApplicativeError[F, E] with Monad[F]

  import cats.MonadError
  val monadErrorEither: MonadError[ErrorOr, String] = MonadError[ErrorOr, String]

  // traverse
  trait MyTraverse[F[_]] extends MyFunctor[F] {
    def traverse[G[_], A, B](container: F[A])(f: A => G[B]): G[F[B]]
  }

  // turn nested wrappers inside out
  val listOfOptions: List[Option[Int]] = List(Some(1), Some(2), Some(42))

  import cats.Traverse
  val listTraverse: Traverse[List]  = Traverse[List]
  val optionList: Option[List[Int]] = listTraverse.traverse(List(1, 2, 3))(x => Option(x))

  import cats.syntax.traverse.*
  val optionList_v2: Any = List(1, 2, 3).traverse(x => Option(x))

}
