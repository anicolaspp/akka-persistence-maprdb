package com.github.anicolaspp.akka.persistence.query

import akka.NotUsed
import akka.actor.ExtendedActorSystem
import akka.persistence.query.{EventEnvelope, Offset}
import akka.persistence.query.javadsl.{CurrentEventsByPersistenceIdQuery, CurrentEventsByTagQuery, CurrentPersistenceIdsQuery, EventsByPersistenceIdQuery, EventsByTagQuery, PersistenceIdsQuery, ReadJournal}
import akka.stream.javadsl.Source
import com.github.anicolaspp.akka.persistence.ojai.MapRDBConnectionProvider
import com.typesafe.config.Config

class MapRDBJavaReadJournal private[anicolaspp](system: ExtendedActorSystem) extends ReadJournal
  with CurrentPersistenceIdsQuery
  with PersistenceIdsQuery
  with CurrentEventsByPersistenceIdQuery
  with EventsByPersistenceIdQuery
  with CurrentEventsByTagQuery
  with EventsByTagQuery
  with MapRDBConnectionProvider {

  override def currentPersistenceIds(): Source[String, NotUsed] =
    MapRDBScalaReadJournal(system).currentPersistenceIds().asJava

  override def persistenceIds(): Source[String, NotUsed] =
    MapRDBScalaReadJournal(system).persistenceIds().asJava

  override def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    MapRDBScalaReadJournal(system).currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).asJava

  override def actorSystemConfiguration: Config = system.settings.config

  override def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    MapRDBScalaReadJournal(system).eventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).asJava

  override def currentEventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] =
    MapRDBScalaReadJournal(system).currentEventsByTag(tag, offset).asJava

  override def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] =
    MapRDBScalaReadJournal(system).eventsByTag(tag, offset).asJava
}

object MapRDBJavaReadJournal {
  def apply(system: ExtendedActorSystem): MapRDBJavaReadJournal = new MapRDBJavaReadJournal(system)
}