package de.ikosidodekaeder.server;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Scanner;

/**
 * Created by Sven on 20.02.2018.
 */

public class ListenThread implements Runnable {

    private Main owner;
    private Socket socket;
    private BufferedReader reader;
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
                    owner.handlePacket(receivedString.toString(), socket);
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

    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }
}
