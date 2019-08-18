package com.github.anicolaspp.akka.persistent
package test

import akka.persistence.CapabilityFlag
import akka.persistence.journal.JournalSpec
import com.typesafe.config.ConfigFactory

class MyJournalSpec extends JournalSpec(config = ConfigFactory.parseString("""
    akka.persistence.journal.plugin = "my.journal.plugin"
    """)) {

  override def supportsRejectingNonSerializableObjects: CapabilityFlag =
    true // or CapabilityFlag.on

//  val storageLocations = List(
//    new File(system.settings.config.getString("akka.persistence.journal.leveldb.dir")),
//    new File(config.getString("akka.persistence.snapshot-store.local.dir")))

  override def beforeAll(): Unit = {
//    super.beforeAll()
//    storageLocations.foreach(FileUtils.deleteRecursively)
  }

  override def afterAll(): Unit = {

  }

}