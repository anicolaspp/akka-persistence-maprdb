package com.github.anicolaspp.akka.persistence.query.sources

import akka.actor.ActorSystem
import akka.persistence.PersistentRepr
import akka.persistence.query.{EventEnvelope, Offset}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageLogicWithLogging, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import com.github.anicolaspp.akka.persistence.ByteArraySerializer
import com.github.anicolaspp.akka.persistence.journal.Journal
import com.github.anicolaspp.akka.persistence.query.sources.subscriber.PersistenceEntityEventsSubscriber
import org.ojai.Document
import org.ojai.store.{Connection, DocumentStore}

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

class EventsByPersistenceIdSource(store: DocumentStore,
                                  system: ActorSystem,
                                  fromSequenceNr: Long,
                                  toSequenceNr: Long,
                                  isStreamingQuery: Boolean,
                                  pollingIntervalMs: Long = 1000)(implicit connection: Connection)
  extends GraphStage[SourceShape[EventEnvelope]] {

  import com.github.anicolaspp.akka.persistence.MapRDB._

  val out: Outlet[EventEnvelope] = if (isStreamingQuery) {
    Outlet("CurrentEventsByPersistenceIdSource")
  } else {
    Outlet("EventsByPersistenceId")
  }

  override def shape: SourceShape[EventEnvelope] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogicWithLogging(shape) with ByteArraySerializer {
    private var started = false
    private val buffer = mutable.Queue.empty[EventEnvelope]

    override implicit lazy val actorSystem: ActorSystem = system

    private lazy val eventSubscription = new PersistenceEntityEventsSubscriber(store, isStreamingQuery)

    private val callback = getAsyncCallback[Seq[Document]] { documents =>
      getEvents(documents)
        .fold(e => throw e,
          toPush => {
            buffer.enqueue(toPush: _*)

            deliver()
          })
    }

    private def getEvents(docs: Seq[Document]): Try[Seq[EventEnvelope]] = {
      val maybeEventEnvelopes = docs
        .map { document =>
          fromBytes[PersistentRepr](Journal.getBinaryRepresentationFrom(document)) match {
            case Success(pr) => Some(EventEnvelope(Offset.sequence(document.getIdBinary.toLong()), document.getString("persistenceId"), document.getIdBinary.toLong(), pr))
            case Failure(_) => None
          }
        }

      if (maybeEventEnvelopes.forall(_.isDefined)) {
        Success(maybeEventEnvelopes.map(_.get))
      } else {
        Failure(new Throwable(s"Some events failed to deserialize"))
      }
    }

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        if (buffer.isEmpty && !started) {
          started = true

          eventSubscription.subscribe(pollingIntervalMs, callback.invoke)

        } else if (buffer.nonEmpty) {
          deliver()
        }
      }
    })

    override def postStop(): Unit = eventSubscription.unsubscribe()

    private def deliver(): Unit = {
      if (buffer.nonEmpty) {
        val elem = buffer.dequeue
        push(out, elem)
      } else {
        if (!isStreamingQuery) {
          // we're done here, goodbye
          eventSubscription.unsubscribe()
          completeStage()
        }
      }
    }
  }
}



