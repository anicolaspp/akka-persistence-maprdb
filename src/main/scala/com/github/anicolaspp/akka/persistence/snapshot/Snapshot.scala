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
      .set("meta", connection.newDocument()
        .set("persistenceId", metadata.persistenceId)
        .set("sequenceNr", metadata.sequenceNr)
        .set("timestamp", metadata.timestamp))
      .set("snapshot", snapshot)

  def id(metadata: SnapshotMetadata) = s"${metadata.persistenceId}_${metadata.sequenceNr}_${metadata.timestamp}"

  def fromMapRDBRow(document: Document)(implicit system: ActorSystem): Option[SelectedSnapshot] = Try {
    new ByteArraySerializer {
      override implicit val actorSystem: ActorSystem = system
    }
      .fromBytes[Snapshot](document.getBinary("snapshot").array())
  }
    .map { snapshot =>
      SelectedSnapshot(
        SnapshotMetadata(
          document.getString("meta.persistenceId"),
          document.getLong("meta.sequenceNr"),
          document.getLong("meta.timestamp")),
        snapshot)
    }
    .toOption
}
