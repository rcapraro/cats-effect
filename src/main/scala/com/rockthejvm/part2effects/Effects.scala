package com.rockthejvm.part2effects

object Effects {

  // pure functional programming
  // substitution
  def combine(a: Int, b: Int): Int = a + b
  val five: Int = combine(2, 3)
  val five_v2: Int = 2 + 3
  val five_v3 = 5

  // referential transparency = can replace an expression with its value
  //    as many times as we want without changing behavior

  // example: print to the console
  val printSomething: Unit = println("Cats Effect")
  val printSomething_v2: Unit = () // not the same

  // example: change a variable
  var anInt = 0
  val changingVar: Unit = (anInt += 1)
  val changingVar_v2: Unit = () // not the same

  // side effects are inevitable for useful programs

  // effect

  def main(args: Array[String]): Unit = {

  }

}
