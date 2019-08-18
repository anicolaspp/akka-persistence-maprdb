package com.github.anicolaspp.akka.persistence

import akka.actor.ActorSystem
import akka.serialization.SerializationExtension

import scala.reflect.{ClassTag, classTag}
import scala.util.Try

trait ByteArraySerializer {
  implicit val actorSystem: ActorSystem

  private val serialization = SerializationExtension(actorSystem)

  def toBytes(data: AnyRef): Try[Array[Byte]] = serialization.serialize(data)

  def fromBytes[T: ClassTag](a: Array[Byte])(implicit actorSystem: ActorSystem): Try[T] =
    SerializationExtension(actorSystem).deserialize(a, classTag[T].runtimeClass.asInstanceOf[Class[T]])
}
