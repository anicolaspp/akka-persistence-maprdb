package com.github.anicolaspp.akka.persistence.query.sources

import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageLogicWithLogging, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import org.ojai.store.{Connection, DocumentStore}

import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.util.Try

class CurrentPersistenceIdsSource(store: DocumentStore)(implicit connection: Connection)
  extends GraphStage[SourceShape[String]] {

  private val out: Outlet[String] = Outlet("CurrentPersistenceIdsSource")

  override def shape: SourceShape[String] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogicWithLogging(shape) {
    private var start = true
    private val index = 0
    private val buffer = mutable.Queue.empty[String]

    private implicit def ec: ExecutionContextExecutor = materializer.executionContext

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        if (buffer.isEmpty && (start || index > 0)) {
          val callback = getAsyncCallback[Seq[String]] { docs =>

            start = false
            buffer.enqueue(docs: _*)

            deliver()
          }

          tryQuery(store).fold(
            e => getAsyncCallback[Unit] { _ => failStage(e) }.invoke(()),
            value => callback.invoke(value))

        } else {
          deliver()
        }
      }
    })

    private def tryQuery(store: DocumentStore) = Try {
      import scala.collection.JavaConverters._

      val result = store.find().asScala.map(_.getIdString).toSeq

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

object CurrentPersistenceIdsSource {
  case object Continue
}