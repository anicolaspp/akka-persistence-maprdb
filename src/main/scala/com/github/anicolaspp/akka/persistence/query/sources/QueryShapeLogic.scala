package com.github.anicolaspp.akka.persistence.query.sources

import akka.actor.ActorSystem
import akka.stream.SourceShape
import akka.stream.stage.{GraphStageLogicWithLogging, OutHandler}
import com.github.anicolaspp.akka.persistence.ByteArraySerializer
import com.github.anicolaspp.akka.persistence.query.subscriber.Subscription
import org.ojai.Document

import scala.collection.mutable
import scala.util.Try

/**
 * Higher lever abstraction that that defines a Source should behave.
 *
 * Implementors must choose what kind of event subscription they will use and the corresponding logic the subscription
 * submits the pulled object from the underlying storage.
 *
 * @param shape             The Graph Shape to be used
 * @param system            The corresponding Actor System
 * @param isStreamingQuery  Defines is Shape will continually run in streaming fashion or it runs just once.
 * @param pollingIntervalMs If the Shape runs on streaming fashion, [[pollingIntervalMs]] defines the time between polls
 * @tparam A
 */
abstract class QueryShapeLogic[A](shape: SourceShape[Any],
                                  system: ActorSystem,
                                  isStreamingQuery: Boolean,
                                  pollingIntervalMs: Long)
  extends GraphStageLogicWithLogging(shape)
    with ByteArraySerializer {

  override implicit lazy val actorSystem: ActorSystem = system

  private var started = false
  private val buffer = mutable.Queue.empty[A]

  private val callback = getAsyncCallback[Seq[Document]] { documents =>
    getEvents(documents)
      .fold(e => throw e,
        toPush => {
          buffer.enqueue(toPush: _*)

          deliver()
        })
  }

  /**
   * Implementors should define what kind of subscription they are going to use.
   *
   * @return A subscription implementation.
   */
  def eventSubscription: Subscription[Seq[Document]]

  /**
   * Every time the eventSubscription has new messages, it calls the callback function that in turns, uses getEvents
   * to generates the events to be pushed to downstream processor using this function.
   *
   * @param docs The list of documents received from the eventSubscription.
   * @return A list of events generated from the received sequence of documents.
   */
  def getEvents(docs: Seq[Document]): Try[Seq[A]]

  /**
   * Once the Graph Shape is pulled the first time, it starts the selected [[eventSubscription]] using the
   * [[pollingIntervalMs]] and the define callback function [[callback]]
   */
  setHandler(shape.out, new OutHandler {
    override def onPull(): Unit = {
      if (buffer.isEmpty && !started) {
        started = true

        eventSubscription.subscribe(pollingIntervalMs, callback.invoke)

      } else {
        deliver()
      }
    }
  })

  override def postStop(): Unit = eventSubscription.unsubscribe()

  private def deliver(): Unit = {
    if (buffer.nonEmpty) {
      val elem = buffer.dequeue
      push(shape.out, elem)
    } else {
      if (!isStreamingQuery) {
        // we're done here, goodbye
        eventSubscription.unsubscribe()
        completeStage()
      }
    }
  }
}
