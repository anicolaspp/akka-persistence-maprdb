package com.github.anicolaspp.akka.persistence

import java.nio.ByteBuffer

import com.google.common.primitives.Bytes
import org.ojai.Document
import org.ojai.store.Connection

/**
 * Journal entry that can be serialized and deserialized to JSON
 * JSON in turn is serialized to ByteString so it can be stored in Redis with Rediscala
 */

import MapRDBAsyncWriteJournal._

object Journal {
  def toMapRDBRow(sequenceNr: Long, persistentRepr: Array[Byte], deleted: Boolean)(implicit connection: Connection) =
    connection
      .newDocument()
      .setId(ByteBuffer.wrap(BigInt(sequenceNr).toByteArray))
      .set(MAPR_BINARY_MARK, persistentRepr)
      .set(MAPR_DELETED_MARK, deleted)

  def getBinaryRepresentationFrom(document: Document): Array[Byte] =
    document.getBinary(MAPR_BINARY_MARK).array()
}
