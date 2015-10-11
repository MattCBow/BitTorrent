// Matt Bowyer
// 156 00 9078

import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.lang.*;

import GivenTools.*;


public class RUBTClient {

    public static void main(String[] args) throws Exception {

        //Check if command arguments are valid
        if ( args.length != 2 || args[0].length()<8 || !args[0].substring(args[0].length() -8).equals(".torrent") ) {
          System.out.println("Usage: java -cp . RUBTClient <torrent> <destination>");
          return;
        }

        try{
          //Declare my peer fields
    	    String my_peer_id = "MCB";
          String my_peer_ip = InetAddress.getLocalHost().getHostAddress().toString();
          int my_peer_port = 6881;

          //Generate Random Peer ID
          Random r = new Random();
      	  for (int i = 3; i < 20; i++){
      	    my_peer_id += r.nextInt(10);
      	  }

          //Declare Networking Tools
          ServerSocket server_socket = null;
          Socket socket;
          DataInputStream in;
          DataOutputStream out;

          //Initiallize a server socket on an open port
          while(server_socket==null){
            try{
              server_socket = new ServerSocket(my_peer_port);

            }
            catch(IOException e){
              server_socket = null;
              my_peer_port++;
            }
            if(my_peer_port>6899){
              System.err.println("No open ports, exiting program");
              throw new IOException();
            }
          }

          //Open torrent file
          byte[] torrentBytes = Files.readAllBytes(new File(args[0]).toPath());
          TorrentInfo torrent = new TorrentInfo(torrentBytes);

          //Escape info hash for url call
          String escaped_info_hash = "";
          for (byte b : torrent.info_hash.array() ) {
            escaped_info_hash += "%"+String.format("%02x", b & 0xff);
          }

          //Create Url to contact tracker
          URL tracker_url = new URL(torrent.announce_url.toString()
                                  +"?info_hash="+escaped_info_hash
                                  +"&peer_id="+my_peer_id
                                  +"&port="+my_peer_port
                                  +"&uploaded=0"
                                  +"&downloaded=0"
                                  +"&left="+torrent.file_length);
          System.out.println("URL: "+tracker_url+"\n");

          //Send http request to the tracker
          HttpURLConnection http_connection = (HttpURLConnection) tracker_url.openConnection();
          http_connection.setRequestMethod("GET");
          in = new DataInputStream(http_connection.getInputStream());
          byte[] http_response = new byte[http_connection.getContentLength()];
          in.readFully(http_response);
          in.close();

          //Get list of peers from tracker
          HashMap<ByteBuffer,Object> tracker = (HashMap<ByteBuffer, Object>) Bencoder2.decode(http_response);
          ArrayList<HashMap<ByteBuffer,Object>> peer_array_list = (ArrayList<HashMap<ByteBuffer, Object>>) tracker.get(ByteBuffer.wrap("peers".getBytes()));
          Iterator<HashMap<ByteBuffer,Object>> it = peer_array_list.iterator();

          //Declare the RU Peer Fields
          String ru_peer_id = "";
          String ru_peer_ip = "";
          int ru_peer_port = 0;

          //Find RU Peer
		      while (ru_peer_port==0 && it.hasNext()) {
            HashMap<ByteBuffer,Object> peer = (HashMap<ByteBuffer, Object>) it.next();
            ru_peer_id = new String(((ByteBuffer)peer.get(ByteBuffer.wrap("peer id".getBytes()))).array());
            if(ru_peer_id.substring(0,6).contains("RU")){
              ru_peer_ip = new String(((ByteBuffer)peer.get(ByteBuffer.wrap("ip".getBytes()))).array());
              ru_peer_port = (Integer) peer.get(ByteBuffer.wrap("port".getBytes()));
            }
          }
          System.out.println("\nID: "+ru_peer_id+"\nIP: "+ru_peer_ip+"\nPort: "+ru_peer_port);

          //Open Connection with RU Peer
          socket = new Socket(ru_peer_ip,ru_peer_port);
          in = new DataInputStream(socket.getInputStream());
          out = new DataOutputStream(socket.getOutputStream());

          //Intialize Handshake
          byte[] handshake = new byte[68];
          byte[] resp = new byte[68];
          byte[] header = {19,'B','i','t','T','o','r','r','e','n','t',' ','p','r','o','t','o','c','o','l',0,0,0,0,0,0,0,0};
          System.arraycopy(header, 0, handshake, 0, 28);
          System.arraycopy(torrent.info_hash.array(), 0, handshake,28 , 20);
		      System.arraycopy(my_peer_id.getBytes(), 0, handshake,48 , 20);

          //Send handshake
          out.write(handshake);
    	    in.read(resp);
          out.close();
          in.close();
          socket.close();

          //Verify Handshake
          boolean valid_handshake = true;
          if(!Arrays.equals(header,Arrays.copyOfRange(resp,0,28)))valid_handshake = false;
          if(!Arrays.equals(torrent.info_hash.array(),Arrays.copyOfRange(resp,28,48)))valid_handshake = false;
          if(!ru_peer_id.equals(new String( Arrays.copyOfRange(resp,48,68) , "UTF-8")))valid_handshake = false;
          System.out.println("\nHANDSHAKE: "+valid_handshake);



        }
        catch (NoSuchFileException no_file) {
            System.out.println("Exception caught opening file");
            System.out.println(no_file.getMessage());
        }
        catch (BencodingException tor_file) {
            System.out.println("Exception caught reading file");
            System.out.println(tor_file.getMessage());
        }
        catch (Exception e){
          System.out.println(e.getMessage());
        }

    }
}
