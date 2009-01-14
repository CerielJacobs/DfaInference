package DfaInference;

import java.io.IOException;

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
        // Create an ibis instance.
        ibis = IbisFactory.createIbis(ibisCapabilities, null, portType);
    }
    
    public void master(ControlResultPairTable table) throws IOException {
        master = ibis.registry().elect("Master");
        this.table = table; 
        receivePort = ibis.createReceivePort(portType, "Master");
        receivePort.enableConnections();
        receivePort.enableMessageUpcalls();
    }
    
    public synchronized void client() throws IOException {
        if (master == null) {
            master = ibis.registry().getElectionResult("Master");
        }
        sendPort = ibis.createSendPort(portType);
        sendPort.connect(master, "Master");
    }

    public void finalize() {
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
    
    public void sendResult(ControlResultPair p) throws IOException {
        if (sendPort != null) {
            WriteMessage w = sendPort.newMessage();
            w.writeObject(p);
            w.finish();
        } else if (table != null) {
            table.putResult(p);
        }
    }
    
    public void upcall(ReadMessage m) throws IOException,
            ClassNotFoundException {
        table.putResult((ControlResultPair) m.readObject());
    }
}
