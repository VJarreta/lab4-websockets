package websockets;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import websockets.web.ElizaServerEndpoint;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import static java.lang.String.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ElizaServerTest {

    private static final Logger LOGGER = Grizzly.logger(ElizaServerTest.class);

	private Server server;

	@Before
	public void setup() throws DeploymentException {
		server = new Server("localhost", 8025, "/websockets",
            new HashMap<>(), ElizaServerEndpoint.class);
		server.start();
	}

	@Test(timeout = 5000)
	public void onOpen() throws DeploymentException, IOException, URISyntaxException, InterruptedException {
		CountDownLatch latch = new CountDownLatch(3);
		List<String> list = new ArrayList<>();
		ClientEndpointConfig configuration = ClientEndpointConfig.Builder.create().build();
		ClientManager client = ClientManager.createClient();
		Session session = client.connectToServer(new Endpoint() {

			@Override
			public void onOpen(Session session, EndpointConfig config) {
				session.addMessageHandler(new ElizaOnOpenMessageHandler(list, latch));
			}

		}, configuration, new URI("ws://localhost:8025/websockets/eliza"));
        session.getAsyncRemote().sendText("bye");
        latch.await();
		assertEquals(3, list.size());
		assertEquals("The doctor is in.", list.get(0));
	}

	@Test(timeout = 1000)
	public void onChat() throws DeploymentException, IOException, URISyntaxException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(4);
		List<String> list = new ArrayList<>();
		ClientEndpointConfig configuration = ClientEndpointConfig.Builder.create().build();
		ClientManager client = ClientManager.createClient();
        Session session = client.connectToServer(new Endpoint() {

            @Override
            public void onOpen(Session session, EndpointConfig config) {
                session.addMessageHandler(new ElizaOnOpenMessageHandler(list, latch));
            }

        }, configuration, new URI("ws://localhost:8025/websockets/eliza"));
        /*
         Tried to use: "I had a weird dream yesterday...", but DOCTOR says "Why do you think so?" due to the
          "yes" part of "yesterday", probably not the best behaviour but solved it easily.
         */
        session.getAsyncRemote().sendText("I had a weird dream two days ago...");
        session.getAsyncRemote().sendText("bye");
        latch.await();
        assertEquals(4, list.size());
        assertEquals("The doctor is in.", list.get(0));
        // Check that DOCTOR asks about our mental health
        List<String> temp12 = Arrays.asList("What does that dream suggest to you?", "Do you dream often?",
                "What persons appear in your dreams?", "Are you disturbed by your dreams?");
        assertTrue(temp12.contains(list.get(3)));
	}

	@After
	public void close() {
		server.stop();
	}

    private static class ElizaOnOpenMessageHandler implements MessageHandler.Whole<String> {

        private final List<String> list;
        private final CountDownLatch latch;

        ElizaOnOpenMessageHandler(List<String> list, CountDownLatch latch) {
            this.list = list;
            this.latch = latch;
        }

        @Override
        public void onMessage(String message) {
            LOGGER.info(format("Client received \"%s\"", message));
            list.add(message);
            latch.countDown();
        }
    }

    private static class ElizaEndpointToComplete extends Endpoint {

        private final List<String> list;

        ElizaEndpointToComplete(List<String> list) {
            this.list = list;
        }

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.addMessageHandler(new ElizaMessageHandlerToComplete());
        }

        private class ElizaMessageHandlerToComplete implements MessageHandler.Whole<String> {

            @Override
            public void onMessage(String message) {
                list.add(message);
            }
        }
    }
}
