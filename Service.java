/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package music_stream;

/**
 *
 * @author admin
 */
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import javazoom.jl.decoder.JavaLayerException;
import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.SessionListener;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.SessionPortListener;
import org.alljoyn.bus.SignalEmitter;
import org.alljoyn.bus.Status;
import javazoom.jl.player.Player;

public class Service {

    static {
        System.loadLibrary("alljoyn_java");
    }

    private static final short CONTACT_PORT = 42;
    private static SampleInterface myInterface;
//	static private SignalInterface gSignalInterface;

    static boolean mSessionEstablished = false;
    static int mSessionId;
    static String mJoinerName;

    // data is sent through the interface
    public static class SignalInterface implements SampleInterface, BusObject {

        @Override
        public void music_data(byte[] data) throws BusException {
            // No implementation required for sending data
        }

    }

    private static class MyBusListener extends BusListener {

        public void nameOwnerChanged(String busName, String previousOwner, String newOwner) {
            if ("com.my.well.known.name".equals(busName)) {
                System.out.println("BusAttachement.nameOwnerChanged(" + busName + ", " + previousOwner + ", " + newOwner);
            }
        }
    }

    public static void main(String[] args) throws FileNotFoundException, JavaLayerException, IOException {

        BusAttachment mBus;
        mBus = new BusAttachment("AppName", BusAttachment.RemoteMessage.Receive);

        Status status;

        SignalInterface mySignalInterface = new SignalInterface();

        status = mBus.registerBusObject(mySignalInterface, "/MyService/Path");
        if (status != Status.OK) {
            return;
        }
        System.out.println("BusAttachment.registerBusObject successful");

        BusListener listener = new MyBusListener();
        mBus.registerBusListener(listener);

        status = mBus.connect();
        if (status != Status.OK) {
            return;
        }
        System.out.println("BusAttachment.connect successful on " + System.getProperty("org.alljoyn.bus.address"));

        Mutable.ShortValue contactPort = new Mutable.ShortValue(CONTACT_PORT);

        SessionOpts sessionOpts = new SessionOpts();
        sessionOpts.traffic = SessionOpts.TRAFFIC_MESSAGES;
        sessionOpts.isMultipoint = true;
        sessionOpts.proximity = SessionOpts.PROXIMITY_ANY;
        sessionOpts.transports = SessionOpts.TRANSPORT_ANY;

        status = mBus.bindSessionPort(contactPort, sessionOpts,
                new SessionPortListener() {
                    public boolean acceptSessionJoiner(short sessionPort, String joiner, SessionOpts sessionOpts) {
                        System.out.println("SessionPortListener.acceptSessionJoiner called");
                        if (sessionPort == CONTACT_PORT) {
                            return true;
                        } else {
                            return false;
                        }
                    }

                    public void sessionJoined(short sessionPort, int id, String joiner) {
                        System.out.println(String.format("SessionPortListener.sessionJoined(%d, %d, %s)", sessionPort, id, joiner));
                        mSessionId = id;
                        mJoinerName = joiner;
                        mSessionEstablished = true;
                    }
                });
        if (status != Status.OK) {
            return;
        }
        System.out.println("BusAttachment.bindSessionPort successful");

        int flags = 0; //do not use any request name flags
        status = mBus.requestName("com.my.well.known.name", flags);
        if (status != Status.OK) {
            return;
        }
        System.out.println("BusAttachment.request 'com.my.well.known.name' successful");

        status = mBus.advertiseName("com.my.well.known.name", SessionOpts.TRANSPORT_ANY);
        if (status != Status.OK) {
            System.out.println("Status = " + status);
            mBus.releaseName("com.my.well.known.name");
            return;
        }
        System.out.println("BusAttachment.advertiseName 'com.my.well.known.name' successful");
        FileInputStream in = new FileInputStream("C:\\Users\\admin\\Music\\SexyBack.mp3");
        try {
            while (!mSessionEstablished) {
                Thread.sleep(10);
            }

            System.out.println(String.format("SignalEmitter sessionID = %d", mSessionId));

            SignalEmitter emitter = new SignalEmitter(mySignalInterface, mSessionId, SignalEmitter.GlobalBroadcast.On);

            myInterface = emitter.getInterface(SampleInterface.class);

            while (true) {
                int len = in.available();
                if (len > 50000) {
                    len = 50000;
                }
                byte[] data = new byte[len];
                in.read(data, 0, len);
                myInterface.music_data(data);
                Thread.sleep(1000);
            }
        } catch (InterruptedException ex) {
            System.out.println("Interrupted");
        } catch (BusException ex) {
            System.out.println("Bus Exception: " + ex.toString());
        }
    }
}
