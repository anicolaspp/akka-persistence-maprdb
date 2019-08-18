package com.github.anicolaspp.akka.persistence.snapshot

import akka.actor.{ActorLogging, ActorSystem}
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import com.github.anicolaspp.akka.persistence.ByteArraySerializer
import com.github.anicolaspp.akka.persistence.MapRDB.{MAPR_CONFIGURATION_STRING, _}
import com.github.anicolaspp.akka.persistence.ojai.StorePool
import org.ojai.store.{Connection, DriverManager, QueryCondition, SortOrder}

import scala.concurrent.{ExecutionContext, Future}

class MapRDBSnapshotStore extends SnapshotStore
  with ActorLogging
  with ByteArraySerializer {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  implicit val connection: Connection = DriverManager.getConnection(MAPR_CONFIGURATION_STRING)
  private val config = actorSystem.settings.config
  private val snapshotPath = config.getString(PATH_CONFIGURATION_KEY)

  private val storePool = StorePool.snapshotStoreFor(snapshotPath)

  override implicit lazy val actorSystem: ActorSystem = context.system

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = Future {
    log.info(s"LOADING SNAPSHOT: $criteria")

    val condition = connection
      .newCondition()
      .and()
      .is("meta.timestamp", QueryCondition.Op.GREATER_OR_EQUAL, criteria.minTimestamp)
      .is("meta.timestamp", QueryCondition.Op.LESS_OR_EQUAL, criteria.maxTimestamp)
      .is("meta.sequenceNr", QueryCondition.Op.LESS_OR_EQUAL, criteria.maxSequenceNr)
      .close()
      .build()

    val query = connection
      .newQuery()
      .where(condition)
      .limit(1)
      .orderBy("_id", SortOrder.DESC)
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

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = ???

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = ???
}