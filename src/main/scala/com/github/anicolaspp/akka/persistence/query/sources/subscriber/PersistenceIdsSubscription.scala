package com.github.anicolaspp.akka.persistence.query.sources.subscriber

import com.github.anicolaspp.akka.persistence.MapRDB
import org.ojai.store.{Connection, DocumentStore}

import scala.util.Try

class PersistenceIdsSubscription(store: DocumentStore)(implicit connection: Connection) extends Subscription[Seq[String]] {
  private var running = false

  override def subscribe(pollingIntervalMs: Long, fn: Seq[String] => Unit): Unit = {
    val subscriber = new Thread {
      setDaemon(true)

      override def run(): Unit = {
        running = true

        while (running) {
          val result = tryQuery(store).getOrElse(Seq.empty)

          fn(result)

          Thread.sleep(pollingIntervalMs)
        }
      }
    }

    subscriber.start()
  }

  override def unsubscribe(): Unit = running = false

  private def tryQuery(store: DocumentStore) = Try {
    import scala.collection.JavaConverters._

    val result = store.find(connection.newQuery().select(MapRDB.MAPR_ENTITY_ID).build()).asScala.map(_.getIdString).toSeq

    result
  }

  override def isRunning: Boolean = running
}

object PersistenceIdsSubscription {
  def apply(store: DocumentStore)(implicit connection: Connection): PersistenceIdsSubscription =
    new PersistenceIdsSubscription(store)(connection)
}