package com.github.anicolaspp.akka.persistence.query

import akka.NotUsed
import akka.actor.ExtendedActorSystem
import akka.persistence.query.scaladsl._
import akka.persistence.query.{EventEnvelope, Offset}
import akka.stream.scaladsl.Source
import com.github.anicolaspp.akka.persistence.MapRDB
import com.github.anicolaspp.akka.persistence.ojai.MapRDBConnectionProvider
import com.github.anicolaspp.akka.persistence.ojai.stores.StorePool
import com.github.anicolaspp.akka.persistence.query.sources.{EventsByPersistenceIdSource, EventsByTagSource, PersistenceIdsSource}
import com.typesafe.config.Config

class MapRDBScalaReadJournal private[anicolaspp](system: ExtendedActorSystem) extends ReadJournal
  with CurrentPersistenceIdsQuery
  with PersistenceIdsQuery
  with CurrentEventsByPersistenceIdQuery
  with EventsByPersistenceIdQuery
  with CurrentEventsByTagQuery
  with EventsByTagQuery
  with MapRDBConnectionProvider {

  /**
   * Same type of query as [[akka.persistence.query.scaladsl.PersistenceIdsQuery#persistenceIds]] but the stream
   * is completed immediately when it reaches the end of the "result set". Persistent
   * actors that are created after the query is completed are not included in the stream.
   */
  override def currentPersistenceIds(): Source[String, NotUsed] =
    Source.fromGraph(new PersistenceIdsSource(
      StorePool.idsStore(actorSystemConfiguration.getString(MapRDB.PATH_CONFIGURATION_KEY)),
      system,
      isStreamingQuery = false,
      pollingIntervalMs = actorSystemConfiguration.getLong(MapRDB.IDS_POLLING_INTERVAL)
    ))

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
      system,
      isStreamingQuery = true,
      pollingIntervalMs = actorSystemConfiguration.getLong(MapRDB.IDS_POLLING_INTERVAL)
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

  /**
   * Same type of query as [[EventsByTagQuery#eventsByTag]] but the event stream
   * is completed immediately when it reaches the end of the "result set". Events that are
   * stored after the query is completed are not included in the event stream.
   */
  override def currentEventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] =
    Source.fromGraph(new EventsByTagSource(
      StorePool.taggedEventsStore(actorSystemConfiguration.getString(MapRDB.PATH_CONFIGURATION_KEY)),
      system,
      tag,
      offset,
      isStreamingQuery = false,
      pollingIntervalMs = actorSystemConfiguration.getLong(MapRDB.EVENTS_POLLING_INTERVAL))
    )

  /**
   * Query events that have a specific tag. A tag can for example correspond to an
   * aggregate root type (in DDD terminology).
   *
   * The consumer can keep track of its current position in the event stream by storing the
   * `offset` and restart the query from a given `offset` after a crash/restart.
   *
   * The exact meaning of the `offset` depends on the journal and must be documented by the
   * read journal plugin. It may be a sequential id number that uniquely identifies the
   * position of each event within the event stream. Distributed data stores cannot easily
   * support those semantics and they may use a weaker meaning. For example it may be a
   * timestamp (taken when the event was created or stored). Timestamps are not unique and
   * not strictly ordered, since clocks on different machines may not be synchronized.
   *
   * The returned event stream should be ordered by `offset` if possible, but this can also be
   * difficult to fulfill for a distributed data store. The order must be documented by the
   * read journal plugin.
   *
   * The stream is not completed when it reaches the end of the currently stored events,
   * but it continues to push new events when new events are persisted.
   * Corresponding query that is completed when it reaches the end of the currently
   * stored events is provided by [[CurrentEventsByTagQuery#currentEventsByTag]].
   */
  override def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] =
    Source.fromGraph(new EventsByTagSource(
      StorePool.taggedEventsStore(actorSystemConfiguration.getString(MapRDB.PATH_CONFIGURATION_KEY)),
      system,
      tag,
      offset,
      isStreamingQuery = true,
      pollingIntervalMs = actorSystemConfiguration.getLong(MapRDB.EVENTS_POLLING_INTERVAL))
    )
}

object MapRDBScalaReadJournal {
  def apply(system: ExtendedActorSystem): MapRDBScalaReadJournal = new MapRDBScalaReadJournal(system)
}