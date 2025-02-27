/*
 * Copyright (C) 2018-2022 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package com.here.platform.artifact.sbt.resolver

import org.apache.http.protocol.HttpContext
import org.apache.http.{Header, HttpResponse, StatusLine}
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers._

class XRateLimitServiceUnavailableRetryStrategyTest extends AnyFunSuite with BeforeAndAfter with MockFactory {

  private var httpResponse = mock[HttpResponse]

  private var httpContext = mock[HttpContext]

  private var strategy = new XRateLimitServiceUnavailableRetryStrategy

  before {
    httpResponse = mock[HttpResponse]
    httpContext = mock[HttpContext]
    strategy = new XRateLimitServiceUnavailableRetryStrategy
    val statusLineMock = mock[StatusLine]
    (statusLineMock.getStatusCode _).expects().returning(429)
    (httpResponse.getStatusLine _).expects().returning(statusLineMock)
  }

  test("testRetryTriggeredFor429ReponseCode") {
    val result = strategy.retryRequest(httpResponse, 1, httpContext)
    result should equal (true)
  }

  test("testRetryIntervalIsUsedFromXRateLimitResetHeader") {
    val headerMock = mock[Header]
    (httpResponse.getFirstHeader _).expects(strategy.X_RATE_LIMIT_RESET_HEADER).returning(headerMock)
    (headerMock.getValue _).expects().returning("99")
    (httpResponse.containsHeader _).expects(strategy.X_RATE_LIMIT_RESET_HEADER).returning(true)
    strategy.retryRequest(httpResponse, 1, httpContext)
    val result = strategy.getRetryInterval
    result should equal (99000)
  }

  test("testRetryIntervalIsUsedFromXRateLimitResetHeaderWhenRetryAfterPresent") {
    val headerMockXrateLimit = mock[Header]
    (httpResponse.getFirstHeader _).expects(strategy.X_RATE_LIMIT_RESET_HEADER).returning(headerMockXrateLimit)
    (headerMockXrateLimit.getValue _).expects().returning("99")
    (httpResponse.containsHeader _).expects(strategy.X_RATE_LIMIT_RESET_HEADER).returning(true)

    val headerRetryAfter = mock[Header]
    (httpResponse.getFirstHeader _).stubs(strategy.RETRY_AFTER_HEADER).returning(headerRetryAfter)
    (headerRetryAfter.getValue _).stubs().returning("1")
    (httpResponse.containsHeader _).stubs(strategy.RETRY_AFTER_HEADER).returning(true)

    strategy.retryRequest(httpResponse, 1, httpContext)
    val result = strategy.getRetryInterval
    result should equal (99000)
  }

  test("testRetryIntervalIsUsedFromRetryAfter") {
    val headerRetryAfter = mock[Header]
    (httpResponse.getFirstHeader _).expects(strategy.RETRY_AFTER_HEADER).returning(headerRetryAfter)
    (headerRetryAfter.getValue _).expects().returning("1")

    (httpResponse.containsHeader _).expects(strategy.X_RATE_LIMIT_RESET_HEADER).returning(false)

    strategy.retryRequest(httpResponse, 1, httpContext)
    val result = strategy.getRetryInterval
    result should equal (1000)
  }

  test("testDefaultRetryIntervalIsUsedWhenXRateLimitResetHeaderContainsNonNumericValue") {
    val headerMockXrateLimit = mock[Header]
    (httpResponse.getFirstHeader _).expects(strategy.X_RATE_LIMIT_RESET_HEADER).returning(headerMockXrateLimit)
    (headerMockXrateLimit.getValue _).expects().returning("asd")
    (httpResponse.containsHeader _).expects(strategy.X_RATE_LIMIT_RESET_HEADER).returning(true)
    strategy.retryRequest(httpResponse, 1, httpContext)
    val result = strategy.getRetryInterval
    result should equal (5000)
  }

  test("testDefaultRetryIntervalIsUsedWhenExceptionOccurred") {
    (httpResponse.containsHeader _).expects(strategy.X_RATE_LIMIT_RESET_HEADER).throwing(new RuntimeException)
    strategy.retryRequest(httpResponse, 1, httpContext)
    val result = strategy.getRetryInterval
    result should equal (5000)
  }

}