package de.ikosidodekaeder.server;


import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

/**
 * Created by Sven on 19.02.2018.
 */

public class Main {

    private static final int TIME_OUT = 30_000;

    private ServerSocket serverSocket;

    private boolean running = true;

    private List<Connection>                hosts = new ArrayList<>();
    private Map<ClientConnection, UUID>     clientHost = new HashMap<>();


    public Main() {
        try {
            System.out.println("Trying...");
            serverSocket = new ServerSocket(25565);
            System.out.println("Done");
        } catch (IOException e) {
            e.printStackTrace();
        }

        start();
    }

    public static void main(String[] args) {
        new Main();
    }

    public void start() {
        Thread ActiveServer = new Thread(() -> {
            while (true) {
                Socket socket = null;

                removeInactive();

                try {
                    System.out.println("Now accepting");
                    socket = serverSocket.accept();
                    //ObjectInputStream in = new ObjectInputStream(client.getInputStream());
                    Scanner in  = new Scanner(socket.getInputStream());
                    System.out.println("Received!");
                    //BufferedInputStream in = new BufferedInputStream(client.getInputStream());
                    System.out.println("Reading...");

                    while (in.hasNext()) {
                        String packet = in.nextLine();
                        System.out.println("Received packet " + packet);
                        handlePacket(packet, socket);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (socket != null)
                        try { socket.close(); } catch (IOException ignored) { }
                }
            }
        });
        ActiveServer.start();
        try {
            ActiveServer.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public Connection getHost(UUID uuid) {
        for (Connection connection : hosts) {
            if (connection.getUuid().equals(uuid)) {
                return connection;
            }
        }
        return null;
    }

    public boolean containsHost(UUID uuid) {
        return getHost(uuid) != null;
    }

    public void handlePacket(String packet, Socket socket) {
        String[] arr = packet.split(";");
        byte typeId = Byte.parseByte(arr[0]);
        PacketType packetType = PacketType.valueOf(typeId);
        if (packetType == null) {
            System.out.println("======= PacketType null -> " + typeId);
            return;
        }

        UUID clientId = UUID.fromString(arr[1]);

        if (packetType == PacketType.REGISTER) {
            if (containsHost(clientId)) {
                System.out.println("Host already connected");
                return;
            }
            hosts.add(new Connection(clientId, socket));
            System.out.println("======= " + packetType.name() + ", " + clientId.toString() + ", " + arr[3]);
            return;
        }
        Connection connection = getHost(clientId);
        if (connection != null) {
            connection.setLastPacket(System.currentTimeMillis());
        }
        if (packetType == PacketType.KEEPALIVE) {
            if (connection != null) {
                // If this packet was sent from the host
                // send keep alive to all clients
                broadcastToClients(connection, packet);
            } else {
                // If this packet was sent from a client
                // send to host
            }
        } else if (packetType == PacketType.JOIN) {
            if (connection != null) {
                // If this packet was sent from the host
                // send join to all clients
                // and add to clientHosts map
            } else {
                // If this packet was sent from a client
                // Send it to the host
            }
        }



    }

    public void broadcastToClients(Connection host, String packet) {
        for (ClientConnection client : clientHost.keySet()) {
            UUID hostUuid = clientHost.get(client);
            if (hostUuid.equals(host.getUuid())) {
                continue;
            }

            try {
                PrintWriter out = new PrintWriter(client.getSocket().getOutputStream(), true);
                out.println(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendToHost(Connection host, String packet) {
        // print writer
        // out -> send packet
    }

    private List<Connection> toRemove = new ArrayList<>();

    /**
     * Removes inactive hosts
     */
    public void removeInactive() {
        for (Connection connection : hosts) {
            if (System.currentTimeMillis() - connection.getLastPacket() >= TIME_OUT) {
                toRemove.add(connection);
                System.out.println("Removed " + connection.getUuid());
            }
        }

        if (!hosts.isEmpty()) {
            hosts.removeAll(toRemove);
            toRemove.clear();
        }
    }

}
