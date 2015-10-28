// Matt Bowyer
// 156 00 9078

import java.io.*;
import java.util.*;
import java.net.*;

public class BTPeer
{
    public String id;
    public String ip;
    public int port;

    public BTPeer()
    {
        id = "";
        ip = "";
        port = 0;
    }
    
    public BTPeer(String peer_id, String peer_ip, int peer_port)
    {
        id = peer_id;
        ip = peer_ip;
        port = peer_port;
    }
}
