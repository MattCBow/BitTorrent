// Matt Bowyer
// 156 00 9078

import java.io.*;
import java.nio.*;
import java.util.*;
import java.net.*;

import GivenTools.*;

public class BTTracker implements Runnable
{
    private volatile boolean end = false;

    BTLocalPeer local_peer;
    TorrentInfo torrent;
    byte[] destination_file;
    String escaped_info_hash;
    String event;
    int uploaded;
    int downloaded;
    int interval;
    URL tracker_url;
    ArrayList<BTPeer> peers;

    public BTTracker(BTLocalPeer local_peer, TorrentInfo torrent) throws IOException
    {
        this.local_peer = local_peer;
        this.torrent = torrent;
        this.destination_file = new byte[torrent.file_length];
        this.uploaded=0;
        this.downloaded=0;
        this.event="";
        this.escaped_info_hash = "";
        for (byte b : torrent.info_hash.array() ) this.escaped_info_hash += "%"+String.format("%02x", b & 0xff);

    }

    public void run()
    {
        while(!end){
            //Send http request to the tracker
            try{
                tracker_url = new URL(torrent.announce_url.toString()
                                      +"?info_hash="+escaped_info_hash
                                      +"&peer_id="+local_peer.id
                                      +"&port="+local_peer.port
                                      +"&left="+torrent.file_length
                                      +"&uploaded="+uploaded
                                      +"&downloaded="+downloaded
                                      +"&event="+event);

                 System.out.println("\nURL: "+tracker_url);
                 HttpURLConnection http_connection = (HttpURLConnection) tracker_url.openConnection();
                 http_connection.setRequestMethod("GET");
                 DataInputStream in = new DataInputStream(http_connection.getInputStream());
                 byte[] http_response = new byte[http_connection.getContentLength()];
                 in.read(http_response);
                 in.close();

                 System.out.println();
                 for(Byte b: http_response){
                     System.out.printf("0x%02X ", b);
                     Thread.sleep(5);
                 }
                 System.out.println();

                   //Get list of peers from tracker
                 HashMap<ByteBuffer,Object> tracker_map = (HashMap<ByteBuffer, Object>) Bencoder2.decode(http_response);
                 ArrayList<HashMap<ByteBuffer,Object>> peer_array_list = (ArrayList<HashMap<ByteBuffer, Object>>) tracker_map.get(ByteBuffer.wrap("peers".getBytes()));
                 interval = (Integer)tracker_map.get(ByteBuffer.wrap("interval".getBytes()));
                 Thread.sleep(interval);
            }
            catch(MalformedURLException e){}
            catch(InterruptedException e) {}
            catch(BencodingException e) {}
            catch(ProtocolException e) {}
            catch(IOException e) {}
        }
    }

    public void end()
    {
        end = true;
    }
}
