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
            for (int i = 3; i < 20; i++)my_peer_id += r.nextInt(10);


            //Declare Networking Tools
            ServerSocket server_socket = null;
            Socket socket;
            DataInputStream in;
            DataOutputStream out;

            //Initialize a server socket on an open port
            while(server_socket==null){
                try{
                    server_socket = new ServerSocket(my_peer_port);

                }
                catch(IOException e){
                    server_socket = null;
                    my_peer_port++;
                }
                if(my_peer_port>6899){
                    System.err.println("Unable to open any ports");
                    throw new IOException();
                }
            }

            //Open torrent file and initialize the destination file
            byte[] torrentBytes = Files.readAllBytes(new File(args[0]).toPath());
            TorrentInfo torrent = new TorrentInfo(torrentBytes);
            byte[] destination_file = new byte[torrent.file_length];

            //Escape info hash for url call
            String escaped_info_hash = "";
            for (byte b : torrent.info_hash.array() ) escaped_info_hash += "%"+String.format("%02x", b & 0xff);

            //Send http request to the tracker
            URL tracker_url = new URL(torrent.announce_url.toString()
                                      +"?info_hash="+escaped_info_hash
                                      +"&peer_id="+my_peer_id
                                      +"&port="+my_peer_port
                                      +"&left="+torrent.file_length
                                      +"&uploaded=0"
                                      +"&downloaded=0");
            System.out.println("\nURL: "+tracker_url);
            HttpURLConnection http_connection = (HttpURLConnection) tracker_url.openConnection();
            http_connection.setRequestMethod("GET");
            in = new DataInputStream(http_connection.getInputStream());
            byte[] http_response = new byte[http_connection.getContentLength()];
            in.read(http_response);
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

            //Print Connection Summary
            System.out.println("\nID: "+ru_peer_id+"\nIP: "+ru_peer_ip+"\nPort: "+ru_peer_port);
            System.out.println("\nID: "+my_peer_id+"\nIP: "+my_peer_ip+"\nPort: "+my_peer_port);

            //Open Connection with RU Peer
            socket = new Socket(ru_peer_ip,ru_peer_port);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            byte[] peer_message = new byte[68];

            //Initialize Handshake
            byte[] handshake_message = new byte[68];
            byte[] handshake_header = {19,'B','i','t','T','o','r','r','e','n','t',' ','p','r','o','t','o','c','o','l',0,0,0,0,0,0,0,0};
            System.arraycopy(handshake_header, 0, handshake_message, 0, 28);
            System.arraycopy(torrent.info_hash.array(), 0, handshake_message,28 , 20);
  		    System.arraycopy(my_peer_id.getBytes(), 0, handshake_message,48 , 20);

            //Send handshake
            out.write(handshake_message);
      	    in.read(peer_message);

            //Verify Handshake
            boolean valid_handshake = true;
            if(!Arrays.equals(handshake_header,Arrays.copyOfRange(peer_message,0,28)))valid_handshake = false;
            if(!Arrays.equals(torrent.info_hash.array(),Arrays.copyOfRange(peer_message,28,48)))valid_handshake = false;
            if(!ru_peer_id.equals(new String( Arrays.copyOfRange(peer_message,48,68) , "UTF-8")))valid_handshake = false;
            if(!valid_handshake)throw new Exception("Error: Invalid Hanshake");

            //Send Interest Message to Peer
            byte[] interest_message = {0x00,0x00,0x00,0x01,0x02};
            out.write(interest_message);

            //Receive BitField Message from Peer
            Thread.sleep( (Integer)tracker.get(ByteBuffer.wrap("interval".getBytes())) );
            int bitfield_length = in.readInt()-1;
            int bitfield_index = in.readByte();
            if(bitfield_index!=0x05)throw new Exception("Error: Did not Receive BitField Message");
            byte[] peer_bitfield = new byte[bitfield_length];
            in.read(peer_bitfield);

            //Unchoke Connection with Peer
            Thread.sleep((Integer)tracker.get(ByteBuffer.wrap("interval".getBytes())) );
            int unchoke_length = in.readInt();
            int unchoke_index = in.readByte();
            if(unchoke_index!=0x01)throw new Exception("Error: Connection is choked");


            //Send Started Event to Tracker
            tracker_url = new URL(torrent.announce_url.toString()
                                        +"?info_hash="+escaped_info_hash
                                        +"&peer_id="+my_peer_id
                                        +"&port="+my_peer_port
                                        +"&left="+torrent.file_length
                                        +"&downloaded=0"
                                        +"&uploaded=0"
                                        +"&event=started");
            http_connection = (HttpURLConnection) tracker_url.openConnection();
            http_connection.setRequestMethod("GET");
            System.out.println("\nCONNECTION SUCCESFUL");

            //Download File
            for(int piece_index=0;piece_index<torrent.piece_hashes.length;piece_index++){

                //Create File Piece Request
                boolean downloaded_piece = false;
                byte[] file_piece = null;
                byte[] request_message = new byte[17];
                byte[] request_header = {0x00,0x00,0x00,0x0D,0x06};
                System.arraycopy(request_header, 0, request_message, 0 , 5);
      		    System.arraycopy(ByteBuffer.allocate(4).putInt(piece_index).array(), 0, request_message,5 , 4);
                System.arraycopy(ByteBuffer.allocate(4).putInt(0).array(), 0, request_message,9 , 4);
      		    System.arraycopy(ByteBuffer.allocate(4).putInt(torrent.piece_length).array(), 0, request_message,13 , 4);

                int attempt = 0;
                while(!downloaded_piece && attempt<5){
                    attempt++;

                    //Send Request Message to Peer
                    out.write(request_message);
                    Thread.sleep(attempt*attempt*3*(Integer)tracker.get(ByteBuffer.wrap("interval".getBytes())) );

                    //Listen for Message from Peer

                    int length_prefix = in.readInt();
                    if(length_prefix!=torrent.piece_length+9){System.out.println("INCORRECT LENGTH");continue;}

                    int message_index = in.readByte();
                    if(message_index != 0x07){System.out.println("INCORRECT INDEX");continue;}

                    int message_piece_index = in.readInt();
                    if(message_piece_index != piece_index){System.out.println("INCORRECT PIECE INDEX");continue;}

                    int message_piece_offset = in.readInt();
                    if(message_piece_offset != 0){System.out.println("INCORRECT OFFSET");continue;}

                    file_piece = new byte[torrent.piece_length];
                    in.read(file_piece);


                    //Verify Hash
                    MessageDigest hash_function = MessageDigest.getInstance("SHA-1");
                    byte[] file_piece_hash = new byte[20];
                    file_piece_hash = hash_function.digest(file_piece);
                    byte[] valid_hash = torrent.piece_hashes[piece_index].array();
                    if(!Arrays.equals(file_piece_hash,valid_hash)){System.out.println("INCORRECT HASH");continue;}

                    //Send Have Message
                    byte[] have_message = new byte[9];
                    byte[] have_header = {0x00,0x00,0x00,0x05,0x04};
                    System.arraycopy(have_header, 0, have_message, 0 , 5);
          		    System.arraycopy(ByteBuffer.allocate(4).putInt(piece_index).array(), 0, have_message,5 , 4);
                    out.write(have_message);

                    //Load File Piece into File
                    System.arraycopy(file_piece, 0, destination_file, piece_index*torrent.piece_length, torrent.piece_length);
                    downloaded_piece=true;

                    System.out.print("File Piece #"+piece_index+" [SUCCESS]\n");

                }
                if(attempt==5){
                    System.out.print("File Piece #"+piece_index+" [FAILED]\n");
                    throw new Exception("Unable to download file piece");
                }

            }

            //Save File
            FileOutputStream file_out = new FileOutputStream(new File(args[1]));
            file_out.write(destination_file);

            //Send Complete event to Tracker
            tracker_url = new URL(torrent.announce_url.toString()
                                    +"?info_hash="+escaped_info_hash
                                    +"&peer_id="+my_peer_id
                                    +"&port="+my_peer_port
                                    +"&downloaded="+torrent.file_length
                                    +"&left=0"
                                    +"&uploaded=0"
                                    +"&event=completed");
            http_connection = (HttpURLConnection) tracker_url.openConnection();
            http_connection.setRequestMethod("GET");
            System.out.println("DOWNLOAD COMPLETE");

            //Close all Connections
            file_out.close();
            out.close();
            in.close();
            socket.close();
            server_socket.close();
            }
            catch (Exception e){
                System.out.println(e.getMessage());
            }
        System.out.println("\nExiting Program");
        }
    }
