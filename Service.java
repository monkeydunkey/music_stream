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
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import org.alljoyn.bus.annotation.BusSignalHandler;

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
    private static FileInputStream in;
    
    // data is sent through the interface
    public static class SignalInterface implements SampleInterface, BusObject {

        @Override
        public void music_data(byte[] data) throws BusException {
            // No implementation required for sending data
        }

        @Override
        public void clock_sync(int countdown) throws BusException {
            // No implementation required for sending data
        }

    }

    public static class SampleSignalHandler {

        @BusSignalHandler(iface = "music_stream.SampleInterface", signal = "clock_sync")
        public void clock_sync(int countdown){
            
        }
    }
    
    private static class MyBusListener extends BusListener {

        public void nameOwnerChanged(String busName, String previousOwner, String newOwner) {
            if ("com.my.well.known.name".equals(busName)) {
                System.out.println("BusAttachement.nameOwnerChanged(" + busName + ", " + previousOwner + ", " + newOwner);
            }
        }
    }

    public static void main(String[] args) throws FileNotFoundException, JavaLayerException, IOException, InterruptedException {

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
        
        SampleSignalHandler mySignalHandlers = new SampleSignalHandler();

        status = mBus.registerSignalHandlers(mySignalHandlers);
        if (status != Status.OK) {
            System.out.println(status);
            return;
        }
        System.out.println("BusAttachment.registerSignalHandlers successful");
        
        status = mBus.advertiseName("com.my.well.known.name", SessionOpts.TRANSPORT_ANY);
        if (status != Status.OK) {
            System.out.println("Status = " + status);
            mBus.releaseName("com.my.well.known.name");
            return;
        }
        System.out.println("BusAttachment.advertiseName 'com.my.well.known.name' successful");
        
        try {
            while (!mSessionEstablished) {
                Thread.sleep(10);
            }

            System.out.println(String.format("SignalEmitter sessionID = %d", mSessionId));

            SignalEmitter emitter = new SignalEmitter(mySignalInterface, mSessionId, SignalEmitter.GlobalBroadcast.On);

            myInterface = emitter.getInterface(SampleInterface.class);
            
            in = new FileInputStream("C:\\Users\\admin\\Music\\Maroon5-Misery.mp3");
            //for data sending purpose
            FileInputStream in2=new FileInputStream("C:\\Users\\admin\\Music\\Maroon5-Misery.mp3");
            TimerTask music_player=new musicPlayerThread(in);
            Timer t1=new Timer(true);
            t1.schedule(music_player, 600);
            new time_sync_Thread(myInterface).start();
            while (true) {
                int len = in2.available();
                if (len > 50000) {
                    len = 50000;
                }
                byte[] data = new byte[len];
                in2.read(data, 0, len);
                myInterface.music_data(data);
                Thread.sleep(60);
            }
        } catch (InterruptedException ex) {
            System.out.println("Interrupted");
        } catch (BusException ex) {
            System.out.println("Bus Exception: " + ex.toString());
        }
        while(true){
            Thread.sleep(1000);
        }
    }
}

class musicPlayerThread extends TimerTask {
FileInputStream data;
  public musicPlayerThread(FileInputStream in) {
    this.data=in;
  }

  public void run() {
  Player mp3player;
    try {
        mp3player = new Player(data);
        mp3player.play();
    } catch (JavaLayerException ex) {
        Logger.getLogger(musicPlayerThread.class.getName()).log(Level.SEVERE, null, ex);
    }
  
  }
}

class time_sync_Thread extends Thread {
SampleInterface myInterface;
  public time_sync_Thread(SampleInterface i) {
    myInterface=i;
  }

  public void run() {
  int time=600;
  for(int i=0;i<5;i++){
    try {
        myInterface.clock_sync(time);
    } catch (BusException ex) {
        Logger.getLogger(time_sync_Thread.class.getName()).log(Level.SEVERE, null, ex);
    }
    try {
        Thread.sleep(100);
    } catch (InterruptedException ex) {
        Logger.getLogger(time_sync_Thread.class.getName()).log(Level.SEVERE, null, ex);
    }
    
    time=time-100;
  }
  }
}