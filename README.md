Matt Bowyer
156 00 9078
Solo Project				
BitTorrent Phase 2

For Phase 2 of the BitTorrent Project things got more interesting. Queues, Threads, Classes, and Inheritance were introduced into the Project. I added 4 new classes to the Project BTPeer, BTLocalPeer, BTConnection, and BTTracker

BTPeer:
	BTPeer was a basic class with only 4 fields, ip address, id, port number, and bitfield. This Class gave me an easy way to compare peers and pass their information through as parameters. They had no significant functionality besides displaying and comparing their information

BTLocalPeer extends BTPeer implements Runnable:
	BTLocalPeer was a very useful class. it had all of the comparable fields of BTPeer and also served as a server to receive incoming connections from Peers who wanted file pieces. It did not contain too much functionality because when it detected a connection it just notified the BTTracker Object and passed off the work to BTConnection

BTConnection extends Thread:
	BTConnection served as the blood vessels of communication in the BITTorrent Project. It had the socket connection, managed the state of peer connections, and controlled all of the messaged that flowed between two peers. It generally had two different types of communication between seeding connections and leaching connections. If a Remote Peer contacted the Local Peer first it was most likely a leecher and it the Local Peer contacted the Remote Peer first that Remote Peer was most likely a seeder.

BTTRacker implements Runnable:
	BTTracker was the heart of the entire project. This class managed all of the threads and connections going on. It held the file bytes and monitored whether or not the download was done. It held the Request Queue and gave requests to connections. It regularly contacted he Tracker to search for more peers and update the tracker on download progress. It also verified hashes of file pieces

RUBTClient:
	RUBTClient was the backbone of the project. It did not have much functionality however it managed the user experience. It verified correct usage, managed user input, and controlled if the program was running or closed.

At a High Level Overview RUBTClient contacted the BTTracker which created a BTLocalPeer and managed BTConnections with other BTPeers
