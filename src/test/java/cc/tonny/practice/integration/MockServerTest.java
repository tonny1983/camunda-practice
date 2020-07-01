package cc.tonny.practice.integration;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class MockServerTest {
    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this, 19000);

    private MockServerClient mockServerClient;

    @Test
    public void dummy() throws IOException {
        mockServerClient.when(HttpRequest.request("/foo"))
                .respond(HttpResponse.response().withStatusCode(200));

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet("http://localhost:19000/foo");
        CloseableHttpResponse response = httpClient.execute(httpGet);
        try {
            assertEquals(200, response.getStatusLine().getStatusCode());
        } finally {
            httpClient.close();
        }
    }
}
