package com.github.anicolaspp.akka.persistence

import org.ojai.store.Connection

/**
 * Journal entry that can be serialized and deserialized to JSON
 * JSON in turn is serialized to ByteString so it can be stored in Redis with Rediscala
 */


object Journal {
  def apply(sequenceNr: Long, persistentRepr: Array[Byte], deleted: Boolean)(implicit connection: Connection) =
    connection
      .newDocument()
      .setId(sequenceNr.toString)
      .set("persistentRepr", persistentRepr)
      .set("deleted", deleted)
}
