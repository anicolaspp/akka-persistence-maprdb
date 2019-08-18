package com.github.anicolaspp.akka.persistence.snapshot

import akka.actor.{ActorLogging, ActorSystem}
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import com.github.anicolaspp.akka.persistence.ByteArraySerializer
import com.github.anicolaspp.akka.persistence.MapRDB.MAPR_CONFIGURATION_STRING
import com.github.anicolaspp.akka.persistence.journal.MapRDBAsyncWriteJournal.PATH_CONFIGURATION_KEY
import com.github.anicolaspp.akka.persistence.ojai.StorePool
import org.ojai.store.{Connection, DriverManager}

import scala.concurrent.{ExecutionContext, Future}

class MapRDBSnapshotStore extends SnapshotStore
  with ActorLogging
  with ByteArraySerializer {
  override implicit val actorSystem: ActorSystem = context.system

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  implicit val connection: Connection = DriverManager.getConnection(MAPR_CONFIGURATION_STRING)
  private val config = actorSystem.settings.config

  private val snapshotPath = config.getString(PATH_CONFIGURATION_KEY)

  private val storePool = StorePool.snapshotFor(snapshotPath)

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = ???

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = Future {
    toBytes(snapshot.asInstanceOf[AnyRef])
      .map { value =>
        val snapshot = Snapshot.toMapRBDRow(metadata, value)

        storePool.getStoreFor(metadata.persistenceId).insert(snapshot)
      }
      .fold(e => Future.failed(e), identity)
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = ???

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = ???
}
