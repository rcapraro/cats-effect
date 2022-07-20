package com.rockthejvm.playground

import cats.effect.{IO, IOApp}

object Playground extends IOApp.Simple{

  override def run: IO[Unit] = IO.println("Learning cats effect")

}
