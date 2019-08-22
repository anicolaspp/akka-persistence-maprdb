package com.github.anicolaspp.akka.persistence.query.subscriber

import com.github.anicolaspp.akka.persistence.MapRDB
import org.ojai.Document
import org.ojai.store.{Connection, DocumentStore, QueryCondition}

import scala.util.Try

class PersistenceEntityEventsPollingSubscriber private[anicolaspp](store: DocumentStore,
                                                                   fromSequenceNr: Long,
                                                                   toSequenceNr: Long,
                                                                   streaming: Boolean)(implicit connection: Connection)
  extends EventsPollingSubscriber(store, streaming) {

  import MapRDB._

  private var minObservedId: Long = -1

  private lazy val rangeCondition = connection
    .newCondition()
    .and()
    .is(MAPR_ENTITY_ID, QueryCondition.Op.GREATER_OR_EQUAL, fromSequenceNr.toBinaryId())
    .is(MAPR_ENTITY_ID, QueryCondition.Op.LESS_OR_EQUAL, toSequenceNr.toBinaryId())
    .close()
    .build()

  override def tryQuery(store: DocumentStore): Try[Seq[Document]] = Try {
    import scala.collection.JavaConverters._

    val (from, op) = if (minObservedId < 0) (0L, QueryCondition.Op.GREATER_OR_EQUAL) else (minObservedId, QueryCondition.Op.GREATER)

    val condition = connection
      .newCondition()
      .and()
      .is(MapRDB.MAPR_ENTITY_ID, op, from.toBinaryId())
      .condition(rangeCondition)
      .close()
      .build()

    val query = connection
      .newQuery()
      .where(condition)
      .build()

    val result = store.find(query).asScala.toSeq

    minObservedId = newMinObservedId(result)

    result
  }

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

  override def subscriptionName: String = "PersistenceEntityEventsSubscriber"
}



