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
    
    
}
