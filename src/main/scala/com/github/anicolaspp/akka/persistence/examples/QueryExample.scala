package com.github.anicolaspp.akka.persistence.examples

import akka.actor.ActorSystem
import akka.persistence.query.{Offset, PersistenceQuery}
import akka.stream.ActorMaterializer
import com.github.anicolaspp.akka.persistence.query.MapRDBScalaReadJournal

import scala.concurrent.{Await, Future}

object QueryExample extends App {

  implicit val system = ActorSystem("example")

  implicit val mat = ActorMaterializer()

  val readJournal =
    PersistenceQuery(system).readJournalFor[MapRDBScalaReadJournal]("akka.persistence.query.journal")


  val currentEventsByTag = readJournal.currentEventsByTag("boy", Offset.noOffset).runForeach(println)

  Await.result(currentEventsByTag, scala.concurrent.duration.Duration.Inf)

//  val events = readJournal.currentEventsByPersistenceId("sample-id-3", 3, Long.MaxValue)
//  Await.result(events.runForeach(println), scala.concurrent.duration.Duration.Inf)
  println("done events...")



//  val unboundedEventStream = readJournal
//    .eventsByPersistenceId("sample-id-3", 3, Long.MaxValue)
//    .runForeach(println)
//
//  val unboundedStream = readJournal.persistenceIds().runForeach(println)
//
//  import scala.concurrent.ExecutionContext.Implicits.global
//
//  Await.result(Future.sequence(List(unboundedStream, unboundedEventStream)), scala.concurrent.duration.Duration.Inf)
//
//  println("done...")
}
