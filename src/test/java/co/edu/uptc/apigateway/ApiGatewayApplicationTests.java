package co.edu.uptc.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
        // The application context must start independently, with no
        // downstream service required to be running (Definition of Done:
        // "Gateway starts independently").
    }
}
