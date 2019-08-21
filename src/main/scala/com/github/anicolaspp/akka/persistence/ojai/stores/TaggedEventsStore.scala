package com.github.anicolaspp.akka.persistence.ojai.stores

import com.github.anicolaspp.akka.persistence.ojai.TagsStore
import com.github.anicolaspp.akka.persistence.ojai.stores.StorePool.initializeStoreInPathIfNeeded
import org.ojai.store.{Connection, DocumentStore}

private case class TaggedEventsStore(path: String)(implicit connection: Connection) extends TagsStore {
  private lazy val store = initializeStoreInPathIfNeeded(path)

  override def getTagsStore(): DocumentStore = store
}

/**
 * There is a single instance of this tagged events stores
 */
object TaggedEventsStore {
  private var taggedEventsStore: TagsStore = _

  def apply(path: String)(implicit connection: Connection): TagsStore = {
    if (taggedEventsStore == null) {
      taggedEventsStore = new TaggedEventsStore(path)(connection)
    }

    taggedEventsStore
  }
}