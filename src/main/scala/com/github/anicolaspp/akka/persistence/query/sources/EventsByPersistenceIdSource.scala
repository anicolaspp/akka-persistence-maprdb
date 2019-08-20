package com.github.anicolaspp.akka.persistence.query.sources

import akka.actor.ActorSystem
import akka.persistence.PersistentRepr
import akka.persistence.query.{EventEnvelope, Offset}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageLogicWithLogging, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import com.github.anicolaspp.akka.persistence.ByteArraySerializer
import com.github.anicolaspp.akka.persistence.journal.Journal
import org.ojai.store.{Connection, DocumentStore, QueryCondition}

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

class EventsByPersistenceIdSource(store: DocumentStore,
                                  system: ActorSystem,
                                  fromSequenceNr: Long,
                                  toSequenceNr: Long)(implicit connection: Connection)
  extends GraphStage[SourceShape[EventEnvelope]] {

  import com.github.anicolaspp.akka.persistence.MapRDB._

  val out: Outlet[EventEnvelope] = Outlet("CurrentEventsByPersistenceIdSource")

  override def shape: SourceShape[EventEnvelope] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogicWithLogging(shape) with ByteArraySerializer {
    private var started = false
    private val buffer = mutable.Queue.empty[EventEnvelope]

    override implicit lazy val actorSystem: ActorSystem = system

    private val callback = getAsyncCallback[Seq[EventEnvelope]] { events =>
      buffer.enqueue(events: _*)

      deliver()
    }

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        if (buffer.isEmpty && !started) {
          started = true

          val events = tryQuery(store).map { docs =>

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

          events.fold(e => throw e, events => events.foreach(callback.invoke))

        } else if (buffer.nonEmpty) {
          deliver()
        }
      }
    })

    private def tryQuery(store: DocumentStore) = Try {
      import scala.collection.JavaConverters._

      val condition = connection
        .newCondition()
        .and()
        .is(MAPR_ENTITY_ID, QueryCondition.Op.GREATER_OR_EQUAL, fromSequenceNr.toBinaryId())
        .is(MAPR_ENTITY_ID, QueryCondition.Op.LESS_OR_EQUAL, toSequenceNr.toBinaryId())
        .close()
        .build()

      val query = connection.newQuery().where(condition).build()

      val result = store.find(query).asScala.toSeq

      result
    }

    private def deliver(): Unit = {
      if (buffer.nonEmpty) {
        val elem = buffer.dequeue
        push(out, elem)
      } else {
        // we're done here, goodbye
        completeStage()
      }
    }
  }
}
