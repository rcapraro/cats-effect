package com.rockthejvm.part1recap

object ContextualAbstractionsScala3 {

  // given/using combo
  def increment(x: Int)(using amount: Int): Int = x + amount

  given defaultAmount: Int = 10

  val twelve: Int = increment(2)

  def multiply(x: Int)(using factor: Int): Int = x * factor

  val aHundred: Int = multiply(100)

  // more complex use case
  trait Combiner[A] {
    def combine(x: A, y: A): A

    def empty: A
  }

  def combineAll[A](values: List[A])(using combiner: Combiner[A]): A =
    values.foldLeft(combiner.empty)(combiner.combine)

  given intCombiner: Combiner[Int] with {
    override def empty: Int = 0

    override def combine(x: Int, y: Int): Int = x + y
  }

  val numbers: List[Int] = (1 to 10).toList
  val sum10: Int = combineAll(numbers) // intCombiner passed automatically

  // synthesize given instances
  given optionCombiner[T] (using combiner: Combiner[T]): Combiner[Option[T]] with {
    override def empty: Option[T] = Some(combiner.empty)

    override def combine(x: Option[T], y: Option[T]): Option[T] = for {
      vx <- x
      vy <- y
    } yield combiner.combine(vx, vy)
  }

  val sumOptions: Option[Int] = combineAll(List(Some(1), None, Some(2)))

  // extension methods
  case class Person(name: String) {
    def greet(): String = s"Hi, my name is $name"
  }

  extension (name: String)
    def greet(): String = Person(name).greet()

  // generic extension
  extension[T] (list: List[T])
    def reduceAll(using combiner: Combiner[T]): T =
      list.foldLeft(combiner.empty)(combiner.combine)

  val alicesGreeting: String = "Alice".greet()

  val sum10_v2: Int = numbers.reduceAll

  // type classes
  object TypeClassesScala3 {

    case class Person(name: String, age: Int)

    // part 1 - Type class definition
    trait JSONSerializer[T] {
      def toJSON(value: T): String
    }

    // part 2 - Type class instances
    given stringSerializer: JSONSerializer[String] with {
      override def toJSON(value: String): String = "\"" + value + "\""
    }

    given intSerializer: JSONSerializer[Int] with {
      override def toJSON(value: Int): String = value.toString
    }

    given personSerializer: JSONSerializer[Person] with {
      override def toJSON(person: Person): String =
        s"""
           |{"name": "${person.name}", "age": "${person.age}"}
           |""".stripMargin.trim
    }

    // part3 - user-facing API
    def convertToJSON[T](value: T)(using serializer: JSONSerializer[T]): String =
      serializer.toJSON(value)

    def convertListToJSON[T](list: List[T])(using serializer: JSONSerializer[T]): String =
      list.map(serializer.toJSON).mkString("[", ", ", "]")

    // part4 - extension methods
    extension [T](value: T)
      def toJson(using serializer: JSONSerializer[T]): String = serializer.toJSON(value)

    @main
    def test(): Unit = {
      println(convertListToJSON(List(Person("Alice", 23), Person("Bob", 46))))
      val bob = Person("Richard", 47)
      println(bob.toJson)
    }
  }

}
