package com.github.anicolaspp.akka.persistence.ojai

import com.github.anicolaspp.akka.persistence.{Journal, Snapshot, StoreType}
import org.ojai.store.{Connection, DocumentStore}

trait StorePool {
  def getStoreFor(persistentId: String): DocumentStore
}

object StorePool {
  def journalFor(journalPath: String)(implicit connection: Connection): StorePool = storeFor(journalPath, Journal)

  def snapshotStoreFor(snapshotPath: String)(implicit connection: Connection): StorePool = storeFor(snapshotPath, Snapshot)

  def storeFor(path: String, storeType: StoreType)(implicit connection: Connection): StorePool =
    MapRDBStorePool(path, storeType)

  private case class MapRDBStorePool(path: String, storeType: StoreType)(implicit connection: Connection) extends StorePool {
    private var stores = Map.empty[String, DocumentStore]

    override def getStoreFor(persistentId: String): DocumentStore = synchronized {
      val storePath = s"$path/$persistentId.${storeType.toString}"

      if (stores.contains(storePath)) {
        stores(storePath)
      } else {

        val store = if (connection.storeExists(storePath)) {
          connection.getStore(storePath)
        } else {
          connection.createStore(storePath)
        }

        stores = stores + (storePath -> store)

        store
      }
    }
  }
}