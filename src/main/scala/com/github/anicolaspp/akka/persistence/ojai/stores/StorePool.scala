package com.github.anicolaspp.akka.persistence.ojai.stores

import com.github.anicolaspp.akka.persistence.{Journal, Snapshot, StoreType}
import org.ojai.store.{Connection, DocumentStore}

/**
 * Pool of [[DocumentStore]] that are kept alive and reused in order to minimize the their creation.
 */
trait StorePool extends IdsStore with TagsStore {

  /**
   * Get a handler to the [[DocumentStore]] where the [[persistentId]] belongs to.
   * @param persistentId
   * @return
   */
  def getStoreFor(persistentId: String): DocumentStore
}

object StorePool {
  def idsStore(path: String)(implicit connection: Connection): DocumentStore = PersistenceEntitiesIdsStore(idsStorePath(path)).getStore()

  def taggedEventsStore(path: String)(implicit connection: Connection): DocumentStore = TaggedEventsStore(taggedEventsStoredPath(path)).getTagsStore()

  def journalFor(journalPath: String)(implicit connection: Connection): StorePool = storeFor(journalPath, Journal)

  def snapshotStoreFor(snapshotPath: String)(implicit connection: Connection): StorePool = storeFor(snapshotPath, Snapshot)

  def storeFor(path: String, storeType: StoreType)(implicit connection: Connection): StorePool = MapRDBStorePool(path, storeType)

  private[anicolaspp] def idsStorePath(basePath: String) = s"$basePath/ids"

  private[anicolaspp] def taggedEventsStoredPath(basePath: String) = s"$basePath/taggedEvents"

  private[anicolaspp] def initializeStoreInPathIfNeeded(path: String)(implicit connection: Connection): DocumentStore = synchronized {
    if (connection.storeExists(path)) {
      connection.getStore(path)
    } else {
      connection.createStore(path)
    }
  }
}
