package com.github.anicolaspp.akka.persistence.query

import akka.actor.ExtendedActorSystem
import akka.persistence.query.javadsl.ReadJournal
import com.typesafe.config.Config

class MapRDBJavaReadJournal extends ReadJournal {

}

object MapRDBJavaReadJournal {
  def apply(system: ExtendedActorSystem, config: Config): MapRDBJavaReadJournal = new MapRDBJavaReadJournal()
}