/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package music_stream;

/**
 *
 * @author Shashank
 */
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;
import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.SessionListener;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.SignalEmitter;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.annotation.BusSignalHandler;

public class Client {

    static {
        System.loadLibrary("alljoyn_java");
    }
    private static final short CONTACT_PORT = 42;
    static BusAttachment mBus;
    private static byte[] mu_data = new byte[10000000];
    private static int offset = 0;
    private static Boolean connected = false;
    private static Boolean connection_ready = false;
    private static SignalInterface mySignalInterface = null;
    private static SampleInterface myInterface;
    private static Boolean Start_playing = false;
    private static int time_sync_count = 0;
    private static long time_left = 0;
    private static long previous_time;
    private static ByteArrayInputStream in;
    private static Boolean time_sync_completed = false;
    private static Date date = new Date();

    public static class SampleSignalHandler {

        @BusSignalHandler(iface = "music_stream.SampleInterface", signal = "music_data")
        public void music_data(byte[] data) {
            int j = 0;
            for (int i = offset; i < offset + data.length; i++, j++) {
                mu_data[i] = data[j];
            }
            offset += data.length;
        }

        @BusSignalHandler(iface = "music_stream.SampleInterface", signal = "clock_sync")
        public void clock_sync(long count_down) {

            if (time_sync_count == 0) {
                previous_time = System.currentTimeMillis();
                System.out.println(previous_time);
                time_left = count_down;
                time_sync_count++;
            } else {
                if (System.currentTimeMillis() - previous_time < 100) {
                    /*System.out.println(System.currentTimeMillis());
                    System.out.println(previous_time);
                    System.out.println("");*/
                    time_left = count_down;
                } else {
                    
                    time_left -= (System.currentTimeMillis() - previous_time);
                }
                previous_time = System.currentTimeMillis();
                
                //System.out.println(previous_time);
                time_sync_count++;

                if (time_sync_count == 5) {
                    time_sync_completed = true;
                    TimerTask music_player = new musicPlayer(in);
                    Timer t1 = new Timer(true);
                    System.out.println("time left " + time_left);
                    t1.schedule(music_player, time_left);

                }
            }
        }
    }

    public static class SignalInterface implements SampleInterface, BusObject {

        @Override
        public void music_data(byte[] data) throws BusException {
            // No implementation required for sending data
        }

        @Override
        public void clock_sync(long count_down) throws BusException {

        }

    }

    public static void main(String[] args) throws JavaLayerException, BusException, InterruptedException {

        class MyBusListener extends BusListener {

            public void foundAdvertisedName(String name, short transport, String namePrefix) {
                System.out.println(String.format("BusListener.foundAdvertisedName(%s, %d, %s)", name, transport, namePrefix));
                short contactPort = CONTACT_PORT;
                SessionOpts sessionOpts = new SessionOpts();
                sessionOpts.traffic = SessionOpts.TRAFFIC_MESSAGES;
                sessionOpts.isMultipoint = true;
                sessionOpts.proximity = SessionOpts.PROXIMITY_ANY;
                sessionOpts.transports = SessionOpts.TRANSPORT_ANY;

                Mutable.IntegerValue sessionId = new Mutable.IntegerValue();

                mBus.enableConcurrentCallbacks();

                Status status = mBus.joinSession(name, contactPort, sessionId, sessionOpts, new SessionListener());
                if (status != Status.OK) {
                    return;
                }
                System.out.println(String.format("BusAttachement.joinSession successful sessionId = %d", sessionId.value));
                SignalEmitter mySignalEmitter = new SignalEmitter(mySignalInterface, sessionId.value, SignalEmitter.GlobalBroadcast.On);
                myInterface = mySignalEmitter.getInterface(SampleInterface.class);
                connected = true;
                System.out.println("we are here");
            }

            public void nameOwnerChanged(String busName, String previousOwner, String newOwner) {
                if ("com.my.well.known.name".equals(busName)) {
                    System.out.println("BusAttachement.nameOwnerChagned(" + busName + ", " + previousOwner + ", " + newOwner);
                }
            }

        }

        mBus = new BusAttachment("AppName", BusAttachment.RemoteMessage.Receive);

        BusListener listener = new MyBusListener();
        mBus.registerBusListener(listener);

        Status status = mBus.connect();
        if (status != Status.OK) {
            return;
        }
        System.out.println("BusAttachment.connect successful");

        SampleSignalHandler mySignalHandlers = new SampleSignalHandler();

        status = mBus.registerSignalHandlers(mySignalHandlers);
        if (status != Status.OK) {
            System.out.println(status);
            return;
        }
        System.out.println("BusAttachment.registerSignalHandlers successful");

        mySignalInterface = new SignalInterface();
        status = mBus.registerBusObject(mySignalInterface, "/MyService/Path");
        if (status != status.OK) {
            System.out.println("SignalInterface not registered");
            return;
        }

        System.out.println("BusAttachment.registerSignalInterface successful");

        status = mBus.findAdvertisedName("com.my.well.known.name");
        if (status != Status.OK) {
            return;
        }
        System.out.println("BusAttachment.findAdvertisedName successful " + "com.my.well.known.name");
        while (!connected) {
            System.out.println("it's stuck");
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                System.out.println("Exception caught at sleep in client");
            }
        }

        System.out.println("start playing");
        in = new ByteArrayInputStream(mu_data);
        /*Player mp3player = new Player(in);
         mp3player.play();
         /*while(true) {
         try {
         Thread.sleep(5000);
         } catch (InterruptedException e) {
         System.out.println("Program interupted");
         }
         }*/
        while (true) {
            Thread.sleep(1000);
        }
    }
}

class musicPlayer extends TimerTask {

    ByteArrayInputStream data;

    public musicPlayer(ByteArrayInputStream in) {
        this.data = in;
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
