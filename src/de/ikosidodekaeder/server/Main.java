package de.ikosidodekaeder.server;


import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
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

    /**
     * Depending of the content of payload, specificly the TypeId part the returned Map
     * contains different sets of keys.
     *
     * Following Keys are always contained in the Map:
     *
     * "Packet" -> Contains the orignal payload
     * "TypeId" -> PacketType
     * "SourceId" -> UUID
     * "isCancelled" -> boolean
     * -----------------------------------|
     * The next only for certain TypeId's |
     * TypeId == Register                 |
     *                                    |
     * "Room" -> String                   |
     * -----------------------------------|
     * TypeId == JOIN                     |
     *                                    |
     * "DestinationId" -> UUID            |
     * "Username" -> String               |
     * "Version" -> String                |
     *------------------------------------|
     * TypeId == KEEPALIVE                |
     *                                    |
     * "SessionId" -> Integer             |
     *------------------------------------|
     * @param payload
     * @return
     */
    public static Map<String,Object> inspectPayloadOf(String payload){
        String[] items = payload.split(";");
        Map<String,Object> tmp =new Hashtable<String,Object>(){{
            put("Packet",payload);
            put("TypeId",PacketType.valueOf(Byte.parseByte(items[0])));
            put("SourceId",UUID.fromString(items[1]));
            put("isCancelled",(Boolean)items[2].equals("1"));
        }};

        switch ((PacketType)tmp.get("TypeId")){
            case KEEPALIVE:
                tmp.put("SessionId",(Integer.parseInt(items[3])));
            case REGISTER:
                tmp.put("Room",(String)items[3]);
                break;
            case JOIN:
                tmp.put("DestinationId",UUID.fromString(items[3]));
                tmp.put("Username",items[4]);
                tmp.put("Version",items[5]);
                break;
        }
        return tmp;
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
        String ROOM = (String) PacketContent.get("Room");
        PacketType Type = ((PacketType)PacketContent.get("TypeId"));

        if (containsHost(senderId)) {
            System.out.println("Host already connected");
            return;
        }
        hosts.add(new Connection(senderId, socket));
        System.out.println("New Host registered: " + senderId.toString());
        System.out.println("======= " + Type.name() + ", " + senderId.toString() + ", " + ROOM);
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
                UUID clientId =((UUID)PacketContent.get("DestinationId"));
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

            UUID hostId = ((UUID)PacketContent.get("DestinationId"));
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
        Map<String,Object> PacketContent = inspectPayloadOf(packet);
        PacketType type = ((PacketType)PacketContent.get("TypeId"));

        /*
            try to create a connection anyway, if it succeds fine if not the appropiate end
            will know what to do
         */
        Connection connection = getHost((UUID)PacketContent.get("SourceId"));

        if( type == null)
           return;
        switch (type){
            case REGISTER:
                onRegister(PacketContent,socket);;
                break;
            case KEEPALIVE:
            {
                boolean fromHost = connection != null;
                if (fromHost) {
                    connection.setLastPacket(System.currentTimeMillis());
                }
                onKeepAlive(connection,packet);
                break;
            }
            case JOIN:
            {
                onJoin(connection,socket,PacketContent);
                break;
            }
            default:
                return;
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
