package com.github.anicolaspp.akka.persistence.ojai.stores

import org.ojai.store.DocumentStore

/**
 * Mix in to get access to [[DocumentStore]] representing the [[akka.persistence.journal.Tagged]] events table.
 */
trait TagsStore {
  def getTagsStore(): DocumentStore
}
