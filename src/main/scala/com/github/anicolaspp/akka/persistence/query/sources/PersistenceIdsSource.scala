package com.github.anicolaspp.akka.persistence.query.sources

import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageLogicWithLogging, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import com.github.anicolaspp.akka.persistence.MapRDB
import com.github.anicolaspp.akka.persistence.query.sources.subscriber.PersistenceIdsSubscription
import org.ojai.store.{Connection, DocumentStore, QueryCondition}

import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.util.Try

case class PersistenceIdsSource(store: DocumentStore, pollingIntervalMs: Long = 1000)(implicit connection: Connection)
  extends GraphStage[SourceShape[String]] {

  private val out: Outlet[String] = Outlet("PersistenceIdsSource")

  override def shape: SourceShape[String] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogicWithLogging(shape) {
    private val buffer = mutable.Queue.empty[String]
    private var downstreamWaiting = false
    private var started = false

    private val orderSet = mutable.SortedSet.empty[String]

    private val callback = getAsyncCallback[Seq[String]] { docs => pushMessages(docs) }

    implicit def ec: ExecutionContextExecutor = materializer.executionContext

    private def pushMessages(messages: Seq[String]): Unit = synchronized {
      val messagesToPush = messages.filterNot(orderSet.contains)

      if (messagesToPush.nonEmpty && started) {

        orderSet ++= messagesToPush

        buffer.enqueue(messagesToPush: _*)

        if (buffer.nonEmpty) {
          downstreamWaiting = true

          deliver()
        }
      } else if (!started) {
        log.info("CANNOT PUSH BEFORE STARTING")
      }
    }

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        downstreamWaiting = true
        started = true
        if (buffer.isEmpty && !started) {
          tryQuery(store).fold(
            e => getAsyncCallback[Unit] { _ => failStage(e) }.invoke(()),
            values => callback.invoke(values)
          )

        } else if (buffer.nonEmpty) {
          deliver()
        }
      }
    })

    private def tryQuery(store: DocumentStore) = Try {
      import scala.collection.JavaConverters._

      val result = store.find(connection.newQuery().select(MapRDB.MAPR_ENTITY_ID).build()).asScala.map(_.getIdString).toSeq

      result
    }

    private lazy val eventSubscription = PersistenceIdsSubscription(store)

    override def preStart(): Unit = eventSubscription.subscribe(pollingIntervalMs, callback.invoke)

    override def postStop(): Unit = eventSubscription.unsubscribe()

    private def deliver(): Unit = {
      if (downstreamWaiting) {
        downstreamWaiting = false

        if (buffer.nonEmpty) {
          val elem = buffer.dequeue

          push(out, elem)
        }
      }
    }
  }
}



