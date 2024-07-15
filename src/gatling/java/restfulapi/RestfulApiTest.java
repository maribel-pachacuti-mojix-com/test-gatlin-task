package restfulapi;

import java.time.Duration;
import java.util.*;
import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class RestfulApiTest extends Simulation {

    String baseUrl = System.getProperty("baseUrl", "https://api.restful-api.dev");

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(baseUrl)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    FeederBuilder.FileBased<Object> feeder = jsonFile("data/objects.json").circular();

    ScenarioBuilder scn = scenario("Restful API Test")
            .feed(feeder)
            .exec(
                    http("POST Create Object")
                            .post("/objects")
                            .body(StringBody(session -> {
                                Map<String, Object> data = session.getMap("data");
                                String body = String.format("{\"name\":\"%s\",\"data\":{\"year\":%d,\"price\":%.2f,\"CPU model\":\"%s\",\"Hard disk size\":\"%s\"}}",
                                        session.getString("name"),
                                        (Integer) data.get("year"),
                                        (Double) data.get("price"),
                                        data.get("CPU model"),
                                        data.get("Hard disk size"));
                                return body;
                            })).asJson()
                            .check(status().is(200))
                            .check(jsonPath("$.id").saveAs("postId"))
            )
            .exec(session -> {
                String postId = session.getString("postId");
                if (postId == null || postId.isEmpty()) {
                    System.err.println("Error: 'postId' not found in session");
                }
                return session;
            })
            .pause(1)
            .exec(
                    http("PUT Update Object")
                            .put("/objects/#{postId}")
                            .body(StringBody(session -> {
                                Map<String, Object> data = session.getMap("data");
                                String updatedName = session.getString("name") + " updated";
                                String body = String.format("{\"name\":\"%s\",\"data\":{\"year\":%d,\"price\":%.2f,\"CPU model\":\"%s\",\"Hard disk size\":\"%s\"}}",
                                        updatedName,
                                        (Integer) data.get("year"),
                                        (Double) data.get("price"),
                                        data.get("CPU model"),
                                        data.get("Hard disk size"));
                                return body;
                            })).asJson()
                            .check(status().is(200))
            )
            .pause(1)
            .exec(
                    http("GET Updated Object")
                            .get("/objects/#{postId}")
                            .check(jmesPath("name").isEL("#{name} updated"))
                            .check(bodyString().saveAs("BODY"))
                            .check(status().is(200))
            )
            .exec(
                    session -> {
                        System.out.println("Objeto: " + session.getString("BODY"));
                        return session;
                    }
            );

    {
        setUp(
                scn.injectClosed(
                        rampConcurrentUsers(1).to(10).during(Duration.ofSeconds(10)),
                        constantConcurrentUsers(10).during(Duration.ofSeconds(20))
                )
        ).protocols(httpProtocol);
    }
}
