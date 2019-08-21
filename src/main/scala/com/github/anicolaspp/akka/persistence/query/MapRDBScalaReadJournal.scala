package com.github.anicolaspp.akka.persistence.query

import akka.NotUsed
import akka.actor.ExtendedActorSystem
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl.{CurrentEventsByPersistenceIdQuery, CurrentPersistenceIdsQuery, EventsByPersistenceIdQuery, PersistenceIdsQuery, ReadJournal}
import akka.stream.scaladsl.Source
import com.github.anicolaspp.akka.persistence.ojai.MapRDBConnectionProvider
import com.github.anicolaspp.akka.persistence.query.sources.{CurrentPersistenceIdsSource, EventsByPersistenceIdSource, PersistenceIdsSource}
import com.github.anicolaspp.akka.persistence.MapRDB
import com.github.anicolaspp.akka.persistence.ojai.stores.StorePool
import com.typesafe.config.Config

class MapRDBScalaReadJournal private[anicolaspp](system: ExtendedActorSystem) extends ReadJournal
  with CurrentPersistenceIdsQuery
  with PersistenceIdsQuery
  with CurrentEventsByPersistenceIdQuery
  with EventsByPersistenceIdQuery
  with MapRDBConnectionProvider {

  /**
   * Same type of query as [[akka.persistence.query.scaladsl.PersistenceIdsQuery#persistenceIds]] but the stream
   * is completed immediately when it reaches the end of the "result set". Persistent
   * actors that are created after the query is completed are not included in the stream.
   */
  override def currentPersistenceIds(): Source[String, NotUsed] =
    Source.fromGraph(new CurrentPersistenceIdsSource(StorePool.idsStore(actorSystemConfiguration.getString(MapRDB.PATH_CONFIGURATION_KEY))))

  /**
   * Query all `PersistentActor` identifiers, i.e. as defined by the
   * `persistenceId` of the `PersistentActor`.
   *
   * The stream is not completed when it reaches the end of the currently used `persistenceIds`,
   * but it continues to push new `persistenceIds` when new persistent actors are created.
   * Corresponding query that is completed when it reaches the end of the currently
   * currently used `persistenceIds` is provided by [[CurrentPersistenceIdsQuery#currentPersistenceIds]].
   */
  override def persistenceIds(): Source[String, NotUsed] =
    Source.fromGraph(new PersistenceIdsSource(
      StorePool.idsStore(actorSystemConfiguration.getString(MapRDB.PATH_CONFIGURATION_KEY)),
      actorSystemConfiguration.getLong(MapRDB.IDS_POLLING_INTERVAL)
    ))

  override def actorSystemConfiguration: Config = system.settings.config

  /**
   * Same type of query as [[EventsByPersistenceIdQuery#eventsByPersistenceId]]
   * but the event stream is completed immediately when it reaches the end of
   * the "result set". Events that are stored after the query is completed are
   * not included in the event stream.
   */
  override def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    Source.fromGraph(new EventsByPersistenceIdSource(
      StorePool.journalFor(actorSystemConfiguration.getString(MapRDB.PATH_CONFIGURATION_KEY)).getStoreFor(persistenceId),
      system,
      fromSequenceNr,
      toSequenceNr,
      isStreamingQuery = false)
    )

  /**
   * Query events for a specific `PersistentActor` identified by `persistenceId`.
   *
   * You can retrieve a subset of all events by specifying `fromSequenceNr` and `toSequenceNr`
   * or use `0L` and `Long.MaxValue` respectively to retrieve all events.
   *
   * The returned event stream should be ordered by sequence number.
   *
   * The stream is not completed when it reaches the end of the currently stored events,
   * but it continues to push new events when new events are persisted.
   * Corresponding query that is completed when it reaches the end of the currently
   * stored events is provided by [[CurrentEventsByPersistenceIdQuery#currentEventsByPersistenceId]].
   */
  override def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    Source.fromGraph(new EventsByPersistenceIdSource(
      StorePool.journalFor(actorSystemConfiguration.getString(MapRDB.PATH_CONFIGURATION_KEY)).getStoreFor(persistenceId),
      system,
      fromSequenceNr,
      toSequenceNr,
      isStreamingQuery = true,
      pollingIntervalMs = actorSystemConfiguration.getLong(MapRDB.EVENTS_POLLING_INTERVAL))
    )
}

object MapRDBScalaReadJournal {
  def apply(system: ExtendedActorSystem): MapRDBScalaReadJournal = new MapRDBScalaReadJournal(system)
}