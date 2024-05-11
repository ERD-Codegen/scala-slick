package com.marmaladesky.realworld.model

case class Page[T](total: Int, items: Seq[T]) {

  def map[B](f: T => B): Page[B] = this.copy(items = this.items.map(f))

}
