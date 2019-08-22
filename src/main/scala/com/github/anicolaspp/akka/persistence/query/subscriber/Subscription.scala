package com.github.anicolaspp.akka.persistence.query.subscriber

/**
 * Basic Subscription Abstraction.
 *
 * In MapR this can be implemented by polling the MapR-DB on intervals as [[EventsPollingSubscriber]].
 *
 * Another possible implementation could be using MapR-DB CDC technology which might allow us to avoid polling
 * completely.
 *
 * @tparam A Type being piped on the [[Subscription]]
 */
trait Subscription[A] {

  def isRunning: Boolean

  def subscribe(pollingIntervalMs: Long, fn: A => Unit): Unit

  def unsubscribe(): Unit
}
