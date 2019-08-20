package com.github.anicolaspp.akka.persistence

import java.nio.ByteBuffer

import com.typesafe.config.Config
import org.ojai.store.DriverManager

object MapRDB {
  implicit class LongEx(value: Long) {
    def toBinaryId(): ByteBuffer = ByteBuffer.wrap(BigInt(value).toByteArray)
  }

  def maprdbConnectionString(config: Config): String = {
    val url = config.getString("maprdb.driver.url")

    if (url == "ojai:anicolaspp:mem") {
      DriverManager.registerDriver(com.mapr.ojai.store.impl.InMemoryDriver)
    }

    url
  }

//  lazy val MAPR_CONFIGURATION_STRING = "ojai:mapr:"

  lazy val MAPR_ENTITY_ID = "_id"

  lazy val MAPR_DELETED_MARK = "deleted"

  lazy val MAPR_BINARY_MARK = "persistentRepr"

  lazy val PATH_CONFIGURATION_KEY = "maprdb.path"

  lazy val IDS_POLLING_INTERVAL = "maprdb.pollingIntervalms"
}

