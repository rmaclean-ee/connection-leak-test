package com.github.phillbarber.connectionleak;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static com.github.phillbarber.connectionleak.AppConfig.USEFUL_SERVICE_PORT;
import static com.github.phillbarber.connectionleak.HealthCheckResponseChecker.hasHealthyMessage;
import static com.github.phillbarber.connectionleak.HealthCheckResponseChecker.hasUnHealthyMessage;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;

public class AcceptanceTestThatRunsMoreTimesThanServerCanTakeConnectionsSimultaneously {

    @ClassRule //Must be a static ClassRule as otherwise dropwizard will terminate after first execution
    public static final DropwizardAppRule<AppConfig> appRule = new DropwizardAppRule<>(ConnectionLeakApp.class,
            ResourceFileUtils.getFileFromClassPath(ConnectionLeakApp.DEFAULT_CONFIG_FILE).getAbsolutePath());
    public static final int NUMBER_OF_CONTAINER_THREADS = 2;
    public static final int NUMBER_OF_JETTY_ACCEPTORS = 1;

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().jettyAcceptors(NUMBER_OF_JETTY_ACCEPTORS).containerThreads(NUMBER_OF_CONTAINER_THREADS).port(USEFUL_SERVICE_PORT));

    //If there is a connection leak, this will fail if we run for one more iteration than there are threads to deal with
    //connections. In this case, the number of threads to deal with connections is NUMBER_OF_CONTAINER_THREADS - NUMBER_OF_JETTY_ACCEPTORS
    //since each acceptor occupies a thread.
    //This fails with java.net.SocketTimeoutException
    @Test
    @Repeat(times=(NUMBER_OF_CONTAINER_THREADS - NUMBER_OF_JETTY_ACCEPTORS)+1)
    public void givenUsefulServiceIsOK_whenHealthCheckCalled_returnsHealthy(){
        new StubbedUsefulService(wireMockRule).addStubForVersionPageThatReturnsOK();
        ClientResponse clientResponse = getAdminResource(AppConfig.HEALTHCHECK_URI).get(ClientResponse.class);
        assertThat(clientResponse.getEntity(String.class), hasHealthyMessage());
    }

    @Test
    @Repeat(times=(NUMBER_OF_CONTAINER_THREADS - NUMBER_OF_JETTY_ACCEPTORS)+1)
    public void givenUsefulServicReturnsError_whenHealthCheckCalled_returnsNotHealthy(){
        new StubbedUsefulService(wireMockRule).addStubForVersionPageThatReturnsError();
        ClientResponse clientResponse = getAdminResource(AppConfig.HEALTHCHECK_URI).get(ClientResponse.class);
        assertThat(clientResponse.getEntity(String.class), hasUnHealthyMessage());
    }


    private WebResource getAdminResource(String resource) {
        return new Client().resource("http://localhost:" + appRule.getAdminPort()).path(resource);
    }
}
