SMART JOYN feature 2 - music streaming from desktop to mobile devices and vice versa

Currently only the music data is getting transferred from the source/service to the connected client and the
client plays the song, currently no sync is put in place so multiple clients will not work
To run you need to follow the same steps as in feature 1 in addition you need a music library called Jlayer
 Download link- http://www.javazoom.net/javalayer/sources.html
 Add the jar exceutable file of Jlayer to the project as done for alljoyn in feature 1
 The Service creates the channel and is the music data source
 The Client connects to the service and receives the music stream 
