/**
 *
 */
package org.kabieror.elwasys.common.maintenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Dieser Server akzeptiert die Verbindung von Maintenance-Clients um ihnen Befehler zu senden und ihren Status
 * abzufragen.
 *
 * @author Oliver Kabierschke
 */
public class MaintenanceServer {

    /**
     * Die Zeit, nach der eine Verbindung als nicht mehr aktiv angenommen und
     * beendet wird.
     */
    final int timeout;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final ServerSocket socket;

    private final Thread serverThread;

    private final Map<String, ClientConnectionThread> clientConnections;

    private boolean shutdown = false;

    public MaintenanceServer(int port, int timeout) throws IOException {
        this.logger.info("MaintenanceServer starting to listen on port " + port + ".");
        this.timeout = timeout;
        this.socket = new ServerSocket(port);
        this.serverThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    final Socket s = this.socket.accept();
                    this.handleConnection(s);
                } catch (final IOException e) {
                    if (this.shutdown) {
                        // Shutting down server.
                        break;
                    }
                    this.logger.error("Could not accept a connection.", e);
                }
            }
        });
        this.serverThread.setName("MaintenanceServerThread");
        this.clientConnections = new HashMap<>();
        this.serverThread.start();
    }

    /**
     * Gibt eine Liste aller Namen von Standorten zurück, zu denen eine Verbindung besteht.
     *
     * @return Eine Liste aller Namen von Standorten, zu denen eine Verbindung besteht.
     */
    public Set<String> getClientConnections() {
        return this.clientConnections.keySet();
    }

    /**
     * Gibt die Verbindung zum Client zurück, der den gesuchten Ort implementiert.
     *
     * @param location Der Standort, zu dem eine Verbindung gesucht wird.
     * @return Die gesuchte Verbindung.
     */
    public IClientConnection getClientConnection(String location) {
        ClientConnectionThread cct =
                this.clientConnections.containsKey(location) ? this.clientConnections.get(location) : null;
        if (cct != null && cct.isAlive()) {
            return cct;
        }
        return null;
    }

    /**
     * Behandelt eine eingehende Wartungs-Verbindung.
     *
     * @param socket Der eingehende Socket.
     */
    private void handleConnection(Socket socket) {
        this.logger.info("Incoming maintenance connection from " + socket.getInetAddress().getHostAddress());
        try {
            new ClientConnectionThread(socket);
        } catch (IOException e) {
            this.logger.error("Could not accept connection from " + socket.getInetAddress().getHostAddress(), e);
        }
    }

    /**
     * Schließt alle Wartungs-Verbindungen und beendet den Server.
     */
    public void shutdown() {
        this.logger.info("Shutting down MaintenanceServer.");
        this.shutdown = true;
        for (final ClientConnectionThread t : this.clientConnections.values()) {
            t.shutdown();
        }
        if (this.serverThread != null && this.serverThread.isAlive()) {
            try {
                this.socket.close();
                try {
                    this.serverThread.join();
                } catch (final InterruptedException e) {
                    this.logger.error("Error while waiting for the server thread to end.", e);
                }
            } catch (final IOException e1) {
                this.logger.error("Could not close the server socket.", e1);
            }
        }
    }

    /**
     * Prüft, ob der Server funktionsfähig ist.
     *
     * @return True, wenn der Server auf Verbindungen wartet.
     */
    public boolean isAlive() {
        return this.socket != null && this.socket.isBound() && !this.socket.isClosed();
    }

    /**
     * Dieser Thread wartet auf eingehende Objekte vom MaintenanceClient.
     *
     * @author Oliver Kabierschke
     */
    private class ClientConnectionThread extends Thread implements IClientConnection {
        private final Logger logger = LoggerFactory.getLogger(this.getClass());

        private final Socket socket;

        private final Thread timeoutThread;

        private final String locationName;

        /**
         * Eine Verknüpfung von Nachrichten-IDs zur jeweiligen Warteschlange, in welche Antworten gespeichert werden.
         */
        private final Map<Long, BlockingQueue<MaintenanceResponse>> incomingMessage = new HashMap<>();

        private long currentConversationId = (long) (Math.random() * Long.MAX_VALUE);
        /**
         * Gibt an, ob der Thread gerade beendet wird.
         */
        private boolean shutdown = false;

        /**
         * Gibt an, ob der Thread gerade durch ein Timeout beendet wird.
         */
        private boolean timeout = false;

        ClientConnectionThread(Socket socket) throws IOException {
            super();

            // Warte für Verbindungs-Anfrage
            socket.setSoTimeout(MaintenanceServer.this.timeout);
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            Object requestObject;
            try {
                requestObject = in.readObject();
            } catch (ClassNotFoundException e) {
                socket.close();
                throw new IOException("Received an invalid request.", e);
            }

            if (!(requestObject instanceof ConnectionRequest)) {
                socket.close();
                throw new IOException(String.format("Received invalid connection request of type '%1s'.",
                        requestObject.getClass().getName()));
            }
            ConnectionRequest connectionRequest = (ConnectionRequest) requestObject;

            // Prüfe, ob nicht bereits eine Verbindung zu einem Client besteht, der denselben Standort besetzt.
            if (MaintenanceServer.this.clientConnections.containsKey(connectionRequest.getLocation()) &&
                    MaintenanceServer.this.clientConnections.get(connectionRequest.getLocation()).isAlive()) {
                this.logger.info(String
                        .format("'%1s' replaces '%2s' on location '%3s'.", socket.getInetAddress().getHostAddress(),
                                MaintenanceServer.this.clientConnections.get(connectionRequest.getLocation())
                                        .getSocket().getInetAddress().getHostAddress(),
                                connectionRequest.getLocation()));
                MaintenanceServer.this.clientConnections.get(connectionRequest.getLocation()).shutdown();
            }

            // Bestätige Verbindung
            out.writeObject(new ConnectionResponse(connectionRequest));

            // Verbindung erfolgreich aufgebaut
            this.locationName = connectionRequest.getLocation();
            MaintenanceServer.this.clientConnections.put(connectionRequest.getLocation(), this);
            this.socket = socket;
            this.setName("ClientConnectionThread with " + socket.getInetAddress().getHostAddress());
            this.start();

            // Starte Timeout-Thread
            this.timeoutThread = new Thread(() -> {
                while (!ClientConnectionThread.this.shutdown) {
                    try {
                        Thread.sleep(MaintenanceServer.this.timeout);
                    } catch (final Exception e) {
                        // Timeout neu starten.
                        continue;
                    }

                    // Timeout ist eingetregen. Beende Verbindung.
                    this.shutdown = true;
                    this.timeout = true;
                    try {
                        this.socket.close();
                    } catch (final Exception e) {
                        MaintenanceServer.this.logger.warn("Could not close the connection to " +
                                this.socket.getInetAddress().getHostAddress());
                    }
                    break;
                }
                MaintenanceServer.this.logger.debug("Timeout thread ended.");
            });
            this.timeoutThread.setName("TimeoutThread " + this.socket.getInetAddress().getHostAddress());
            if (MaintenanceServer.this.timeout > 0) {
                this.timeoutThread.start();
            }
        }

        /**
         * Beendet diese Verbindung zum Client.
         */
        public void shutdown() {
            if (this.shutdown) {
                // Verbindung wurde bereits heruntergefahren
                return;
            }
            MaintenanceServer.this.logger
                    .debug("Shutting down connection with " + this.socket.getInetAddress().getHostAddress() + ".");
            this.shutdown = true;

            // Terminate timeout thread
            if (this.timeoutThread.isAlive()) {
                this.timeoutThread.interrupt();
                try {
                    this.timeoutThread.join();
                } catch (final InterruptedException e) {
                    MaintenanceServer.this.logger.warn("Could not wait for TimeoutThread to terminate.");
                }
            }

            // Terminate connection thread
            try {
                this.socket.close();
                try {
                    this.join();
                } catch (final InterruptedException e) {
                    MaintenanceServer.this.logger.warn("Could not wait for ClientConnectionThread to terminate.");
                }
            } catch (final IOException e1) {
                MaintenanceServer.this.logger.warn("Could not close the client connection.");
            }
        }

        /*
         * Sendet Befehler zum Client und empfängt Antworten von ihm.
         *
         * (non-Javadoc)
         *
         * @see java.lang.Thread#run()
         */
        @Override
        public void run() {

            final String remoteIdentifier = this.socket.getInetAddress().getHostAddress();

            MaintenanceServer.this.logger.info("Maintenance connection to " + remoteIdentifier + " starting.");

            try {
                // Receiver loop
                while (!Thread.interrupted()) {
                    try {
                        ObjectInputStream in = new ObjectInputStream(this.socket.getInputStream());
                        final Object o = in.readObject();

                        // Nachricht empfangen. Timeout zurücksetzen.
                        this.timeoutThread.interrupt();

                        // Nachricht abarbeiten.
                        final MaintenanceMessage response = this.handleIncomingObject(o);

                        if (response == null) {
                            // Keine Antwort zu senden.
                            continue;
                        }

                        synchronized (this.socket) {
                            ObjectOutputStream out = new ObjectOutputStream(this.socket.getOutputStream());
                            out.writeObject(response);
                        }
                    } catch (final ClassNotFoundException e) {
                        MaintenanceServer.this.logger
                                .warn("Could not process the incoming message from " + remoteIdentifier + ".");
                    }
                }
            } catch (final IOException e) {
                if (this.timeout) {
                    MaintenanceServer.this.logger.warn("Connection to " + remoteIdentifier +
                            " timed out due to a missing heartbeat message.", e);
                } else if (!this.shutdown) {
                    MaintenanceServer.this.logger.error("Connection to " + remoteIdentifier + " is broken.", e);
                }
            } finally {
                try {
                    this.socket.close();
                } catch (final IOException e) {
                    // Do nothing.
                }
            }

            // Thread ending.
            this.shutdown = true;
            if (this.timeoutThread.isAlive()) {
                this.timeoutThread.interrupt();
                try {
                    this.timeoutThread.join();
                } catch (final InterruptedException e) {
                    MaintenanceServer.this.logger.warn("Could not wait for the TimeoutThread to end.");
                }
            }

            MaintenanceServer.this.clientConnections.remove(this.locationName);

            MaintenanceServer.this.logger.info("Maintenance connection to " + remoteIdentifier + " closed.");
        }


        /**
         * Nimmt ein eingehendes Objekt entgegen und generiert eine Antwort dafür.
         *
         * @param o Die eingegangene Anfrage.
         * @return Die Antwort auf die Anfrage.
         */
        private MaintenanceMessage handleIncomingObject(Object o) {
            if (!(o instanceof MaintenanceMessage)) {
                this.logger.debug("Received unknown object of type " + o.getClass().getName());
                return new ErrorMessage("Invalid Message.");
            }

            this.logger.trace("Incoming " + o.getClass().getSimpleName() + "[" +
                    ((MaintenanceMessage) o).getConversationId() + "] from " +
                    this.socket.getInetAddress().getHostAddress() + ".");

            if (o instanceof CheckConnectionRequest) {
                return new CheckConnectionResponse((CheckConnectionRequest) o);
            }

            if (o instanceof MaintenanceResponse) {
                MaintenanceResponse resp = (MaintenanceResponse) o;
                if (this.incomingMessage.containsKey(resp.getConversationId())) {
                    try {
                        this.incomingMessage.get(resp.getConversationId()).put(resp);
                    } catch (InterruptedException e) {
                        this.logger.error("Interrupted Exception", e);
                    }
                } else {
                    return new ErrorMessage((MaintenanceMessage) o, "Did not wait for this message.");
                }
            }

            return new ErrorMessage((MaintenanceMessage) o, "Unknown message type.");
        }

        /**
         * Sendet eine Nachricht an den Client und wartet auf eine Antwort.
         *
         * @param request Die Anfrage an den Client
         * @return Die Antwort des Clients.
         */
        @Override
        public MaintenanceResponse sendQuery(MaintenanceRequest request) throws IOException {
            if (this.socket.isClosed()) {
                this.logger.warn("The connection to the maintenance server is broken.");
                throw new IOException("The connection to the maintenance server is broken.");
            }
            request.setConversationId(this.currentConversationId++);

            BlockingQueue<MaintenanceResponse> queue = new LinkedBlockingQueue<>();
            this.incomingMessage.put(request.getConversationId(), queue);

            synchronized (this.socket) {
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.writeObject(request);
            }

            MaintenanceResponse resp = null;
            try {
                resp = queue.poll(MaintenanceServer.this.timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                this.logger.error("Interrupted while waiting for a response of the client.", e);
            }

            this.incomingMessage.remove(request.getConversationId());
            return resp;
        }

        @Override
        public void sendCommand(MaintenanceRequest request) throws IOException {
            if (this.socket.isClosed()) {
                this.logger.warn("The connection to the maintenance server is broken.");
                throw new IOException("The connection to the maintenance server is broken.");
            }
            request.setConversationId(this.currentConversationId++);

            BlockingQueue<MaintenanceResponse> queue = new LinkedBlockingQueue<>();
            this.incomingMessage.put(request.getConversationId(), queue);

            synchronized (this.socket) {
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.writeObject(request);
            }
        }

        @Override
        public String getHostAddress() {
            return socket.getInetAddress().getHostAddress();
        }

        private Socket getSocket() {
            return socket;
        }
    }
}
