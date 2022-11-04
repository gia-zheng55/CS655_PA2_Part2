import javax.swing.plaf.basic.BasicButtonUI;
import java.util.*;

public class StudentNetworkSimulator extends NetworkSimulator {
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                     Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity):
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment):
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     *  Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData):
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *          returns the Packet's payload
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;

    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

    // A variable
    private int LAR;
    private int LPS;
    private int currentSeq;
    private Queue<Packet> bufferA;
    private Queue<Packet> sentBufferA;
    private ArrayList<Integer> ackedBufferA;
    private int[] sackA;

    // Sender buffer size
    public static final int bufferASize = 50;

    // B variable
    private int LPA;
    private int NPE;
    private LinkedList<Packet> bufferB;
    private int[] sackB;

    private int PacketToLayer5InA = 0;

    private int AckSentByB = 0;

    private int CorruptedPackets = 0;

    // Output variables
    private int packetNum = 0;
    private int retransmitNum = 0;
    private int ackNum = 0;
    private int firstACKNum = 0;
    private double RTT = 0;
    private double ComTime = 0;

    // record the sending time of packets with retransmission
    private HashMap<Integer, Double> retransmitTime = new HashMap<>();

    // record the sending time of packets
    private HashMap<Integer, Double> normalTime = new HashMap<>();

    // This is the constructor.  Don't touch!
    public StudentNetworkSimulator(int numMessages,
                                   double loss,
                                   double corrupt,
                                   double avgDelay,
                                   int trace,
                                   int seed,
                                   int winsize,
                                   double delay) {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize * 2; // set appropriately; assumes SR here!
        RxmtInterval = delay;
    }

    // Calculate the difference between to sequence numbers
    protected int difference(int low, int high) {
        int diff = high - low;
        return diff < 0 ? diff + LimitSeqNo : diff;
    }

    // check if the packet is corrupt
    protected boolean checkCS(Packet packet) {
        int getCS = packet.getChecksum();
        int checksum = generateChecksum(packet.getSeqnum(), packet.getAcknum(), packet.getPayload(), packet.getSack());
        return getCS == checksum;
    }

    // Apply TCP checksum by integrate sequence number and acknowledge number in the checksum
    protected int generateChecksum(int seq, int ack, String payload, int[] sack) {
        int checksum = 0;
        checksum += seq;
        checksum += ack;
        for (int i = 0; i < payload.length(); i++) {
            checksum += (byte) payload.charAt(i);
        }
        for (int i = 0; i < sack.length; i++) {
            checksum += (byte)sack[i];
        }
        return checksum;
    }

    // add the new acked packet into B's sack
    protected void addToSackB(int seq){
        int index = -1;
        for(int i = 4; i >= 0; i--){
            if(sackB[i] == -1){
                index = i;
            }
        }
        // sackB has empty index
        if(index != -1){
            sackB[index] = seq;
        }else{
            // sackB is full, move the whole array forward
            for(int i = 0; i < 4; i++){
                sackB[i] = sackB[i+1];
            }
            sackB[4] = seq;
        }
    }

    protected boolean ifBacked(int seq){
        return ackedBufferA.contains(seq);
    }

    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to ensure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message) {
        // if the buffer is full, drop it
        if (bufferA.size() >= bufferASize) {
            System.out.println("A side: A's buffer is full, drop this packet.");
            return;
        }

        String payload = message.getData();
        int acknum = 0;
        int seqnum = currentSeq;
        currentSeq = (currentSeq + 1) % LimitSeqNo;
        int checksum = generateChecksum(seqnum, acknum, payload, sackA);
        Packet packet = new Packet(seqnum, acknum, checksum, payload, sackA);
        bufferA.add(packet);

        if (difference(LAR, LPS) < WindowSize) {
            if(!bufferA.isEmpty()){
                System.out.println("A side: Send packet " + packet.getSeqnum() + " to B");
                stopTimer(A);
                toLayer3(A, bufferA.peek());
                startTimer(A, RxmtInterval);
                sentBufferA.add(bufferA.peek());
                retransmitTime.put(bufferA.peek().getSeqnum(), getTime());
                normalTime.put(bufferA.peek().getSeqnum(), getTime());
                bufferA.poll();
                LPS = (LPS + 1) % LimitSeqNo;
                packetNum += 1;
            }
        }
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    // 1. correct: sack=0,
    // 2. corrupted
    // 3. duplicate
    // 4. ooo
    protected void aInput(Packet packet) {
        // check if packet is corrupted
        // S2
        if (!checkCS(packet)) {
            System.out.println("A side: ACK packet " + packet.getAcknum() + " corrupted");
            CorruptedPackets++;
        }else{
            ackNum++;
            int ack = packet.getAcknum();
            int[] receivedSack = packet.getSack();
            ArrayList<Integer> Backed = new ArrayList<>();
            for(int ackSeq : receivedSack){
                Backed.add(ackSeq);
            }
            ackedBufferA = Backed;
            String lost = "A side: The received sack is: [";
            for(int i = 0; i < sackB.length; i++){
                lost += sackB[i] + " ";
            }
            lost += "]";
            System.out.println(lost);

            if (ack == LAR) {
                // exist lost packet
                System.out.println("A side: ACK packet " + ack + " duplicated");
                if(!sentBufferA.isEmpty()){
                    Queue<Packet> resent = new LinkedList<>(sentBufferA);
                    while(!resent.isEmpty()){
                        Packet p = resent.poll();
                        if(ifBacked(p.getSeqnum())){
                            continue;
                        }
                        stopTimer(A);
                        toLayer3(A,p);
                        startTimer(A,RxmtInterval);
                        if(normalTime.containsKey(p.getSeqnum())){
                            normalTime.remove(p.getSeqnum());
                        }
                        retransmitNum++;
                    }
                }
            } else {
                System.out.println("A side: Correct ACK");
                // correct or ACK corrupt
                int acknum = ack;
                if(acknum < LAR){
                    acknum += LimitSeqNo;
                }

                if(normalTime.containsKey(ack-1)){
                    RTT = RTT + getTime() - normalTime.get(ack-1);
                    normalTime.remove(ack);
                    firstACKNum++;
                }

                for(int i = 0; i < (acknum-LAR); i++){
                    sentBufferA.poll();
                    ComTime = ComTime + getTime() - retransmitTime.get(((ack-1-i) % LimitSeqNo+LimitSeqNo)%LimitSeqNo);
                    //
                    if(!bufferA.isEmpty()){
                        Packet p = bufferA.poll();
                        System.out.println("A side: Send packet " + p.getSeqnum() + " to B");
                        stopTimer(A);
                        toLayer3(A, p);
                        startTimer(A, RxmtInterval);
                        sentBufferA.add(p);
                        retransmitTime.put(p.getSeqnum(), getTime());
                        normalTime.put(p.getSeqnum(), getTime());
                        LPS = (LPS + 1) % LimitSeqNo;
                        packetNum += 1;
                    }
                }
            }
            LAR = ack;
        }

    }

    // This routine will be called when A's timer expires (thus generating a
    // timer interrupt). You'll probably want to use this routine to control
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped.
    protected void aTimerInterrupt() {
        System.out.println("RTO: Packet, retransmit it");
        if(!sentBufferA.isEmpty()){
            Queue<Packet> resent = new LinkedList<>(sentBufferA);
            while(!resent.isEmpty()){
                Packet p = resent.poll();
                if(ifBacked(p.getSeqnum())){
                    continue;
                }
                stopTimer(A);
                toLayer3(A,p);
                startTimer(A,RxmtInterval);
                if(normalTime.containsKey(sentBufferA.peek().getSeqnum())){
                    normalTime.remove(sentBufferA.peek().getSeqnum());
                }
                retransmitNum++;
            }
        }
    }

    // This routine will be called once, before any of your other A-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit() {
        LAR = 0;
        LPS = 0;
        currentSeq = FirstSeqNo;
        bufferA = new LinkedList<>();
        sentBufferA = new LinkedList<>();
        ackedBufferA = new ArrayList<>();
        sackA = new int[5];
        Arrays.fill(sackA, -1);
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.

    // 1. corrupted packets: drop
    // 2. out of range of RWS: drop and ack
    // 3. NPE packet: ack and send to layer5
    // 4. ooo packet: ack and buffer
    protected void bInput(Packet packet) {
        int seqnum = packet.getSeqnum();
        String backPayload = String.valueOf(seqnum);

        // S1
        if (!checkCS(packet)) {
            System.out.println("B side: Packet " + seqnum + " corrupted");
            CorruptedPackets++;
        } else{
            System.out.println("B side: Current expect packet:" + NPE);
            System.out.println("B side: Current receive packet:" + seqnum);
            if(seqnum == NPE){
                System.out.println("B side: Correct packet");
                // correct packet
                toLayer5(packet.getPayload());
                PacketToLayer5InA++;
                NPE = updateRW(NPE);
                LPA = updateRW(LPA);
                while (!bufferB.isEmpty()) {
                    Packet next = bufferB.get(0);
                    if (next.getSeqnum() == NPE) {
                        toLayer5(next.getPayload());
                        PacketToLayer5InA++;
                        NPE = updateRW(NPE);
                        LPA = updateRW(LPA);
                        bufferB.remove(0);
                    } else {
                        break;
                    }
                }
                Arrays.fill(sackB, -1);
                if(bufferB.size() != 0){
                    for(int i = 0; i < bufferB.size(); i++){
                        sackB[i] = bufferB.get(i).getSeqnum();
                    }
                }
                Packet ackPacket = new Packet(111, NPE, generateChecksum(111, NPE, backPayload, sackB), backPayload, sackB);
                toLayer3(B, ackPacket);
                AckSentByB++;
            } else {
                // find duplicate packets at B
                for(int i = 0; i < bufferB.size(); i++){
                    if(seqnum == bufferB.get(i).getSeqnum()){
                        Packet ackPacket = new Packet(111, NPE, generateChecksum(111, NPE, backPayload, sackB), backPayload, sackB);
                        toLayer3(B, ackPacket);
                        AckSentByB++;
                        return;
                    }
                }
                int rightBoundary = (NPE+WindowSize)%LimitSeqNo;
                if (rightBoundary > NPE) { // check ooo
                    // 1 [ 2 3 4 5 6 7 ] 0
                    if (seqnum <= rightBoundary && seqnum > NPE) {// in SW range
                        if (!bufferB.isEmpty()) {
                            int index = 0;
                            while(index < bufferB.size()){
                                if (bufferB.get(index).getSeqnum() > seqnum) {
                                    // make sure the bufferB is in order
                                    bufferB.add(index, packet);
                                    break;
                                }
                                index++;
                            }
                            if (index == bufferB.size() - 1 && bufferB.get(index).getSeqnum() < seqnum) {
                                bufferB.addLast(packet);
                            }
                        } else {
                            bufferB.add(packet);
                        }
                    }
                } else {
                    if (seqnum <= rightBoundary || seqnum > NPE) {
                        // 6 [ 7 0 1 2 3 4 ] 5
                        if (!bufferB.isEmpty()) {
                            int seq = seqnum;
                            if (seqnum <= rightBoundary) {
                                seq = seqnum + LimitSeqNo;
                            }
                            for (int i = 0; i < bufferB.size(); i++) {
                                if (bufferB.get(i).getSeqnum() > NPE) {
                                    if (bufferB.get(i).getSeqnum() > seq) {
                                        // 0 1 [i] 3
                                        bufferB.add(i, packet);
                                        break;
                                    } else if (i == bufferB.size() - 1) {
                                        bufferB.addLast(packet);
                                        break;
                                    }
                                } else if (bufferB.get(i).getSeqnum() <= rightBoundary) {
                                    if (bufferB.get(i).getSeqnum() + LimitSeqNo > seq) {
                                        bufferB.add(i, packet);
                                        break;
                                    } else if (i == bufferB.size() - 1) {
                                        bufferB.addLast(packet);
                                        break;
                                    }
                                }
                            }
                        } else {
                            bufferB.add(packet);
                        }

                    }
                }
                Arrays.fill(sackB, -1);
                if(bufferB.size() != 0){
                    for(int i = 0; i < bufferB.size(); i++){
                        sackB[i] = bufferB.get(i).getSeqnum();
                    }
                }
                Packet ackPacket = new Packet(111, NPE, generateChecksum(111, NPE, backPayload, sackB), backPayload, sackB);
                toLayer3(B, ackPacket);
                AckSentByB++;
            }
            System.out.println("B side: Next expect packet:" + NPE);
            String Buffer = "B side: Buffer has packets: [";
            for(Packet bufferPacket: bufferB){
                Buffer += bufferPacket.getSeqnum() + " ";
            }
            Buffer += "]";
            System.out.println(Buffer);
        }
    }

    // B receive the correct packet, sliding the RW by 1
    protected int updateRW(int num) {
        return (num + 1) % LimitSeqNo;
    }

    // This routine will be called once, before any of your other B-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit() {
        NPE = FirstSeqNo;
        LPA = NPE;
        bufferB = new LinkedList<>();
        sackB = new int[5];
        Arrays.fill(sackB, -1);
    }

    protected void Simulation_done() {
        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A:" + packetNum);
        System.out.println("Number of retransmissions by A:" + retransmitNum);
        System.out.println("Number of data packets delivered to layer 5 at B:" + PacketToLayer5InA);
        System.out.println("Number of ACK packets sent by B:" + AckSentByB);
        System.out.println("Number of corrupted packets:" + CorruptedPackets);
        System.out.println("Ratio of lost packets:" + ((double) (retransmitNum - CorruptedPackets) / (packetNum + retransmitNum + AckSentByB)));
        System.out.println("Ratio of corrupted packets:" + ((double) (CorruptedPackets) / (packetNum + AckSentByB + CorruptedPackets)));
        System.out.println("Average RTT:" + RTT / firstACKNum);
        System.out.println("Average communication time:" + ComTime / packetNum);
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        // EXAMPLE GIVEN BELOW
        System.out.println("Total RTT time :" + RTT);
        System.out.println("Total communication time :" + ComTime);
    }

}