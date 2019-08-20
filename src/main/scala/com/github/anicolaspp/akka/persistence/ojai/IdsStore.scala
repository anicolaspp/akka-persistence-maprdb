package com.github.anicolaspp.akka.persistence.ojai

import org.ojai.store.DocumentStore

trait IdsStore {
  def getStore(): DocumentStore
}
