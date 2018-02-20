package de.ikosidodekaeder.server;


import com.sun.security.ntlm.Client;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
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

    public static Map<String,Object> inspectPayload(String payload){
        String[] items = payload.split(";");
        return new Hashtable<String,Object>(){{
            put("Packet",payload);
           put("TypeID",PacketType.valueOf(Byte.parseByte(items[0])));
           put("SourceId",UUID.fromString(items[1]));
           put("isCancelled",(Boolean)items[2].equals("1"));
           put("DestinationId",(String)items[3]);

        }};
    }

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
        while (true) {
            Socket socket = null;
            System.out.println("Now accepting");
            try {
                socket = serverSocket.accept();
                Thread thread = new Thread(new ListenThread(this, socket));
                thread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        /*ActiveServer.start();
        try {
            ActiveServer.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/
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

    public ClientConnection getClient(UUID uuid) {
        for (ClientConnection connection : clientHost.keySet()) {
            if (connection.getUuid().equals(uuid)) {
                return connection;
            }
        }
        return null;
    }

    public boolean containsClient(UUID uuid) {
        return getClient(uuid) != null;
    }

    void onRegister(Map<String,Object> PacketContent,Socket socket){
        UUID senderId = (UUID) PacketContent.get("SourceId");
        UUID hostId = (UUID) PacketContent.get("DestinationId");
        PacketType Type = ((PacketType)PacketContent.get("TypeId"));

        if (containsHost(senderId)) {
            System.out.println("Host already connected");
            return;
        }
        hosts.add(new Connection(senderId, socket));
        System.out.println("New Host registered: " + senderId.toString());
        System.out.println("======= " + Type.name() + ", " + senderId.toString() + ", " + hostId);
    }

    void onKeepAlive(Connection connection, String packet){
        if (connection != null) {
            // If this packet was sent from the host
            // send keep alive to all clients
            broadcastToClients(connection, packet);
        } else {
            // If this packet was sent from a client
            // send to host
        }
    }

    void onJoin(Connection connection,Socket socket,Map<String,Object> PacketContent){
        if (connection !=  null) {
            System.out.println("Join From Host");
            // If this packet was sent from the host
            // send join to all clients
            broadcastToClients(connection,(String) PacketContent.get("Packet"));


            if ((Boolean) PacketContent.get("isCancelled")) {
                // Remove the socket if the host did not accept
                UUID clientId =UUID.fromString ((String)PacketContent.get("DestinationId"));
                ClientConnection client = getClient(clientId);
                try {
                    client.getSocket().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                clientHost.remove(client);
            }
        } else {
            // If this packet was sent from a client
            // Store the socket
            if (containsClient(((UUID)PacketContent.get("SourceId")))) {
                System.out.println(((UUID)PacketContent.get("SourceId")) + " already connected");
                return;
            }

            UUID hostId = UUID.fromString((String)PacketContent.get("DestinationId"));
            UUID senderId = ((UUID)PacketContent.get("SourceId"));

            // Send it to the host
            Connection host = getHost(hostId);
            if (host != null) {
                ClientConnection clientConnection = new ClientConnection(senderId, socket);
                clientHost.put(clientConnection, senderId);
                System.out.println("Registered new client: " + senderId);

                sendToHost(host, ((String)PacketContent.get("Packet")));
                System.out.println("Sent to host");
            } else {
                System.out.println("Error, host null");
            }
        }
    }

    public void handlePacket(String packet, Socket socket) {

        Map<String,Object> PacketContent = inspectPayload(packet);

       /* String[] arr = packet.split(";");
        byte typeId = Byte.parseByte(arr[0]);
        PacketType packetType = PacketType.valueOf(typeId);
        if (packetType == null) {
            System.out.println("======= PacketType null -> " + typeId);
            return;
        }

        */

        Connection connection = getHost((UUID)PacketContent.get("SourceId"));
        boolean fromHost = connection != null;
        if (fromHost) {
            connection.setLastPacket(System.currentTimeMillis());
        }

       if(PacketContent.get("TypeId") == null)
           return;
        switch ((PacketType)PacketContent.get("TypeId")){
            case REGISTER:
                onRegister(PacketContent,socket);;
                break;
            case KEEPALIVE:
                onKeepAlive(connection,packet);
                break;
            case JOIN:
                onJoin(connection,socket,PacketContent);
                break;
            default:
                return;
        }
        /*
                UUID senderId = UUID.fromString(arr[1]);
                System.out.println("Sender id: " + senderId + " /// " + packetType.name());

                boolean cancelled = arr[2].equals("1");
        */

        /*
        if (PacketContent.get("TypeId") == PacketType.REGISTER) {
            onRegister(PacketContent,socket);;
        }

        Connection connection = getHost((UUID)PacketContent.get("SourceId"));
        boolean fromHost = connection != null;
        if (fromHost) {
            connection.setLastPacket(System.currentTimeMillis());
        }
        if (PacketContent.get("TypeId") == PacketType.KEEPALIVE) {
            onKeepAlive(connection,packet);
        } else if (PacketContent.get("TypeId") == PacketType.JOIN) {
            onJoin(connection,socket,PacketContent);
        }*/



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
        try {
            PrintWriter out = new PrintWriter(host.getSocket().getOutputStream(), true);
            out.println(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<Connection> toRemove = new ArrayList<>();

    /**
     * Removes inactive hosts
     */
    public void removeInactive() {
        // TODO: Nur für Testingzwecke auskommentiert
        /*for (Connection connection : hosts) {
            if (System.currentTimeMillis() - connection.getLastPacket() >= TIME_OUT) {
                toRemove.add(connection);
                System.out.println("Removed " + connection.getUuid());
            }
        }

        if (!hosts.isEmpty()) {
            hosts.removeAll(toRemove);
            toRemove.clear();
        }*/
    }

}
