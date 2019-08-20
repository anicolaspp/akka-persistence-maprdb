package com.github.anicolaspp.akka.persistence.examples

import akka.actor.ActorSystem
import akka.persistence.query.PersistenceQuery
import akka.stream.ActorMaterializer
import com.github.anicolaspp.akka.persistence.query.MapRDBScalaReadJournal

import scala.concurrent.Await

object QueryExample extends App {

  implicit val system = ActorSystem("example")

  implicit val mat = ActorMaterializer()

  val readJournal =
    PersistenceQuery(system).readJournalFor[MapRDBScalaReadJournal]("akka.persistence.query.journal")

  val events = readJournal.currentEventsByPersistenceId("p-1", 3, Long.MaxValue)


  Await.result(events.runForeach(println), scala.concurrent.duration.Duration.Inf)

  val boundedStream = readJournal.currentPersistenceIds().runForeach(println)

  val unboundedStream = readJournal.persistenceIds().runForeach(println)

  Await.result(boundedStream, scala.concurrent.duration.Duration.Inf)
  Await.result(unboundedStream, scala.concurrent.duration.Duration.Inf)
}
