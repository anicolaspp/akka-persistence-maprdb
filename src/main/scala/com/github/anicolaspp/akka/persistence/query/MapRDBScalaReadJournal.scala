package com.github.anicolaspp.akka.persistence.query

import akka.NotUsed
import akka.actor.ExtendedActorSystem
import akka.persistence.query.scaladsl.{CurrentPersistenceIdsQuery, PersistenceIdsQuery, ReadJournal}
import akka.stream.scaladsl.Source
import com.github.anicolaspp.akka.persistence.ojai.StorePool
import com.github.anicolaspp.akka.persistence.query.sources.{CurrentPersistenceIdsSource, PersistenceIdsSource}
import com.github.anicolaspp.akka.persistence.{MapRDB, MapRDBConnectionProvider}
import com.typesafe.config.Config

class MapRDBScalaReadJournal(system: ExtendedActorSystem, config: Config) extends ReadJournal
  with CurrentPersistenceIdsQuery
  with PersistenceIdsQuery
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

}

object MapRDBScalaReadJournal {
  def apply(system: ExtendedActorSystem, config: Config): MapRDBScalaReadJournal = new MapRDBScalaReadJournal(system: ExtendedActorSystem, config: Config)
}