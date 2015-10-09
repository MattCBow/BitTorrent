// Matt Bowyer
// 156 00 9078

import java.net.*;
import java.io.*;
import java.nio.file.*;
import GivenTools.*;
import java.util.Random;


public class RUBTClient {

    public static void main(String[] args) throws Exception {

        //Checks arguments
        String torrentSuffix = "";
        if(args[0].length()>=8) torrentSuffix = args[0].substring(args[0].length() -8);

        if ( args.length != 2 || !torrentSuffix.equals(".torrent") ) {
          System.out.println("Usage: java -cp . RUBTClient <torrent> <destination>");
          return;
        }

        try{
          byte[] torrentBytes = Files.readAllBytes(new File(args[0]).toPath());
          TorrentInfo torrent = new TorrentInfo(torrentBytes);

          String infohash = "";
          for (int i = 0; i < 20; i++) {
            byte b = torrent.info_hash.array()[i];
            infohash += "%"+String.format("%02x", b & 0xff);
          }

          String peerId = getRandomPeerId();

          URL url = new URL(torrent.announce_url.toString()+"?info_hash="+infohash+"&peer_id="+peerId+"&port="
                            +torrent.announce_url.getPort()+"&uploaded=0&downloaded=0&left="+torrent.file_length);
          System.out.println("URL: "+url);




        }
        catch (NoSuchFileException noFile) {
            System.out.println("Exception caught opening file");
            System.out.println(noFile.getMessage());
        }
        catch (BencodingException torFile) {
            System.out.println("Exception caught reading file");
            System.out.println(torFile.getMessage());
        }
        catch (Exception e){
          System.out.println(e.getMessage());
        }
        /*
        int port = Integer.parseInt(args[0]);

        try{
          // create socket
          ServerSocket server = new ServerSocket(port);
          System.out.println("Server opened on "+port);

          // repeatedly wait for connections, and process
          while (true) {

            // a "blocking" call which waits until a connection is requested
            Socket client = server.accept();
            System.out.println("Accepted connection from client at " +client.getRemoteSocketAddress());

            // open up IO streams
            PrintWriter out = new PrintWriter(client.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));

            // waits for data and reads it in until connection dies
            String input;
            while( !(input = in.readLine()).equals("#") && !input.equals("$") ) {
                System.out.println(input);
                String reverse =  new StringBuffer(input).reverse().toString();
                out.println(reverse);
            }

            // close IO streams, then socket
            System.out.println("Closing connection");
            out.close();
            in.close();
            client.close();
          }
        }
        catch (IOException e) {
            System.out.println("Exception caught listening on port " + port);
            System.out.println(e.getMessage());
        }
        */
    }

    public static String getRandomPeerId()
    {
	    Random rand = new Random();
	    int min = 97;
	    int max = 122;
	    String peerId = "";
	    for (int i = 0; i < 20; i++)
	    {
	    	int randInt = rand.nextInt((max - min) + 1) + min;
	    	char c = (char) randInt;
	    	peerId += c;
	    }
      if(peerId.substring(0,4).toUpperCase().equals("RUBT"))peerId=getRandomPeerId();

	    return peerId.toUpperCase();
    }
}
