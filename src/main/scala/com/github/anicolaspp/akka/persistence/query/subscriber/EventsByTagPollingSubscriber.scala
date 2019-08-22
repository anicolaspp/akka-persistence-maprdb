package com.github.anicolaspp.akka.persistence.query.subscriber

import akka.persistence.query.{Offset, Sequence}
import com.github.anicolaspp.akka.persistence.MapRDB
import org.ojai.Document
import org.ojai.store.{Connection, DocumentStore, QueryCondition}

import scala.util.Try

class EventsByTagPollingSubscriber(store: DocumentStore, tag: String, streaming: Boolean, offset: Offset)(implicit connection: Connection)
  extends EventsPollingSubscriber(store, streaming) {

  private val firstOffset = offset match {
    case Sequence(value) => Some(value)
    case _ => None
  }

  private var currentOffset = -1L

  private var firstQuery = true
  private var minObservedId: String = ""

  override def tryQuery(store: DocumentStore): Try[Seq[Document]] = Try {
    import scala.collection.JavaConverters._

    val (from, op) = if (minObservedId == "") ("", QueryCondition.Op.GREATER_OR_EQUAL) else (minObservedId, QueryCondition.Op.GREATER)

    val condition = connection
      .newCondition()
      .and()
      .is(MapRDB.MAPR_ENTITY_ID, op, from)
      .is("tag", QueryCondition.Op.EQUAL, tag)
      .close()
      .build()

    val query = connection
      .newQuery()
      .where(condition)
      .offset(queryOffset)
      .build()

    println(query.asJsonString())

    if (currentOffset < 0) {
      currentOffset = queryOffset
    }

    val result = store
      .find(query)
      .asScala
      .map { doc =>
        currentOffset = currentOffset + 1

        val envelope = connection
          .newDocument()
          .setId(currentOffset.toString)
          .set("inner", doc)
          .set("old_id", doc.getIdString)

        envelope
      }
      .toSeq

    minObservedId = newMinObservedId(result)

    firstQuery = false

    result
  }

  override def subscriptionName: String = "EventsByTagSubscriber"

  private def newMinObservedId(result: Seq[Document]) = {
    result.lastOption.map { last =>
      val lastLong = last.getString("old_id")

      if (lastLong > minObservedId) {
        lastLong
      } else {
        minObservedId
      }
    }.getOrElse(minObservedId)
  }

  private def queryOffset = (firstQuery, firstOffset) match {
    case (false, _) => 0L
    case (true, None) => 0L
    case (true, Some(value)) => value
  }
}
