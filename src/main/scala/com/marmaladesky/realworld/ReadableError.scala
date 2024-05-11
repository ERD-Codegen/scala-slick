package com.marmaladesky.realworld

import org.http4s.Status

/** A lightweight exception that is expected to be human-readable and used as a request response
  *
  * @param codeHint
  *   is a hint about HTTP response code that could be provided to error handler
  */
case class ReadableError(
    message: String,
    cause: Throwable = null,
    codeHint: Int = Status.BadRequest.code
) extends Throwable(message, cause, false, false)
