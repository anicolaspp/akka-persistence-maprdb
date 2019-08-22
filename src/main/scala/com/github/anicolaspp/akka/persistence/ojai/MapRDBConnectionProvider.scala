package com.github.anicolaspp.akka.persistence.ojai

import com.github.anicolaspp.akka.persistence.MapRDB.maprdbConnectionString
import com.typesafe.config.Config
import org.ojai.store.{Connection, DriverManager}

/**
 * Mix in in order to get access to an implicit [[Connection]]
 */
trait MapRDBConnectionProvider {
  implicit val connection: Connection = DriverManager.getConnection(maprdbConnectionString(actorSystemConfiguration))

  def actorSystemConfiguration: Config
}
