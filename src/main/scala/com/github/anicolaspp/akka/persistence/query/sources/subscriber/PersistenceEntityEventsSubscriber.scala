package com.github.anicolaspp.akka.persistence.query.sources.subscriber

import com.github.anicolaspp.akka.persistence.MapRDB
import org.ojai.Document
import org.ojai.store.{Connection, DocumentStore, QueryCondition}

import scala.util.Try

class PersistenceEntityEventsSubscriber(store: DocumentStore, streaming: Boolean)(implicit connection: Connection) extends Subscription[Seq[Document]] {

  import MapRDB._

  private var running = false
  private var minObservedId: Long = -1

  override def isRunning: Boolean = running

  override def subscribe(pollingIntervalMs: Long, fn: Seq[Document] => Unit): Unit = {
    val subscriber = new Thread {
      setDaemon(true)

      override def run(): Unit = {
        running = true

        while (running) {
          val result = tryQuery(store).getOrElse(Seq.empty)

          minObservedId = newMinObservedId(result)

          fn(result)

          if (!streaming) {
            running = false
          }

          Thread.sleep(pollingIntervalMs)
        }
      }
    }

    subscriber.start()
  }

  override def unsubscribe(): Unit = running = false

  private def newMinObservedId(result: Seq[Document]) = {
    result.lastOption.map { last =>
      val lastLong = last.getIdBinary.toLong()

      if (lastLong > minObservedId) {
        lastLong
      } else {
        minObservedId
      }
    }.getOrElse(minObservedId)
  }

  /**
   * Query the journal based on a minimum observed id. As the data is query, the minimum observed id get updated so
   * only new events are retrieves.
   *
   * Notice that minimum observed id is used to query _id in MapR-DB which is insanely fast and retrieved in order.
   *
   * @param store Store handler to the Journal
   * @return
   */
  private def tryQuery(store: DocumentStore) = Try {
    import scala.collection.JavaConverters._

    val (from, op) = if (minObservedId < 0) (0L, QueryCondition.Op.GREATER_OR_EQUAL) else (minObservedId, QueryCondition.Op.GREATER)

    val condition = connection
      .newCondition()
      .is(MapRDB.MAPR_ENTITY_ID, op, from.toBinaryId())
      .build()

    val query = connection
      .newQuery()
      .where(condition)
      .build()

    val result = store.find(query).asScala.toSeq

    result
  }
}
