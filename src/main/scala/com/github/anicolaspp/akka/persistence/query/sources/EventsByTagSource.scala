package com.github.anicolaspp.akka.persistence.query.sources

import akka.actor.ActorSystem
import akka.persistence.PersistentRepr
import akka.persistence.query.{EventEnvelope, Offset}
import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, Outlet, SourceShape}
import com.github.anicolaspp.akka.persistence.journal.Journal
import com.github.anicolaspp.akka.persistence.query.subscriber.{EventsByTagPollingSubscriber, Subscription}
import org.ojai.Document
import org.ojai.store.{Connection, DocumentStore}

import scala.util.{Failure, Success, Try}

class EventsByTagSource(store: DocumentStore,
                        system: ActorSystem,
                        tag: String,
                        offset: Offset,
                        isStreamingQuery: Boolean,
                        pollingIntervalMs: Long = 1000)(implicit connection: Connection)
  extends GraphStage[SourceShape[EventEnvelope]] {

  private val out: Outlet[EventEnvelope] = if (isStreamingQuery) {
    Outlet("CurrentEventsByTag")
  } else {
    Outlet("EventsByTag")
  }

  override def shape: SourceShape[EventEnvelope] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new QueryShapeLogic[EventEnvelope](shape, system, isStreamingQuery, pollingIntervalMs) {

    /**
     * Implementors should define what kind of subscription they are going to use.
     *
     * @return A subscription implementation.
     */
    def eventSubscription: Subscription[Seq[Document]] = new EventsByTagPollingSubscriber(store, tag, isStreamingQuery, offset)

    /**
     * Every time the eventSubscription has new messages, it calls the callback function that in turns, uses getEvents
     * to generates the events to be pushed to downstream processor using this function.
     *
     * @param docs The list of documents received from the eventSubscription.
     * @return A list of events generated from the received sequence of documents.
     */
    def getEvents(docs: Seq[Document]): Try[Seq[EventEnvelope]] = {
      val maybeEventEnvelopes = docs
        .map { document =>
          val inner = connection.newDocument(document.getMap("inner"))

          fromBytes[PersistentRepr](Journal.getBinaryRepresentationFrom(inner)) match {
            case Success(pr) => Some(EventEnvelope(Offset.sequence(document.getIdString.toLong), pr.persistenceId, pr.sequenceNr, pr))
            case Failure(_) => None
          }
        }

      if (maybeEventEnvelopes.forall(_.isDefined)) {
        Success(maybeEventEnvelopes.map(_.get))
      } else {
        Failure(new Throwable(s"Some events failed to deserialize"))
      }
    }
  }
}
