package com.github.anicolaspp.akka.persistence.query

import akka.actor.ExtendedActorSystem
import akka.persistence.query.ReadJournalProvider
import akka.persistence.query.javadsl.{ReadJournal => JReadJournal}
import akka.persistence.query.scaladsl.{ReadJournal => SReadJournal}
import com.typesafe.config.Config

class MapRDBReadJournalProvider(system: ExtendedActorSystem, config: Config) extends ReadJournalProvider {
  override def scaladslReadJournal(): SReadJournal = MapRDBScalaReadJournal(system, config)

  override def javadslReadJournal(): JReadJournal = MapRDBJavaReadJournal(system, config)
}

object MapRDBReadJournalProvider {
//  lazy val Identifier = "akka-persistence-maprdb.read-journal"
}

