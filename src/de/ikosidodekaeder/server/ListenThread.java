package de.ikosidodekaeder.server;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
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
        Scanner in  = null;
        try {
            in = new Scanner(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        while (running) {

            try {

                int received = 0;

                socket.setSoTimeout(Main.TIME_OUT);
                while (in.hasNext()) {
                    received++;
                    socket.setSoTimeout(Main.TIME_OUT);
                    String packet = in.nextLine();
                    System.out.println("Received packet " + packet);
                    owner.handlePacket(packet, socket);
                }

                if (received == 0) {
                    running = false;
                    System.out.println("================== STOPPING THREAD ==================");
                }



            } catch (IOException e) {
                System.out.println("==== Socket Timeout (30 seconds) ====");
                running = false;
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
