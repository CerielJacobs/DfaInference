package DfaInference;

import java.io.IOException;
import java.util.Properties;

import ibis.ipl.Ibis;
import ibis.ipl.IbisCapabilities;
import ibis.ipl.IbisCreationFailedException;
import ibis.ipl.IbisFactory;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.MessageUpcall;
import ibis.ipl.PortType;
import ibis.ipl.ReadMessage;
import ibis.ipl.ReceivePort;
import ibis.ipl.SendPort;
import ibis.ipl.WriteMessage;

public class TableManager implements MessageUpcall {
    
    private static PortType portType =
        new PortType(PortType.COMMUNICATION_RELIABLE,
                PortType.SERIALIZATION_OBJECT, PortType.RECEIVE_AUTO_UPCALLS,
                PortType.CONNECTION_MANY_TO_ONE);

    private static IbisCapabilities ibisCapabilities =
        new IbisCapabilities(IbisCapabilities.ELECTIONS_STRICT);
    
    private final Ibis ibis;
    
    private IbisIdentifier master;
    
    private ControlResultPairTable table = null;
    
    private SendPort sendPort = null;
    
    private ReceivePort receivePort = null;
    
    public TableManager() throws IbisCreationFailedException {
        Properties props = new Properties();
        props.setProperty("ibis.pool.name", "Another_" + System.getProperty("ibis.pool.name"));

        // Create an ibis instance.
        ibis = IbisFactory.createIbis(ibisCapabilities, props, true, null, portType);
    }
    
    public void master(ControlResultPairTable table) throws IOException {
        master = ibis.registry().elect("Master");
        this.table = table; 
        receivePort = ibis.createReceivePort(portType, "Master", this);
        receivePort.enableConnections();
        receivePort.enableMessageUpcalls();
        table.setManager(this);
    }
    
    public synchronized void client(ControlResultPairTable table) throws IOException {
        if (master == null) {
            master = ibis.registry().getElectionResult("Master");
            sendPort = ibis.createSendPort(portType);
            sendPort.connect(master, "Master");
            table.setManager(this);
        }
    }

    public void done() {
        if (sendPort != null) {
            try {
                sendPort.close();
            } catch(Throwable e) {
                // ignored
            }
        }
        try {
            ibis.end();
        } catch(Throwable e) {
            // ignored
        }
    }
    
    public void sendResult(int fixOffset, int depth, ControlResultPair p) throws IOException {
        if (sendPort != null) {
            WriteMessage w = sendPort.newMessage();
            w.writeInt(fixOffset);
            w.writeInt(depth);
            w.writeObject(p);
            w.finish();
        } else if (table != null) {
            table.putResult(fixOffset, depth, p);
        }
    }
    
    public void upcall(ReadMessage m) throws IOException,
            ClassNotFoundException {
        int fixOffset = m.readInt();
        int depth = m.readInt();
        ControlResultPair p = (ControlResultPair) m.readObject();
        table.putResult(fixOffset, depth, p);
    }
}
