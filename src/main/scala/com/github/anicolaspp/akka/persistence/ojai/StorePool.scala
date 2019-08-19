package com.github.anicolaspp.akka.persistence.ojai

import com.github.anicolaspp.akka.persistence.{Journal, MapRDB, PersistenceIdStore, Snapshot, StoreType}
import org.ojai.store.{Connection, DocumentStore}

import scala.concurrent.Future

trait StorePool extends IdsStore {
  def getStoreFor(persistentId: String): DocumentStore
}

trait IdsStore {
  def getStore(): DocumentStore
}

object StorePool {
  def idsStore(path: String)(implicit connection: Connection): DocumentStore = SingleStore(idsStorePath(path)).getStore()

  def journalFor(journalPath: String)(implicit connection: Connection): StorePool = storeFor(journalPath, Journal)

  def snapshotStoreFor(snapshotPath: String)(implicit connection: Connection): StorePool = storeFor(snapshotPath, Snapshot)

  def storeFor(path: String, storeType: StoreType)(implicit connection: Connection): StorePool =
    MapRDBStorePool(path, storeType)

  private case class MapRDBStorePool(path: String, storeType: StoreType)(implicit connection: Connection) extends StorePool {
    private var stores = Map.empty[String, DocumentStore]

    private lazy val ids = SingleStore(idsStorePath(path))

    override def getStoreFor(persistentId: String): DocumentStore = synchronized {
      val storePath = s"$path/$persistentId.${storeType.toString}"

      if (stores.contains(storePath)) {
        stores(storePath)
      } else {

        val store = initializeStoreInPathIfNeeded(storePath)

        Future {
          ids.getStore().insert(connection.newDocument().setId(persistentId).set("path", storePath))
        }(scala.concurrent.ExecutionContext.global)

        stores = stores + (storePath -> store)

        store
      }
    }

    override def getStore(): DocumentStore = ids.getStore()

  }

  private case class SingleStore(path: String)(implicit connection: Connection) extends IdsStore {
    private lazy val store = initializeStoreInPathIfNeeded(path)

    override def getStore(): DocumentStore = store
  }

  private def idsStorePath(basePath: String) = s"$basePath/ids"

  private def initializeStoreInPathIfNeeded(path: String)(implicit connection: Connection): DocumentStore = synchronized {
    if (connection.storeExists(path)) {
      connection.getStore(path)
    } else {
      connection.createStore(path)
    }
  }
}
