/*
 * Scala (https://www.scala-lang.org)
 *
 * Copyright EPFL and Lightbend, Inc.
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package io.getquill.util

import scala.util._
import scala.util.control.{ ControlThrowable, NonFatal }

/**
 * Copied from scala 2.13.0 source code. Hope won't break lis
 *
 * A utility for performing automatic resource management. It can be used to perform an
 * operation using resources, after which it releases the resources in reverse order
 * of their creation.
 *
 */
object Using {

  def apply[R: Releasable, A](resource: => R)(f: R => A): Try[A] = Try { Using.resource(resource)(f) }

  final class Manager private {
    import Manager._

    private var closed = false
    private[this] var resources: List[Resource[_]] = Nil

    def apply[R: Releasable](resource: R): R = {
      acquire(resource)
      resource
    }

    def acquire[R: Releasable](resource: R): Unit = {
      if (resource == null) throw new NullPointerException("null resource")
      if (closed) throw new IllegalStateException("Manager has already been closed")
      resources = new Resource(resource) :: resources
    }

    private def manage[A](op: Manager => A): A = {
      var toThrow: Throwable = null
      try {
        op(this)
      } catch {
        case t: Throwable =>
          toThrow = t
          null.asInstanceOf[A] // compiler doesn't know `finally` will throw
      } finally {
        closed = true
        var rs = resources
        resources = null // allow GC, in case something is holding a reference to `this`
        while (rs.nonEmpty) {
          val resource = rs.head
          rs = rs.tail
          try resource.release()
          catch {
            case t: Throwable =>
              if (toThrow == null) toThrow = t
              else toThrow = preferentiallySuppress(toThrow, t)
          }
        }
        if (toThrow != null) throw toThrow
      }
    }
  }

  object Manager {
    def apply[A](op: Manager => A): Try[A] = Try { (new Manager).manage(op) }

    private final class Resource[R](resource: R)(implicit releasable: Releasable[R]) {
      def release(): Unit = releasable.release(resource)
    }
  }

  private def preferentiallySuppress(primary: Throwable, secondary: Throwable): Throwable = {
    def score(t: Throwable): Int = t match {
      case _: VirtualMachineError                   => 4
      case _: LinkageError                          => 3
      case _: InterruptedException | _: ThreadDeath => 2
      case _: ControlThrowable                      => 0
      case e if !NonFatal(e)                        => 1 // in case this method gets out of sync with NonFatal
      case _                                        => -1
    }
    @inline def suppress(t: Throwable, suppressed: Throwable): Throwable = { t.addSuppressed(suppressed); t }

    if (score(secondary) > score(primary)) suppress(secondary, primary)
    else suppress(primary, secondary)
  }

  /**
   * Performs an operation using a resource, and then releases the resource,
   * even if the operation throws an exception. This method behaves similarly
   * to Java's try-with-resources.
   *
   *
   * @param resource the resource
   * @param body     the operation to perform with the resource
   * @tparam R the type of the resource
   * @tparam A the return type of the operation
   * @return the result of the operation, if neither the operation nor
   *         releasing the resource throws
   */
  def resource[R, A](resource: R)(body: R => A)(implicit releasable: Releasable[R]): A = {
    if (resource == null) throw new NullPointerException("null resource")

    var toThrow: Throwable = null
    try {
      body(resource)
    } catch {
      case t: Throwable =>
        toThrow = t
        null.asInstanceOf[A] // compiler doesn't know `finally` will throw
    } finally {
      if (toThrow eq null) releasable.release(resource)
      else {
        try releasable.release(resource)
        catch { case other: Throwable => toThrow = preferentiallySuppress(toThrow, other) }
        finally throw toThrow
      }
    }
  }

  /**
   * Performs an operation using two resources, and then releases the resources
   * in reverse order, even if the operation throws an exception. This method
   * behaves similarly to Java's try-with-resources.
   *
   *
   * @param resource1 the first resource
   * @param resource2 the second resource
   * @param body      the operation to perform using the resources
   * @tparam R1 the type of the first resource
   * @tparam R2 the type of the second resource
   * @tparam A  the return type of the operation
   * @return the result of the operation, if neither the operation nor
   *         releasing the resources throws
   */
  def resources[R1: Releasable, R2: Releasable, A](
    resource1: R1,
    resource2: => R2
  )(body: (R1, R2) => A): A =
    resource(resource1) { r1 =>
      resource(resource2) { r2 =>
        body(r1, r2)
      }
    }

  /**
   * Performs an operation using three resources, and then releases the resources
   * in reverse order, even if the operation throws an exception. This method
   * behaves similarly to Java's try-with-resources.
   *
   *
   * @param resource1 the first resource
   * @param resource2 the second resource
   * @param resource3 the third resource
   * @param body      the operation to perform using the resources
   * @tparam R1 the type of the first resource
   * @tparam R2 the type of the second resource
   * @tparam R3 the type of the third resource
   * @tparam A  the return type of the operation
   * @return the result of the operation, if neither the operation nor
   *         releasing the resources throws
   */
  def resources[R1: Releasable, R2: Releasable, R3: Releasable, A](
    resource1: R1,
    resource2: => R2,
    resource3: => R3
  )(body: (R1, R2, R3) => A): A =
    resource(resource1) { r1 =>
      resource(resource2) { r2 =>
        resource(resource3) { r3 =>
          body(r1, r2, r3)
        }
      }
    }

  /**
   * Performs an operation using four resources, and then releases the resources
   * in reverse order, even if the operation throws an exception. This method
   * behaves similarly to Java's try-with-resources.
   *
   *
   * @param resource1 the first resource
   * @param resource2 the second resource
   * @param resource3 the third resource
   * @param resource4 the fourth resource
   * @param body      the operation to perform using the resources
   * @tparam R1 the type of the first resource
   * @tparam R2 the type of the second resource
   * @tparam R3 the type of the third resource
   * @tparam R4 the type of the fourth resource
   * @tparam A  the return type of the operation
   * @return the result of the operation, if neither the operation nor
   *         releasing the resources throws
   */
  def resources[R1: Releasable, R2: Releasable, R3: Releasable, R4: Releasable, A](
    resource1: R1,
    resource2: => R2,
    resource3: => R3,
    resource4: => R4
  )(body: (R1, R2, R3, R4) => A): A =
    resource(resource1) { r1 =>
      resource(resource2) { r2 =>
        resource(resource3) { r3 =>
          resource(resource4) { r4 =>
            body(r1, r2, r3, r4)
          }
        }
      }
    }

  /**
   * A typeclass describing how to release a particular type of resource.
   *
   * A resource is anything which needs to be released, closed, or otherwise cleaned up
   * in some way after it is finished being used, and for which waiting for the object's
   * garbage collection to be cleaned up would be unacceptable. For example, an instance of
   * java.io.OutputStream would be considered a resource, because it is important to close
   * the stream after it is finished being used.
   *
   * An instance of `Releasable` is needed in order to automatically manage a resource
   * with [[Using `Using`]]. An implicit instance is provided for all types extending
   * java.lang.AutoCloseable.
   *
   * @tparam R the type of the resource
   */
  trait Releasable[-R] {
    /** Releases the specified resource. */
    def release(resource: R): Unit
  }

  object Releasable {
    /** An implicit `Releasable` for java.lang.AutoCloseable `AutoCloseable`s. */
    implicit object AutoCloseableIsReleasable extends Releasable[AutoCloseable] {
      def release(resource: AutoCloseable): Unit = resource.close()
    }
  }

}
