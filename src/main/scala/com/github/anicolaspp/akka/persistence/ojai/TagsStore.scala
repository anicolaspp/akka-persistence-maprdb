package com.github.anicolaspp.akka.persistence.ojai

import org.ojai.store.DocumentStore

trait TagsStore {
  def getTagsStore(): DocumentStore
}
