package pigball

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class PigBallGameSimulation extends Simulation {

  // Número de jugadores por sala
  val playersPerRoom = 19

  // Configuración HTTP + WS
  val httpProtocol = http
    .baseUrl("https://piggame.duckdns.org:8080")
    .wsBaseUrl("wss://piggame.duckdns.org:8080")
    .wsReconnect
    .wsMaxReconnects(5)
    .wsUnmatchedInboundMessageBufferSize(1000)

  // Carácter nulo como literal
  private val NUL = "\u0000"

  // Helpers para frames STOMP
  def stompConnect: String =
    new StringBuilder()
      .append("CONNECT\n")
      .append("accept-version:1.2\n")
      .append("heart-beat:10000,10000\n")
      .append("\n")
      .append(NUL)
      .toString()

  def stompSubscribe(subId: String, dest: String): String =
    new StringBuilder()
      .append("SUBSCRIBE\n")
      .append(s"id:$subId\n")
      .append(s"destination:$dest\n")
      .append("\n")
      .append(NUL)
      .toString()

  def stompSend(dest: String, body: String): String = {
    val len = body.getBytes("UTF-8").length
    new StringBuilder()
      .append("SEND\n")
      .append(s"destination:$dest\n")
      .append("content-type:application/json\n")
      .append(s"content-length:$len\n")
      .append("\n")
      .append(body)
      .append(NUL)
      .toString()
  }

  // Feeder para asignar índice único a cada jugador
  val idxFeeder = Iterator.from(1).map(i => Map("playerIdx" -> i))

  // Construye un escenario parametrizado para una sala dada
  def makeScenario(roomId: String) = scenario(s"PigBall Game Flow – Room $roomId")
    // 1) Setea el ID de la sala
    .exec(session => session.set("gameId", roomId))
    // 2) Alimenta cada VU con su índice único para generar nombres distintos
    .feed(idxFeeder)
    .exec { session =>
      val id   = java.util.UUID.randomUUID().toString
      val idx  = session("playerIdx").as[Int]
      val name = s"Player-$idx"
      session
        .set("playerId", id)
        .set("playerName", name)
    }
    // 3) WS handshake + STOMP CONNECT + SUBSCRIBE
    .exec(
      ws("WS Connect").connect("/pigball")
        .onConnected(
          exec(ws("STOMP CONNECT").sendText(stompConnect))
            .pause(100.millis)
            .exec(ws("SUBSCRIBE players")
              .sendText(session => stompSubscribe("sub-players", s"/topic/players/${session("gameId").as[String]}")))
            .exec(ws("SUBSCRIBE started")
              .sendText(session => stompSubscribe("sub-started", s"/topic/started/${session("gameId").as[String]}")))
            .exec(ws("SUBSCRIBE play")
              .sendText(session => stompSubscribe("sub-play", s"/topic/play/${session("gameId").as[String]}")))
            .pause(100.millis)

            // 4) JOIN
            .exec(
              ws("SEND join").sendText(session => {
                val name   = session("playerName").as[String]
                val id     = session("playerId").as[String]
                val gameId = session("gameId").as[String]
                stompSend(s"/app/join/$gameId", s"""{"name":"$name","id":"$id"}""")
              })
            )
            .pause(100.millis)

            // 5) START
            .exec(ws("SEND start").sendText(session => 
              stompSend(s"/app/start/${session("gameId").as[String]}", "{}")
            ))
            .pause(5.seconds)

            // 6) LOOP de PLAY (~60 msg/s durante ~50s)
            .repeat(3000) {
              exec { session =>
                val rand = scala.util.Random
                val x = {
                  val r = rand.between(-1, 3)
                  if (r == 2) 1 else r
                }
                val y = {
                  val r = rand.between(-1, 3)
                  if (r == 2) 1 else r
                }
                val isKicking = rand.nextBoolean()
                val name      = session("playerName").as[String]
                val json      = s"""{"player":"$name","dx":$x,"dy":$y,"isKicking":$isKicking}"""
                session.set("playPayload", json)
              }.exec(
                ws("SEND play")
                  .sendText(session => {
                    val payload = session("playPayload").as[String]
                    val gameId  = session("gameId").as[String]
                    stompSend(s"/app/play/$gameId", payload)
                  })
              ).pause(16.millis)
            }

            // 7) CLOSE WS
            .exec(ws("CLOSE WS").close)
        )
    )

  // Creamos un escenario para cada sala 1, 2, 3 y 4
  val scnRoom1 = makeScenario("1")
  val scnRoom2 = makeScenario("2")
  val scnRoom3 = makeScenario("3")
  val scnRoom4 = makeScenario("4")
  val scnRoom5 = makeScenario("5")

  // Setup: inyectamos 19 jugadores en cada sala
  setUp(
    scnRoom1.inject(atOnceUsers(playersPerRoom)),
    scnRoom2.inject(atOnceUsers(playersPerRoom)),
    scnRoom3.inject(atOnceUsers(playersPerRoom)),
    scnRoom4.inject(atOnceUsers(playersPerRoom)),
    scnRoom5.inject(atOnceUsers(playersPerRoom))
  ).protocols(httpProtocol)
    .assertions(
      global.failedRequests.count.is(0),
      forAll.failedRequests.percent.lt(1)
    )
}
