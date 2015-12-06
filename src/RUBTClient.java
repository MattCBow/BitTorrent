// Matt Bowyer
// 156 00 9078

import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.lang.*;
import java.security.*;

import GivenTools.*;


public class RUBTClient {

    public static void main(String[] args) throws Exception {

        //Check if command arguments are valid
        if ( args.length != 2 || args[0].length()<8 || !args[0].substring(args[0].length() -8).equals(".torrent") ) {
            System.out.println("Usage: java -cp out RUBTClient <torrent> <destination>");
            return;
        }

        System.out.println("\n------RUNNING PROGRAM--------");
        System.out.println("---TYPE EXIT TO SAVE & QUIT---\n");



        //Open torrent file and initialize the destination file
        String file_name = args[1];
        byte[] torrentBytes = Files.readAllBytes(new File(args[0]).toPath());
        TorrentInfo torrent = new TorrentInfo(torrentBytes);
        BTTracker tracker = new BTTracker(torrent, file_name);

        //Tracker Manages the entire Bittorent
        (new Thread(tracker)).start();

        //Manages User Input
        String input;
        Scanner in = new Scanner(System.in);
        while(!(input=in.nextLine()).toUpperCase().equals("EXIT")){
            System.out.println("---TYPE EXIT TO SAVE & QUIT---\n");
        }

        //Exits Program
        tracker.stop();
        System.out.println("------CLOSING PROGRAM--------\n");
        }

}
