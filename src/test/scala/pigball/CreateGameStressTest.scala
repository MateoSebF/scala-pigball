package pigball

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class CreateGameStressTest extends Simulation {

  // Load VU count from system properties
  val vu: Int = Integer.getInteger("vu", 1)

  // Define HTTP configuration
  val httpProtocol = http
    .baseUrl("https://piggame.duckdns.org:8080") // Cambia esto a la URL de tu servidor
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36")

  // Define scenario
  val createGameScenario = scenario("Create Game Scenario")
    .exec(
      http("Create Game Request")
        .post("/createGame")
        .body(StringBody(
          """
          {
            "gameId": "12345",
            "gameName": "TestGame",
            "creatorName": "TestCreator",
            "maxPlayers": 10,
            "privateGame": true
          }
          """
        )).asJson
        .check(status.is(201)) // Verifica que el estado de la respuesta sea 200
    )

  // Define assertions
  val assertion = global.failedRequests.count.is(0)

  // Define injection profile and execute the test
  setUp(
    createGameScenario.inject(atOnceUsers(vu)) // Configura el número de usuarios virtuales a ejecutar
  ).assertions(assertion)
   .protocols(httpProtocol)
}



