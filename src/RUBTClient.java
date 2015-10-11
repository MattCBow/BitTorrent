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

        if ( args.length != 2 || args[0].length()<8 || !args[0].substring(args[0].length() -8).equals(".torrent") ) {
          System.out.println("Usage: java -cp . RUBTClient <torrent> <destination>");
          return;
        }

        try{
          byte[] torrentBytes = Files.readAllBytes(new File(args[0]).toPath());
          TorrentInfo torrent = new TorrentInfo(torrentBytes);

          String info_hash = "";
          for (byte b : torrent.info_hash.array() ) {
            info_hash += "%"+String.format("%02x", b & 0xff);
          }

    	    String peer_id = "MCB";
          Random r = new Random();
      	  for (int i = 3; i < 20; i++){
      	    peer_id += r.nextInt(10);
      	  }

          URL url = new URL(torrent.announce_url.toString()+"?info_hash="+info_hash+"&peer_id="+peer_id+"&port="
                            +torrent.announce_url.getPort()+"&uploaded=0&downloaded=0&left="+torrent.file_length);
          System.out.println("URL: "+url+"\n");

          HttpURLConnection con = (HttpURLConnection) url.openConnection();
          con.setRequestMethod("GET");
          DataInputStream dis = new DataInputStream(con.getInputStream());
          byte[] httpResponse = new byte[con.getContentLength()];
          dis.readFully(httpResponse);
          dis.close();

          String ruPeerId = "";
          String ruPeerIP = "";
          int ruPeerPort = 0;

          HashMap<ByteBuffer,Object> tracker = (HashMap<ByteBuffer, Object>) Bencoder2.decode(httpResponse);
          ArrayList<HashMap<ByteBuffer,Object>> peerArrayList = (ArrayList<HashMap<ByteBuffer, Object>>) tracker.get(ByteBuffer.wrap("peers".getBytes()));
          Iterator<HashMap<ByteBuffer,Object>> it = peerArrayList.iterator();

		      while (ruPeerPort==0 && it.hasNext()) {
            HashMap<ByteBuffer,Object> peer = (HashMap<ByteBuffer, Object>) it.next();
            ruPeerId = new String(((ByteBuffer)peer.get(ByteBuffer.wrap("peer id".getBytes()))).array());
            if(ruPeerId.substring(0,6).contains("RU")){
              ruPeerIP = new String(((ByteBuffer)peer.get(ByteBuffer.wrap("ip".getBytes()))).array());
              ruPeerPort = (Integer) peer.get(ByteBuffer.wrap("port".getBytes()));
            }
          }

          System.out.println("\nID: "+ruPeerId+"\nIP: "+ruPeerIP+"\nPort: "+ruPeerPort);

          Socket soc = new Socket(ruPeerIP,ruPeerPort);
          OutputStream out = soc.getOutputStream();
          InputStream in = soc.getInputStream();

          byte[] handshake = new byte[68];
          byte[] resp = new byte[68];
          byte[] header = {19,'B','i','t','T','o','r','r','e','n','t',' ','p','r','o','t','o','c','o','l',0,0,0,0,0,0,0,0};

          System.arraycopy(header, 0, handshake, 0, 28);
          System.arraycopy(torrent.info_hash.array(), 0, handshake,28 , 20);
		      System.arraycopy(peer_id.getBytes(), 0, handshake,48 , 20);

          out.write(handshake);
    	    in.read(resp);

          out.close();
          in.close();
          soc.close();

          System.out.println("\nOUT:"+handshake+"\nIN:"+resp+"\n");
          for (byte b: resp) {
            System.out.print((char) b+" ");
          }

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

    }
}
