package com.github.anicolaspp.akka.persistence.snapshot

import akka.actor.ActorSystem
import akka.persistence.{SelectedSnapshot, SnapshotMetadata}
import akka.persistence.serialization.Snapshot
import com.github.anicolaspp.akka.persistence.ByteArraySerializer
import org.ojai.Document
import org.ojai.store.Connection

import scala.util.Try

object Snapshot {
  def toMapRBDRow(metadata: SnapshotMetadata, snapshot: Array[Byte])(implicit connection: Connection): Document =
    connection.newDocument()
      .setId(id(metadata))
      .set(META, connection.newDocument()
        .set(PERSISTENCE_ID, metadata.persistenceId)
        .set(SEQUENCE_NR, metadata.sequenceNr)
        .set(TIMESTAMP, metadata.timestamp))
      .set(SNAPSHOT, snapshot)

  def id(metadata: SnapshotMetadata) = s"${metadata.persistenceId}_${metadata.sequenceNr}_${metadata.timestamp}"

  def fromMapRDBRow(document: Document)(implicit system: ActorSystem): Option[SelectedSnapshot] = Try {
    new ByteArraySerializer {
      override implicit val actorSystem: ActorSystem = system
    }
      .fromBytes[Snapshot](document.getBinary(SNAPSHOT).array())
  }
    .map { snapshot =>
      SelectedSnapshot(
        SnapshotMetadata(
          document.getString(META_PERSISTENCE_ID),
          document.getLong(META_SEQUENCE_NR),
          document.getLong(META_TIMESTAMP)),
        snapshot)
    }
    .toOption

  lazy val PERSISTENCE_ID = "persistenceId"
  lazy val SEQUENCE_NR = "sequenceNr"
  lazy val TIMESTAMP = "timestamp"
  lazy val SNAPSHOT = "snapshot"

  lazy val META = "meta"

  lazy val META_PERSISTENCE_ID = s"$META.$PERSISTENCE_ID"
  lazy val META_SEQUENCE_NR = s"$META.$SEQUENCE_NR"
  lazy val META_TIMESTAMP = s"$META.$TIMESTAMP"
}
