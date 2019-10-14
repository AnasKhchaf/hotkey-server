package com.rfw.hotkey_server.net;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.rfw.hotkey_server.util.Utils.*;

public class Server {
    private static final Logger LOGGER = Logger.getLogger(Server.class.getName());

    private ServerSocket serverSocket;
    public volatile boolean stop = false;

    private Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
    }

    private Server() throws IOException {
        serverSocket = new ServerSocket(0);
    }

    private void startServer() throws IOException {
        System.out.println("Starting server ...");
        System.out.printf("IP Address: %s\nPort: %d\n", getLocalIpAddress(), serverSocket.getLocalPort());
        System.out.println();

        while (!stop) {
            Socket socket = serverSocket.accept();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            new Thread(() -> handleConnection(socket, in, out)).start(); // start a new thread to handle the connection
        }
    }

    private void handleConnection(Socket socket, BufferedReader in, PrintWriter out) {
        try {
            String message = in.readLine();
            JSONObject receivedPacket = new JSONObject(new JSONTokener(message));

            switch (receivedPacket.getString("type")) {
                case "handshake": // connection request
                    String clientName = receivedPacket.getString("deviceName");
                    switch (receivedPacket.getString("connectionType")) {
                        case "normal":
                            JSONObject responsePacket = new JSONObject();
                            responsePacket.put("type", "handshake");
                            responsePacket.put("deviceName", getDeviceName());
                            out.println(responsePacket);

                            new ConnectionHandler(socket, in, out, clientName, this).start();
                            System.out.printf("Connected to %s [%s]\n", clientName, getRemoteSocketAddressAndPort(socket));
                            System.out.println("Handler Type: normal");

                            break;

                        default:
                            LOGGER.log(Level.SEVERE, "Server.handleConnection: unknown connection type requested");
                    }
                    break;

                case "ping":
                    JSONObject responsePacket = new JSONObject();
                    responsePacket.put("type", "ping");
                    responsePacket.put("deviceName", getDeviceName());
                    receivedPacket.put("ipAddress", getLocalIpAddress());
                    responsePacket.put("port", serverSocket.getLocalPort());
                    out.println(responsePacket);
                    break;

                default:
                    LOGGER.log(Level.SEVERE, "Server.handleConnection: unknown packet type");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Server.handleConnection: error handling connection");
        }
    }

    public static void main(String[] args) {
        try {
            Server server;
            if (args.length >= 1) {
                int port = Integer.parseInt(args[0]);
                server = new Server(port);
            } else { // if no port specified bind server to any available port
                server = new Server();
            }

            try {
                server.startServer();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Server.main: IO exception occurred, server stopped");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Server.main: Server failed to start");
        }
    }
}
