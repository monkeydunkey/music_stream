/**
* @SmartJoyn feature 3 - Group Play 
*/
package music_stream;

/**
 *
 * @author Shashank
 */
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
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
import org.tritonus.share.sampled.file.TAudioFileFormat;

/** This is the service part of our group play feature. 
 * This is accessible only to user who are creator of the channel. Only channel creator has the 
 * right to choose the files that are to be played and the right to start and stop
 * the stream. 
*/


public class Service {

    static {
        System.loadLibrary("alljoyn_java");
    }

    private static final short CONTACT_PORT = 42;     //This is the alljoyn port through which communication takes place 
    private static SampleInterface myInterface;       
    private static boolean mSessionEstablished = false; //this is notifying whether a client has joined or not
    private static int mSessionId;                      //this stores the connection's session id  
    private static String mJoinerName;
    private static FileInputStream in;                  //this stores the FileInputStream object of the current song
    private static long previous_time;                  //This used for syncing purpose
    private static Timer t1;                            //This timer is used to schedule the player
    private static TimerTask music_player;              //This is the task that handles the music player
    private static long delay = 0;                      //this stores the Round trip time of a communication with other devices
    private static int delay_count = 0;                 
    private static long curr_file_dur;                  //This stores the current music file duration/track length
    private static Thread music_player_handler = null;  //this handles the continuous music playing
    private static Boolean music_player_running = true; //this is used to specify whether the music is to be played or not
    private static data_transfer_thread data_transfer_handler = null;   //this handles the data transfer from service to various clients 
    private static Boolean data_transfer = true;                        //this specifies whether data_transfer should occur or not
    private static ArrayList<String> filelist;                          //this stores all the names of the music files to be played
    private static musicPlayerThread mp3player = null;                  //this stores the object of the music player
    private static long[] music_duration;                               //this stores the duration of all music files

    // data is sent through the interface
    public static class SignalInterface implements SampleInterface, BusObject {

        @Override
        public void music_data(byte[] data) throws BusException {
            // No implementation required for sending data
        }

        @Override
        public void clock_sync(long countdown) throws BusException {
            // No implementation required for sending data
        }

        //This method is used to estimate the delay between client and service
        @Override
        public void delay_est(long time_stamp, long previous_time) throws BusException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        //This method is used to notify the client that the data to be received next is of a different song
        @Override
        public void song_change(long duration) throws BusException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
        
