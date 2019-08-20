package com.github.anicolaspp.akka.persistence.query.sources

import com.github.anicolaspp.akka.persistence.MapRDB
import org.ojai.store.{Connection, DocumentStore}

import scala.util.Try

class PersistenceIdsSubscription(store: DocumentStore)(implicit connection: Connection) {
  private var isRunning = true

  def subscribe(pollingIntervalMs: Long, fn: Seq[String] => Unit): Unit = {
    val subscriber = new Thread {
      setDaemon(true)

      override def run(): Unit = {
        while (isRunning) {
          val result = tryQuery(store).getOrElse(Seq.empty)

          fn(result)

          Thread.sleep(pollingIntervalMs)
        }
      }
    }

    subscriber.start()
  }

  def unsubscribe(): Unit = isRunning = false

  private def tryQuery(store: DocumentStore) = Try {
    import scala.collection.JavaConverters._

    val result = store.find(connection.newQuery().select(MapRDB.MAPR_ENTITY_ID).build()).asScala.map(_.getIdString).toSeq

    result
  }
}

object PersistenceIdsSubscription {
  def apply(store: DocumentStore)(implicit connection: Connection): PersistenceIdsSubscription =
    new PersistenceIdsSubscription(store)(connection)
}