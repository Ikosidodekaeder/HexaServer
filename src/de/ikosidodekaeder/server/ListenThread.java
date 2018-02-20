package de.ikosidodekaeder.server;

import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

/**
 * Created by Sven on 20.02.2018.
 */

public class ListenThread implements Runnable {

    private Main owner;
    private Socket socket;

    private boolean running = true;

    public ListenThread(Main owner, Socket socket) {
        this.owner = owner;
        this.socket = socket;
    }

    @Override
    public void run() {
        System.out.println("Started new thread");
        while (running) {

            owner.removeInactive();

            try {
                Scanner in  = new Scanner(socket.getInputStream());
                //BufferedInputStream in = new BufferedInputStream(client.getInputStream());
                System.out.println("Reading... "
                        + socket.isConnected() + ", "
                        + socket.isBound() + ", "
                        + socket.isClosed() + ", "
                        + socket.isInputShutdown());

                int received = 0;

                while (in.hasNext()) {
                    received++;
                    String packet = in.nextLine();
                    System.out.println("Received packet " + packet);
                    owner.handlePacket(packet, socket);
                }

                if (received == 0) {
                    running = false;
                    System.out.println("================== STOPPING THREAD ==================");
                }

            } catch (IOException e) {
                e.printStackTrace();
            }/* finally {
                    if (socket != null)
                        try { socket.close(); } catch (IOException ignored) { }
                }*/
        }

        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }
}
