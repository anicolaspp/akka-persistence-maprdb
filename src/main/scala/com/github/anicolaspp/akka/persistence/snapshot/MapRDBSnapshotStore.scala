package com.github.anicolaspp.akka.persistence.snapshot

import akka.actor.{ActorLogging, ActorSystem}
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import com.github.anicolaspp.akka.persistence.ByteArraySerializer
import com.github.anicolaspp.akka.persistence.MapRDB._
import com.github.anicolaspp.akka.persistence.ojai.{MapRDBConnectionProvider, StorePool}
import com.github.anicolaspp.akka.persistence.snapshot.Snapshot._
import com.typesafe.config.Config
import org.ojai.store.{QueryCondition, SortOrder}

import scala.concurrent.{ExecutionContext, Future}

class MapRDBSnapshotStore extends SnapshotStore
  with ActorLogging
  with ByteArraySerializer with MapRDBConnectionProvider {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  def actorSystemConfiguration: Config = actorSystem.settings.config

  private lazy val snapshotPath = actorSystemConfiguration.getString(PATH_CONFIGURATION_KEY)

  private lazy val storePool = StorePool.snapshotStoreFor(snapshotPath)

  override implicit lazy val actorSystem: ActorSystem = context.system

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = Future {
    log.info(s"LOADING SNAPSHOT: $criteria")

    val condition = connection
      .newCondition()
      .and()
      .is(META_TIMESTAMP, QueryCondition.Op.GREATER_OR_EQUAL, criteria.minTimestamp)
      .is(META_TIMESTAMP, QueryCondition.Op.LESS_OR_EQUAL, criteria.maxTimestamp)
      .is(META_SEQUENCE_NR, QueryCondition.Op.LESS_OR_EQUAL, criteria.maxSequenceNr)
      .close()
      .build()

    val query = connection
      .newQuery()
      .where(condition)
      .limit(1)
      .orderBy(MAPR_ENTITY_ID, SortOrder.DESC)
      .build()

    val it = storePool.getStoreFor(persistenceId).find(query).iterator()

    val last = if (it.hasNext) {
      Some(it.next())
    } else {
      None
    }

    last.flatMap(Snapshot.fromMapRDBRow)
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = Future {
    log.info(s"SAVING SNAPSHOT: $metadata; $snapshot")

    toBytes(akka.persistence.serialization.Snapshot(snapshot))
      .map { value =>
        val snapshot = Snapshot.toMapRBDRow(metadata, value)

        storePool.getStoreFor(metadata.persistenceId).insert(snapshot)
      }
      .fold(e => Future.failed(e), identity)
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = Future {
    val condition = connection
      .newCondition()
      .and()
      .is(META_TIMESTAMP, QueryCondition.Op.EQUAL, metadata.timestamp)
      .is(META_PERSISTENCE_ID, QueryCondition.Op.EQUAL, metadata.persistenceId)
      .is(META_SEQUENCE_NR, QueryCondition.Op.EQUAL, metadata.sequenceNr)
      .close()
      .build()

    deleteWithCondition(condition, metadata.persistenceId)
  }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = Future {
    val condition = connection
      .newCondition()
      .and()
      .is(META_TIMESTAMP, QueryCondition.Op.GREATER_OR_EQUAL, criteria.minTimestamp)
      .is(META_TIMESTAMP, QueryCondition.Op.LESS_OR_EQUAL, criteria.maxTimestamp)
      .is(META_SEQUENCE_NR, QueryCondition.Op.GREATER_OR_EQUAL, criteria.minSequenceNr)
      .is(META_SEQUENCE_NR, QueryCondition.Op.LESS_OR_EQUAL, criteria.maxSequenceNr)
      .close()
      .build()

    deleteWithCondition(condition, persistenceId)
  }

  private def deleteWithCondition(condition: QueryCondition, persistenceId: String): Unit = {
    val store = storePool.getStoreFor(persistenceId)

    store.delete(store.find(connection.newQuery().where(condition)))
  }
}