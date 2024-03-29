package com.github.anicolaspp.akka.persistence.journal

import org.ojai.Document
import org.ojai.joda.DateTime
import org.ojai.store.Connection

object Journal {

  import com.github.anicolaspp.akka.persistence.MapRDB._

  def toMapRDBRow(persistenceId: String, sequenceNr: Long, persistentRepr: Array[Byte], deleted: Boolean)(implicit connection: Connection): Document =
    connection
      .newDocument()
      .setId(sequenceNr.toBinaryId())
      .set(MAPR_BINARY_MARK, persistentRepr)
      .set("persistenceId", persistenceId)
      .set(MAPR_DELETED_MARK, deleted)

  def getBinaryRepresentationFrom(document: Document): Array[Byte] =
    document.getBinary(MAPR_BINARY_MARK).array()

  def tagToMapRDBRow(tag: String, eventSerializedRepresentation: Array[Byte])(implicit connection: Connection): Document =
    connection
      .newDocument()
      .setId(DateTime.now().toInstant.toString)
      .set("tag", tag)
      .set(MAPR_BINARY_MARK, eventSerializedRepresentation)
}
