package com.github.anicolaspp.akka.persistence

sealed trait StoreType

case object Journal extends StoreType {
  override def toString: String = "journal"
}

case object Snapshot extends StoreType {
  override def toString: String = "snapshot"
}