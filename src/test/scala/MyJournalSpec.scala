package com.github.anicolaspp.akka.persistence
package test

import akka.persistence.CapabilityFlag
import akka.persistence.journal.JournalSpec
import com.github.anicolaspp.akka.persistence.ojai.MapRDBConnectionProvider
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Ignore

//@Ignore
//class MyJournalSpec extends JournalSpec(config = ConfigFactory.parseString(
//  """
//    akka.persistence.journal.plugin = "akka-persistence-maprdb.journal"
//    """)) with MapRDBConnectionProvider {
//
//  override def supportsRejectingNonSerializableObjects: CapabilityFlag = false // or CapabilityFlag.on
//
//  override def actorSystemConfiguration: Config = system.settings.config
//
//  override def preparePersistenceId(pid: String): Unit = {
//    val storePath = actorSystemConfiguration.getString(MapRDB.PATH_CONFIGURATION_KEY) + s"/$pid.journal"
//
//    if (connection.storeExists(storePath)) {
//      connection.deleteStore(storePath)
//    }
//
//  }
//}