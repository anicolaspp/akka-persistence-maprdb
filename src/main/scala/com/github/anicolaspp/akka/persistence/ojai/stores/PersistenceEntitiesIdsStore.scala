package com.github.anicolaspp.akka.persistence.ojai.stores

import com.github.anicolaspp.akka.persistence.ojai.IdsStore
import com.github.anicolaspp.akka.persistence.ojai.stores.StorePool.initializeStoreInPathIfNeeded
import org.ojai.store.{Connection, DocumentStore}

private case class PersistenceEntitiesIdsStore(path: String)(implicit connection: Connection) extends IdsStore {
  private lazy val store = initializeStoreInPathIfNeeded(path)

  override def getStore(): DocumentStore = store
}

object PersistenceEntitiesIdsStore {
  private var idStore: IdsStore = _

  def apply(path: String)(implicit connection: Connection): IdsStore = {
    if (idStore == null) {
      idStore = new PersistenceEntitiesIdsStore(path)(connection)
    }

    idStore
  }
}