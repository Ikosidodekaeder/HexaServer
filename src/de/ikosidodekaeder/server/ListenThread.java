package de.ikosidodekaeder.server;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Scanner;
import java.util.UUID;

/**
 * Created by Sven on 20.02.2018.
 */

public class ListenThread implements Runnable {

    private Main owner;
    private Socket socket;
    private BufferedReader reader;
    private boolean running = true;

    private UUID uuid;

    public ListenThread(Main owner, Socket socket) {
        this.owner = owner;
        this.socket = socket;

    }

    @Override
    public void run() {
        System.out.println("Started new thread");
        Scanner in  = null;
        try {
            //filter = new BufferedInputStream(socket.getInputStream());
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            //in = new Scanner(socket.getInputStream());
            System.out.println("Buffer Size: " + socket.getReceiveBufferSize());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        while (running) {

            try {
                StringBuilder receivedString = new StringBuilder();
                int received = 0;
                int waiting = 0;

                socket.setSoTimeout(Main.TIME_OUT);
                /*while (in.hasNext()) {
                    received++;
                    socket.setSoTimeout(Main.TIME_OUT);
                    String packet = in.nextLine();
                    System.out.println("Received packet " + packet);
                    owner.handlePacket(packet, socket);
                }*/
                /*while (in.hasNextByte()) {
                    received++;
                    byte b = in.nextByte();
                    System.out.println("Received byte " + b);
                    receivedString.append(b);
                }*/
                while (waiting <= Main.TIME_OUT) {
                    if (reader.ready()) {
                        waiting = 0;
                        received++;
                        char c = ((char) reader.read());
                        if (c == '\n') {
                            break;
                        }
                        receivedString.append(c);
                    } else {
                        Thread.sleep(1);
                        waiting++;
                    }
                }

                if (received == 0 || receivedString.length() == 0) {
                    running = false;
                    System.out.println("================== STOPPING THREAD ==================");
                } else {
                    System.out.println("Received packet " + receivedString.toString());
                    owner.handlePacket(receivedString.toString(), socket, this);
                }



            } catch (IOException e) {
                System.out.println("==== Socket Timeout (30 seconds) ====");
                running = false;
                e.printStackTrace();
            }/* finally {
                    if (socket != null)
                        try { socket.close(); } catch (IOException ignored) { }
                }*/ catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        HostConnection hostConnection = owner.getHost(uuid);
        if (hostConnection != null) {
            // This socket was to a host, broadcast a LEAVE to all clients
            for (ClientConnection clientConnection : owner.getClientHost().keySet()) {
                UUID hostUuid = owner.getClientHost().get(clientConnection);
                if (!hostUuid.equals(this.uuid)) {
                    continue;
                }
                if (clientConnection.getSocket().isClosed()) {
                    continue;
                }
                System.out.println("##### Sent LEAVE to client " + clientConnection.getUuid());
                owner.sendPacketToClient(clientConnection.getSocket(), PacketType.LEAVE.ID + ";"
                        + uuid + ";0;"
                        + clientConnection.getUuid() + ";true;");
            }
        } else {
            hostConnection = owner.getHostFromClient(uuid);
            if (hostConnection != null) {
                System.out.println("##### Client " + uuid + " left the game");
                owner.sendToHost(hostConnection, PacketType.LEAVE.ID + ";"
                        + uuid + ";0;"
                        + uuid + ";false;");
            } else {
                System.out.println("##### Could not find Host for this UUID");
            }
        }

        owner.removeInactive();
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }
}
