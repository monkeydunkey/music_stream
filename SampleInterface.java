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


import org.alljoyn.bus.BusException;
import org.alljoyn.bus.annotation.BusInterface;
import org.alljoyn.bus.annotation.BusSignal;

@BusInterface (name = "music_stream.SampleInterface")
public interface SampleInterface {
    
    @BusSignal
    public void music_data(byte[] data) throws BusException;
    
    @BusSignal
    public void clock_sync(long countdown )throws BusException;
    
    @BusSignal
    public void delay_est(long time_stamp,long time_stamp_pre) throws BusException;
    
    @BusSignal
    public void song_change(long duration) throws BusException;
    
    @BusSignal
    public void re_sync() throws BusException;
}
