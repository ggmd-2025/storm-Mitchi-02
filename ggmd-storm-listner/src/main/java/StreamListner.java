package main.java;

import java.net.*;
import java.io.*;


/**
 * Gestion de l'affichage en sortie des exitBold de storm
 * */
public class StreamListner {

    public static void main(String[] args){
    String host = "supervisor";
    int port = Integer.parseInt( args[0] );

        try (Socket socket = new Socket(host, port)) {
            System.out.println("Connected to server.");

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            // Thread qui écoute le flux du serveur
            new Thread(() -> {
                try {
                    String data;
                    while ((data = in.readLine()) != null) {
                        System.out.println("Received: " + data);
                    }
                } catch (IOException ignored) {}
            }).start();

            // Le programme reste ouvert tant que la connexion est active
            Thread.sleep(Long.MAX_VALUE);

        } catch (Exception e) {
            e.printStackTrace();
        }finally{
            System.out.println("Client exit!! ");
        }
    }
}
