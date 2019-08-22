package com.github.anicolaspp.akka.persistence.query.subscriber

import org.ojai.Document
import org.ojai.store.DocumentStore

import scala.util.Try

/**
 * Higher lever abstraction that encapsulates a polling event subscriber. This is one of the many possible
 * implementations of [[Subscription]]
 *
 * Implementor must define the specific query to be executed again the underlying data source.
 *
 * @param store     [[DocumentStore]] to pull data from.
 * @param streaming Indicates that [[EventsPollingSubscriber]] should run on a streaming fashion or stop after the
 *                  first poll.
 */
abstract class EventsPollingSubscriber(store: DocumentStore, streaming: Boolean) extends Subscription[Seq[Document]] {

  private var running = false

  override def isRunning: Boolean = running

  /**
   * Starts a polling threads and executes [[tryQuery]] on intervals.
   *
   * The [[tryQuery]] function is implementor specific.
   *
   * @param pollingIntervalMs    Polling interval.
   * @param subscriptionCallBack Callback function. [[subscriptionCallBack]] is used to notify about new data arrival.
   */
  override def subscribe(pollingIntervalMs: Long, subscriptionCallBack: Seq[Document] => Unit): Unit = {
    println(s"[$subscriptionName]: STARTING SUBSCRIPTION...")

    val subscriber = new Thread {
      setDaemon(true)

      override def run(): Unit = {
        running = true

        while (running) {
          val result = tryQuery(store).getOrElse(Seq.empty)

          subscriptionCallBack(result)

          if (!streaming) {
            running = false
          }

          Thread.sleep(pollingIntervalMs)
        }
      }
    }

    subscriber.start()
  }

  /**
   * Signal the polling thread to stop processing new records for the given store.
   */
  override def unsubscribe(): Unit = running = false

  /**
   * Implementors must define their own query strategy. Normally, it implies keeping a cursor so following queries
   * do not retrieve the same data, but only new added data points.
   *
   * @param store Store handler to the Journal
   * @return
   */
  def tryQuery(store: DocumentStore): Try[Seq[Document]]

  def subscriptionName: String
}
