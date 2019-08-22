package com.github.anicolaspp.akka.persistence.query.subscriber

import com.github.anicolaspp.akka.persistence.MapRDB
import org.ojai.Document
import org.ojai.store.{Connection, DocumentStore}

import scala.util.Try

class PersistenceIdsSubscription(store: DocumentStore,
                                 isStreamingQuery: Boolean)(implicit connection: Connection)
  extends EventsPollingSubscriber(store, isStreamingQuery) {

  override def tryQuery(store: DocumentStore): Try[Seq[Document]] = Try {
    import scala.collection.JavaConverters._

    store.find(connection.newQuery().select(MapRDB.MAPR_ENTITY_ID).build()).asScala.toSeq
  }

  override def subscriptionName: String = "PersistenceIdsSubscription"
}