package com.github.anicolaspp.akka.persistence

import java.nio.ByteBuffer

object MapRDB {
  implicit class LongEx(value: Long) {
    def toBinaryId(): ByteBuffer = ByteBuffer.wrap(BigInt(value).toByteArray)
  }

  lazy val MAPR_CONFIGURATION_STRING = "ojai:mapr:"

  lazy val MAPR_ENTITY_ID = "_id"

  lazy val MAPR_DELETED_MARK = "deleted"

  lazy val MAPR_BINARY_MARK = "persistentRepr"

  lazy val PATH_CONFIGURATION_KEY = "akka-persistence-maprdb.path"
}

