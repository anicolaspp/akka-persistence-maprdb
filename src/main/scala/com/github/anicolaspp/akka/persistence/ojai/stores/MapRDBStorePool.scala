package com.github.anicolaspp.akka.persistence.ojai.stores

import com.github.anicolaspp.akka.persistence.StoreType
import org.ojai.store.{Connection, DocumentStore}

import scala.concurrent.Future

private case class MapRDBStorePool(path: String, storeType: StoreType)(implicit connection: Connection) extends StorePool {
  import StorePool._
  private var stores = Map.empty[String, DocumentStore]

  private lazy val ids = PersistenceEntitiesIdsStore(idsStorePath(path))

  private lazy val taggedEvents = TaggedEventsStore(taggedEventsStoredPath(path))

  override def getStoreFor(persistentId: String): DocumentStore = synchronized {
    val storePath = s"$path/$persistentId.${storeType.toString}"

    if (stores.contains(storePath)) {
      stores(storePath)
    } else {

      val store = initializeStoreInPathIfNeeded(storePath)

      asyncAddPersistenceIdToIdsTable(persistentId, storePath)

      stores = stores + (storePath -> store)

      store
    }
  }

  private def asyncAddPersistenceIdToIdsTable(persistentId: String, storePath: String) = Future {
    ids.getStore()
      .insert(connection.newDocument().setId(persistentId).set("path", storePath))
  }(scala.concurrent.ExecutionContext.global)

  override def getStore(): DocumentStore = ids.getStore()

  override def getTagsStore(): DocumentStore = taggedEvents.getTagsStore()
}
