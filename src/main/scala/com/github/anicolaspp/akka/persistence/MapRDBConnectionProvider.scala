package com.github.anicolaspp.akka.persistence

import com.github.anicolaspp.akka.persistence.MapRDB.maprdbConnectionString
import com.typesafe.config.Config
import org.ojai.store.{Connection, DriverManager}

trait MapRDBConnectionProvider {
  implicit val connection: Connection = DriverManager.getConnection(maprdbConnectionString(actorSystemConfiguration))

  def actorSystemConfiguration: Config
}