        //This method is used to initializes the variables on the client side
        @Override
        public void re_sync() throws BusException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }
//This is used to handle the signals on the alljoyn bus 
    public static class SampleSignalHandler {
        //This is used for clock syncing purpose
        @BusSignalHandler(iface = "music_stream.SampleInterface", signal = "clock_sync")
        public void clock_sync(long count_down) throws BusException, FileNotFoundException {

        }
        //This is used for estimating the delay of the network
        @BusSignalHandler(iface = "music_stream.SampleInterface", signal = "delay_est")
        public void delay_est(long time_stamp, long time_stamp_pre) throws IOException, BusException, InterruptedException {
            delay_count++;
            System.out.println("it hererer");
            long received = System.currentTimeMillis();
            if (received - previous_time > delay) {
                delay = received - previous_time;
            }
            if (delay_count >= 2) {
                System.out.println("delay" + delay);
                myInterface.clock_sync(2 * delay);
                delay_count=0;
                asyncMusicPlay(2 * delay);
                
            } else {
                Thread.sleep(60);
                System.out.println("pre gets updated");
                myInterface.delay_est(System.currentTimeMillis(), received);
                previous_time = System.currentTimeMillis();
            }
        }
    }
    //This creates a new thread on which music player handler is to be run 
    public static void asyncMusicPlay(final long delay) {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    play_music(delay);
                } catch (Exception ex) {
                    System.out.println("music player handler cannot be started");
                }
            }
        };
        music_player_handler = new Thread(task, "musicThread");
        music_player_handler.start();
    }

    //this method handles the continuous playing of music 
    public static void play_music(long delay) throws InterruptedException, IOException {

        mp3player = new musicPlayerThread(in);
        Timer t1 = new Timer(true);
        t1.schedule(mp3player, delay);
        Thread.sleep(delay + curr_file_dur);
        in.close();
        for (int j = 1; j < filelist.size(); j++) {
            in = new FileInputStream(filelist.get(j));
            mp3player = new musicPlayerThread(in);
            t1 = new Timer(true);
            System.out.println("player is about to start");
            t1.schedule(mp3player, 500);

            Thread.sleep(curr_file_dur);
            
            in.close();
            if (!music_player_running) {
                break;
            }
        }
    }

    //This method is called after the user has selected the music he/she wants to play and is ready to start playing the songs
    public static void play() throws FileNotFoundException, UnsupportedAudioFileException, IOException, BusException, InterruptedException {
        System.out.println("current time" + System.currentTimeMillis());
        music_duration = new long[filelist.size()];
        for (int j = 0; j < filelist.size(); j++) {
            music_duration[j] = DurationWithMp3Spi(filelist.get(j));
        }

        sync();

    }

    //This method is used to stop the music player and the data transfer
    public static void stop() throws BusException, IOException {
        if (mp3player != null) {
            mp3player.stop();
        }
        if(data_transfer_handler!=null){
            data_transfer_handler.set_running(false);
        }
        music_player_running = false;
        myInterface.re_sync();
        delay_count=0;
        if(in!=null){
            in.close();
        }
        
    }
    
    //This method is used to initialize the filelist which stores the names of all the files
    //that are to be played
    public static void add_song(ArrayList<String> arr) {
        filelist = new ArrayList<String>();
        for (int i = 0; i < arr.size(); i++) {
            filelist.add(arr.get(i));
        }
    }

    //This initiates the sync process. It initializes all variables 
    public static void sync() throws UnsupportedAudioFileException, IOException, BusException, InterruptedException {
        if (mp3player != null) {
            mp3player.stop();
        }
        if(data_transfer_handler!=null){
            data_transfer_handler.set_running(false);
        }
        music_player_running = false;
        myInterface.re_sync();
        delay_count=0;
        if(in!=null){
            in.close();
        }
        Thread.sleep(100);
        in = new FileInputStream(filelist.get(0));
        previous_time = System.currentTimeMillis();
       
        // a new data_transfer_handler is initialized
        data_transfer_handler = new data_transfer_thread(filelist, myInterface, music_duration);
        data_transfer_handler.start();
        
    }

    //It sets the duration of the current music file
    public static void set_curr_file_dur(long dur) {
        System.out.println("curr file duration"+dur);
        curr_file_dur = dur;
    }

    

    private static class MyBusListener extends BusListener {

        public void nameOwnerChanged(String busName, String previousOwner, String newOwner) {
            if ("com.my.well.known.name".equals(busName)) {
                System.out.println("BusAttachement.nameOwnerChanged(" + busName + ", " + previousOwner + ", " + newOwner);
            }
        }
    }

    //It runs the service part of the group play feature
    public static void run_service() throws FileNotFoundException, JavaLayerException, IOException, InterruptedException, UnsupportedAudioFileException, BusException{
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
        //new client_thread().start();
        try {
            while (!mSessionEstablished) {
                Thread.sleep(10);
            }

            System.out.println(String.format("SignalEmitter sessionID = %d", mSessionId));

            SignalEmitter emitter = new SignalEmitter(mySignalInterface, mSessionId, SignalEmitter.GlobalBroadcast.On);

            myInterface = emitter.getInterface(SampleInterface.class);
            
            //It invokes the MusicPlayerGUI which provides the users the ability to select music and play them 
            java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
              new MusicPlayerGUI().setVisible(true);
    
            }
        });
            
            
        } catch (InterruptedException ex) {
            System.out.println("Interrupted");
        }
        while (true) {
            Thread.sleep(1000);
        }
    }
    public static void main(String[] args) throws FileNotFoundException, JavaLayerException, IOException, InterruptedException, UnsupportedAudioFileException, BusException {

        run_service();
    }

    //This method is used to calculate the duration of the a music file whose path and name is specified by string s 
    private static long DurationWithMp3Spi(String s) throws UnsupportedAudioFileException, IOException {
        System.out.println("file name "+s);
        File f1 = new File(s);
        AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(f1);
        if (fileFormat instanceof TAudioFileFormat) {
            Map<?, ?> properties = ((TAudioFileFormat) fileFormat).properties();
            String key = "duration";
            Long microseconds = (Long) properties.get(key);
            long mili = (microseconds / 1000);
            long sec = (mili / 1000) % 60;
            long min = (mili / 1000) / 60;
            System.out.println("time = " + min + ":" + sec);
            return mili;
        } else {
            throw new UnsupportedAudioFileException();
        }

    }
}

//This thread implementation is used to run the music player
class musicPlayerThread extends TimerTask {

    FileInputStream data;
    static Player mp3player;

    public musicPlayerThread(FileInputStream in) {
        this.data = in;
    }

    public void stop() {
        mp3player.close();
    }

    public void run() {

        try {
            System.out.println("player " + System.currentTimeMillis());
            mp3player = new Player(data);
            mp3player.play();
        } catch (JavaLayerException ex) {
            Logger.getLogger(musicPlayerThread.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}

//This thread implementation is used for handling the data transfer to the clients
class data_transfer_thread extends Thread {

    public ArrayList<String> filelist;
    public SampleInterface myInterface;
    public long[] music_duration;
    public Boolean running;

    public data_transfer_thread(ArrayList<String> files, SampleInterface Interface, long[] music_length) {
        filelist = files;
        myInterface = Interface;
        music_duration = music_length;
        running = true;
    }

    public void set_running(Boolean flag){
        running = false;
    } 
    public void run() {
        System.out.println("Data transer starts");
        FileInputStream in2;
        try {
            in2 = new FileInputStream(filelist.get(0));
            myInterface.delay_est(System.currentTimeMillis(), 0);
            for (int j = 0; j < filelist.size(); j++) {
                if (running) {
                    in2 = new FileInputStream(filelist.get(j));
                    long curr_file_dur = music_duration[j];
                    Service.set_curr_file_dur(curr_file_dur);
                    myInterface.song_change(music_duration[j]);
                    System.out.println("song change " + curr_file_dur);

                    int sleep_count = 0;
                    while (in2.available() > 0 && running) {
                        //System.out.println("data transfered");
                        int len = in2.available();
                        if (len > 50000) {
                            len = 50000;
                        }
                        byte[] data = new byte[len];
                        in2.read(data, 0, len);
                        if(running){
                        myInterface.music_data(data);
                        }
                        sleep_count++;
                        Thread.sleep(50);

                    }
                    in2.close();
                    Thread.sleep(curr_file_dur - (sleep_count * 150));
                }
                else{
                    break;
                }
                System.out.println("data transfer stops");
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(data_transfer_thread.class.getName()).log(Level.SEVERE, null, ex);
        } catch (BusException ex) {
            Logger.getLogger(data_transfer_thread.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(data_transfer_thread.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(data_transfer_thread.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}
