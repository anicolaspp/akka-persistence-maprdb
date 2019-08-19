package com.github.anicolaspp.akka.persistence
package test

import akka.persistence.CapabilityFlag
import akka.persistence.journal.JournalSpec
import com.typesafe.config.ConfigFactory

//class MyJournalSpec extends JournalSpec(config = ConfigFactory.parseString("""
//    akka.persistence.journal.plugin = "akka-persistence-maprdb"
//    """)) {
//
//  override def supportsRejectingNonSerializableObjects: CapabilityFlag = false // or CapabilityFlag.on
//
}