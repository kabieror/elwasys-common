/**
 *
 */
package org.kabieror.elwasys.common.maintenance;

/**
 * @author Oliver Kabierschke
 *
 */
//public class MaintenanceConnectionTest implements IMaintenanceMessageHandler {
//
//    private static int SERVER_TIMEOUT = 500;
//
//    private static int CLIENT_TIMEOUT = 500;
//
//    private static int CLIENT_CHECK_CONNECTION_INTERVAL = 450;
//    private final String serverAddress = "127.0.0.1";
//    private final int serverPort = 55655;
//    private MaintenanceServer server;
//    private MaintenanceClient client;
//
//    @Before
//    public void setUp() throws IOException {
//        System.out.println("\n\n...............");
//        this.server = new MaintenanceServer(this.serverPort, this, SERVER_TIMEOUT);
//        this.client = new MaintenanceClient(this.serverAddress, this.serverPort, CLIENT_TIMEOUT,
//                CLIENT_CHECK_CONNECTION_INTERVAL);
//    }
//
//    @After
//    public void tearDown() {
//        System.out.println("Shutting down.");
//        this.server.shutdown();
//        this.client.shutdown();
//    }
//
//    @Test
//    public void testSendMessage() throws IOException, InterruptedException {
//        System.out.println("\n=== Send Message ===");
//        for (int i = 0; i < 50; i++) {
//            final CheckConnectionRequest req = new CheckConnectionRequest();
//            System.out.println(i + " - Send request");
//            final MaintenanceResponse res = this.client.sendRequest(req);
//            System.out.println(i + " - Response received.");
//            Assert.assertTrue(res instanceof CheckConnectionResponse);
//            Assert.assertEquals(req.getConversationId(), res.getConversationId());
//        }
//    }
//
//    @Test(expected = IOException.class)
//    public void testClientBrokenConnection() throws IOException {
//        System.out.println("\n=== Broken Connection ===");
//
//        final MaintenanceRequest req = new CheckConnectionRequest();
//        final MaintenanceResponse res = this.client.sendRequest(req);
//        Assert.assertEquals(CheckConnectionResponse.class, res.getClass());
//        Assert.assertEquals(req.getConversationId(), res.getConversationId());
//
//        this.server.shutdown();
//        this.client.sendRequest(new CheckConnectionRequest());
//    }
//
//    @Test
//    public void testTimeout1() throws InterruptedException, IOException {
//        System.out.println("\n=== Timeout 1 ===");
//        this.client.disableConnectionCheck();
//        Thread.sleep(this.server.timeout - 50);
//        Assert.assertEquals(CheckConnectionResponse.class,
//                this.client.sendRequest(new CheckConnectionRequest()).getClass());
//    }
//
//    @Test(expected = IOException.class)
//    public void testTimeout2() throws IOException, InterruptedException {
//        System.out.println("\n=== Timeout 2 ===");
//        this.client.disableConnectionCheck();
//        Thread.sleep(this.server.timeout + 50);
//        this.client.sendRequest(new CheckConnectionRequest());
//    }
//
//    @Test
//    public void testMultipleConnections() throws IOException {
//        System.out.println("\n=== Multiple Connections ===");
//        final MaintenanceClient client2 = new MaintenanceClient(this.serverAddress, this.serverPort, CLIENT_TIMEOUT,
//                CLIENT_CHECK_CONNECTION_INTERVAL);
//        final Thread t1 = new Thread(() -> {
//            try {
//                final MaintenanceRequest req = new CheckConnectionRequest();
//                final MaintenanceResponse res = this.client.sendRequest(req);
//                Assert.assertEquals(CheckConnectionResponse.class, res.getClass());
//                Assert.assertEquals(req.getConversationId(), res.getConversationId());
//            } catch (final Exception e) {
//                e.printStackTrace();
//            }
//        });
//        t1.start();
//
//        final MaintenanceRequest req = new CheckConnectionRequest();
//        final MaintenanceResponse res = client2.sendRequest(req);
//        Assert.assertEquals(CheckConnectionResponse.class, res.getClass());
//        Assert.assertEquals(req.getConversationId(), res.getConversationId());
//
//        client2.shutdown();
//    }
//
//    @Test
//    public void testCheckConnection() throws InterruptedException, IOException {
//        System.out.println("\n=== Check Connection ===");
//        Thread.sleep(3 * SERVER_TIMEOUT + 5);
//
//        final MaintenanceRequest req = new CheckConnectionRequest();
//        final MaintenanceResponse res = this.client.sendRequest(req);
//        Assert.assertEquals(CheckConnectionResponse.class, res.getClass());
//        Assert.assertEquals(req.getConversationId(), res.getConversationId());
//    }
//
//    /*
//     * (non-Javadoc)
//     *
//     * @see
//     * IMaintenanceMessageHandler#
//     * handleGetLog(GetLogRequest)
//     */
//    @Override
//    public GetLogResponse handleGetLog(GetLogRequest request) {
//        // TODO Auto-generated method stub
//        return null;
//    }
//
//    /*
//     * (non-Javadoc)
//     *
//     * @see
//     * IMaintenanceMessageHandler#
//     * handleGetStatus(org.kabieror.elwasys.common.maintenance.
//     * GetStatusRequest)
//     */
//    @Override
//    public GetStatusResponse handleGetStatus(GetStatusRequest request) {
//        // TODO Auto-generated method stub
//        return null;
//    }
//
//    /*
//     * (non-Javadoc)
//     *
//     * @see
//     * IMaintenanceMessageHandler#
//     * handleRestartApp(org.kabieror.elwasys.common.maintenance.
//     * RestartAppRequest)
//     */
//    @Override
//    public void handleRestartApp(RestartAppRequest request) {
//        // TODO Auto-generated method stub
//
//    }
//}
