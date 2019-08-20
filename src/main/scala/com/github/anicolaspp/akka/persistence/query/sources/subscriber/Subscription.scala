package com.github.anicolaspp.akka.persistence.query.sources.subscriber

trait Subscription[A] {

  def isRunning: Boolean

  def subscribe(pollingIntervalMs: Long, fn: A => Unit): Unit

  def unsubscribe(): Unit
}
