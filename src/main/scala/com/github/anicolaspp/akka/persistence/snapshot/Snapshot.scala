package com.github.anicolaspp.akka.persistence.snapshot

import akka.persistence.SnapshotMetadata
import org.ojai.Document
import org.ojai.store.Connection

object Snapshot {
  def toMapRBDRow(metadata: SnapshotMetadata, snapshot: Array[Byte])(implicit connection: Connection): Document =
    connection.newDocument()
      .setId(s"${metadata.persistenceId}_${metadata.sequenceNr}_${metadata.timestamp}")
      .set("meta", connection.newDocument()
        .set("persistenceId", metadata.persistenceId)
        .set("sequenceNr", metadata.sequenceNr)
        .set("timestamp", metadata.timestamp))
      .set("snapshot", snapshot)


}
