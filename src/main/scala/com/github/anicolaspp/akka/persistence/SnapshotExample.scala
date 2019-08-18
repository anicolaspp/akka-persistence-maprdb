package com.github.anicolaspp.akka.persistence

import akka.actor.{ActorSystem, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

object SnapshotExample extends App {
  final case class ExampleState(received: List[String] = Nil) {
    def updated(s: String): ExampleState = copy(s :: received)
    override def toString = received.reverse.toString
  }

  class ExamplePersistentActor extends PersistentActor {
    def persistenceId: String = "sample-id-3"

    var state = ExampleState()

    def receiveCommand: Receive = {
      case "print"                               => println("current state = " + state)
      case "snap"                                => saveSnapshot(state)
      case SaveSnapshotSuccess(metadata)         => // ...
      case SaveSnapshotFailure(metadata, reason) => // ...
      case s: String =>
        persist(s) { evt => state = state.updated(evt) }
    }

    def receiveRecover: Receive = {
      case SnapshotOffer(_, s: ExampleState) =>
        println("offered state = " + s)
        state = s
      case evt: String =>
        state = state.updated(evt)
    }

    override def journalPluginId: String = "akka-persistence-maprdb"
  }

  val system = ActorSystem("example")
  val persistentActor = system.actorOf(Props(classOf[ExamplePersistentActor]), "persistentActor-3-scala")

  persistentActor ! "a"
  persistentActor ! "b"
  persistentActor ! "snap"
  persistentActor ! "c"
  persistentActor ! "d"
  persistentActor ! "print"

  Thread.sleep(1000*60*60)
  system.terminate()
}
