package com.github.anicolaspp.akka.persistence.ojai.stores

import org.ojai.store.DocumentStore


/**
 * Mix in to get access to [[DocumentStore]] representing the [[PersistenceEntities]] id table.
 */
trait IdsStore {

  /**
   * Handler to MapR-DB tables where the [[PersistenceEntities]] live.
   */
  def getStore(): DocumentStore
}
