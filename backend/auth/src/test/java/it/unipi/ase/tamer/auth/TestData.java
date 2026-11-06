package it.unipi.ase.tamer.auth;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import java.util.UUID;

/** Helpers shared by the auth tests. */
public final class TestData {

    public static final String PASSWORD = "pikachu123";

    private TestData() {
    }

    /** A unique valid username (the test database is shared by all test classes). */
    public static String uniqueUsername() {
        return "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static Response register(String username, String password) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", password))
                .post("/api/auth/register");
    }

    public static Response passwordGrant(String username, String password) {
        return given().contentType(ContentType.URLENC)
                .formParam("grant_type", "password")
                .formParam("username", username)
                .formParam("password", password)
                .post("/api/auth/token");
    }
}
