/**
 *
 */
package org.kabieror.elwasys.common.maintenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Dieser Client verbindet sich mit einem MaintenanceServer und nimmt von diesem Befehle entgegen.
 *
 * @author Oliver Kabierschke
 */
public class MaintenanceClient {
    /**
     * Das Intervall, in dem Nachrichten an den Server geschickt werden sollen,
     * damit die Verbindung aufrecht erhalten bleibt.
     */
    public final int checkConnectionInterval;
    public final int timeout;
    /**
     * Eine Verknüpfung von Nachrichten-IDs zur jeweiligen Warteschlange, in welche Antworten gespeichert werden.
     */
    private final Map<Long, BlockingQueue<MaintenanceResponse>> incomingMessage = new HashMap<>();
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Socket socket;
    private final Thread checkConnectionThread;
    private final Thread listenerThread;
    private final IMaintenanceMessageHandler messageHandler;
    private long currentConversationId = (long) (Math.random() * Long.MAX_VALUE);
    private boolean shutdown = false;

    public MaintenanceClient(String serverAddress, int port, int timeout, int checkConnectionInterval,
                             String locationName, IMaintenanceMessageHandler messageHandler) throws IOException {
        this.logger.debug("Starting maintenance connection to " + serverAddress + ":" + port + ".");
        this.messageHandler = messageHandler;
        this.timeout = timeout;
        this.checkConnectionInterval = checkConnectionInterval;
        this.socket = new Socket(serverAddress, port);
        this.socket.setSoTimeout(timeout);

        {
            ConnectionRequest req = new ConnectionRequest(locationName);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(req);
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            Object responseObject;
            try {
                responseObject = in.readObject();
            } catch (ClassNotFoundException e) {
                throw new IOException("Could not parse answer from server.");
            }
            if (!(responseObject instanceof ConnectionResponse)) {
                throw new IOException(String.format("Server sent answer of type %1s instead of %2s.",
                        responseObject.getClass().getName(), ConnectionResponse.class.getName()));
            }
        }

        this.checkConnectionThread = new Thread(() -> {
            while (!this.shutdown) {
                try {
                    Thread.sleep(this.checkConnectionInterval);
                    if (!this.checkConnection()) {
                        this.shutdown = true;
                        try {
                            this.socket.close();
                        } catch (final Exception ignored) {
                        }
                        break;
                    }
                } catch (final InterruptedException e) {
                    // Interrupted.
                }
            }
            this.logger.debug("Check connection thread terminating.");
        });
        this.checkConnectionThread.setName("MaintenanceCheckConnectionThread");
        if (this.checkConnectionInterval > 0) {
            this.checkConnectionThread.start();
        } else {
            this.logger.warn("Connection checks are disabled.");
        }

        this.listenerThread = new Thread(() -> {

            final String remoteIdentifier = this.socket.getInetAddress().getHostAddress();

            this.logger.info("Maintenance connection to " + remoteIdentifier + " starting.");

            try {
                // Receiver loop
                while (!Thread.interrupted()) {
                    try {
                        ObjectInputStream in = new ObjectInputStream(this.socket.getInputStream());
                        final Object o = in.readObject();

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
                        this.logger.warn("Could not process the incoming message from " + remoteIdentifier + ".");
                    }
                }
            } catch (final IOException e) {
                if (!this.shutdown) {
                    this.logger.error("Connection to " + remoteIdentifier + " is broken.", e);
                }
            } finally {
                try {
                    this.socket.close();
                } catch (final IOException e) {
                    // Do nothing.
                }
            }

            // Thread ending.
            if (this.checkConnectionThread.isAlive()) {
                this.checkConnectionThread.interrupt();
                try {
                    this.checkConnectionThread.join();
                } catch (final InterruptedException e) {
                    this.logger.warn("Could not wait for the CheckConnectionThread to end.");
                }
            }

            this.logger.info("Maintenance connection to " + remoteIdentifier + " closed.");
        });
        this.listenerThread.setName("MaintenanceListenerThread");
        this.listenerThread.start();
    }

    public boolean isAlive() {
        return this.listenerThread.isAlive();
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

        this.logger
                .trace("Incoming " + o.getClass().getSimpleName() + "[" + ((MaintenanceMessage) o).getConversationId() +
                        "] from " + this.socket.getInetAddress().getHostAddress() + ".");

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

        if (o instanceof GetLogRequest) {
            return this.messageHandler.handleGetLog((GetLogRequest) o);
        } else if (o instanceof RestartAppRequest) {
            this.messageHandler.handleRestartApp((RestartAppRequest) o);
            return null;
        } else if (o instanceof GetStatusRequest) {
            return this.messageHandler.handleGetStatus((GetStatusRequest) o);
        }

        return new ErrorMessage((MaintenanceMessage) o, "Unknown message type.");
    }

    /**
     * Beendet das periodische Prüfen der Verbindung.
     */
    void disableConnectionCheck() {
        if (this.checkConnectionThread.isAlive()) {
            this.shutdown = true;
            this.checkConnectionThread.interrupt();
            try {
                this.checkConnectionThread.join();
            } catch (final InterruptedException e) {
                this.logger.error("Interrupted while waiting for the check connection thread to terminate.");
            }
            this.shutdown = false;
        }
    }

    public void shutdown() {
        this.logger.info("Shutting down connection with " + this.socket.getInetAddress().getHostAddress());
        if (this.shutdown) {
            this.logger.warn("The connection to the client is already closed.");
            return;
        }
        this.shutdown = true;
        this.checkConnectionThread.interrupt();
        try {
            this.socket.close();
        } catch (final IOException e) {
            this.logger.warn("Could not close the socket.", e);
        }
    }

    private MaintenanceResponse sendRequest(MaintenanceRequest request) throws IOException {
        this.logger.trace("Sending " + request.getClass().getSimpleName() + "[" + this.currentConversationId + "].");

        if (this.socket.isClosed()) {
            this.logger.warn("The connection to the maintenance server is broken.");
            throw new IOException("The connection to the maintenance server is broken.");
        }
        request.setConversationId(this.currentConversationId++);

        BlockingQueue<MaintenanceResponse> queue = new LinkedBlockingQueue<>();
        this.incomingMessage.put(request.getConversationId(), queue);

        synchronized (this.socket) {
            final ObjectOutputStream out = new ObjectOutputStream(this.socket.getOutputStream());
            out.writeObject(request);
        }

        MaintenanceResponse response;
        try {
            response = queue.poll(this.timeout, TimeUnit.MILLISECONDS);
        } catch (final ClassCastException e) {
            throw new IOException("Received invalid reponse from server.");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while waiting for a response from the server.", e);
        }
        return response;
    }

    public boolean checkConnection() {
        if (this.shutdown || !this.socket.isBound() || this.socket.isClosed()) {
            return false;
        }

        final MaintenanceRequest req = new CheckConnectionRequest();
        MaintenanceResponse res;
        try {
            res = this.sendRequest(req);
        } catch (final IOException e) {
            // Connection is broken.
            this.logger.warn("The connection to the server is broken.", e);
            return false;
        }
        if (!(res instanceof CheckConnectionResponse) || res.getConversationId() != req.getConversationId()) {
            // Connection is broken.
            this.logger.warn("The connection to the server is broken. " + "No suitable response was received.");
            return false;
        }
        return true;
    }
}
