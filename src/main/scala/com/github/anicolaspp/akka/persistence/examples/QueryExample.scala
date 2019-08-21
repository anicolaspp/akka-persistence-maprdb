package com.github.anicolaspp.akka.persistence.examples

import akka.actor.ActorSystem
import akka.persistence.query.PersistenceQuery
import akka.stream.ActorMaterializer
import com.github.anicolaspp.akka.persistence.query.MapRDBScalaReadJournal

import scala.concurrent.{Await, Future}

object QueryExample extends App {

  implicit val system = ActorSystem("example")

  implicit val mat = ActorMaterializer()

  val readJournal =
    PersistenceQuery(system).readJournalFor[MapRDBScalaReadJournal]("akka.persistence.query.journal")

  val events = readJournal.currentEventsByPersistenceId("p-1", 3, Long.MaxValue)
  Await.result(events.runForeach(println), scala.concurrent.duration.Duration.Inf)
  println("done events...")

  val boundedStream = readJournal.currentPersistenceIds().runForeach(println)
  Await.result(boundedStream, scala.concurrent.duration.Duration.Inf)
  println("done boundedStream...")

  val unboundedEventStream = readJournal
    .eventsByPersistenceId("p-1", 3, Long.MaxValue)
    .runForeach(println)

  val unboundedStream = readJournal.persistenceIds().runForeach(println)

  import scala.concurrent.ExecutionContext.Implicits.global

  Await.result(Future.sequence(List(unboundedStream, unboundedEventStream)), scala.concurrent.duration.Duration.Inf)

  println("done...")
}
