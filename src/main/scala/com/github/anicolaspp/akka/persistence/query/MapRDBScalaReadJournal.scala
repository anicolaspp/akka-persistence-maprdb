package com.github.anicolaspp.akka.persistence.query

import akka.actor.ExtendedActorSystem
import akka.persistence.query.scaladsl.ReadJournal
import com.typesafe.config.Config

class MapRDBScalaReadJournal extends ReadJournal {

}

object MapRDBScalaReadJournal {
  def apply(system: ExtendedActorSystem, config: Config): MapRDBScalaReadJournal = new MapRDBScalaReadJournal()
}