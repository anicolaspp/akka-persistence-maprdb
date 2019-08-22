package com.github.anicolaspp.akka.persistence.query.sources

import akka.actor.ActorSystem
import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, Outlet, SourceShape}
import com.github.anicolaspp.akka.persistence.query.subscriber.{PersistenceIdsSubscription, Subscription}
import org.ojai.Document
import org.ojai.store.{Connection, DocumentStore}

import scala.collection.mutable
import scala.util.Try

class PersistenceIdsSource(store: DocumentStore,
                           system: ActorSystem,
                           isStreamingQuery: Boolean,
                           pollingIntervalMs: Long = 1000)(implicit connection: Connection)
  extends GraphStage[SourceShape[String]] {

  private val out: Outlet[String] = Outlet("PersistenceIdsSource")

  override def shape: SourceShape[String] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new QueryShapeLogic[String](shape, system, true, pollingIntervalMs) {

      private val orderSet = mutable.SortedSet.empty[String]

      /**
       * Implementors should define what kind of subscription they are going to use.
       *
       * @return A subscription implementation.
       */
      override def eventSubscription: Subscription[Seq[Document]] = new PersistenceIdsSubscription(store, isStreamingQuery)

      /**
       * Every time the eventSubscription has new messages, it calls the callback function that in turns, uses getEvents
       * to generates the events to be pushed to downstream processor using this function.
       *
       * @param docs The list of documents received from the eventSubscription.
       * @return A list of events generated from the received sequence of documents.
       */
      override def getEvents(docs: Seq[Document]): Try[Seq[String]] = Try {
        val messagesToPush = docs.map(_.getIdString).filterNot(orderSet.contains)

        orderSet ++= messagesToPush

        messagesToPush
      }
    }
}



