# ozmod
OZMod is a Java sound library designed to replay MOD tracker musics (MOD, S3M, XM, IT) and sounds (WAV, AIFF and AU).

It can be integrated in your application in a matter of minuts, it's free under the LGPL licence and works everywhere (PC, MAC, Linux, ...) even into a web browser.

Data can come from disk or any URL. The main motivation of OZMod is to be able to replay music with a basic native Java configuration and without the need to pass by heavy mechanism like MP3 that can weight tons of mega bytes for long music. OZMod doesn't use timer interruption, it works entirely using threads and has very low latency. All sounds FX are supported and it includes linear resampling for a better sound quality.

All of your MOD should be replayed without any difficulties with a very low CPU usage. Currently, OZMod is designed for 16 bits, stereo, 44.1 khz and has a sound reproducing better than standard like Winamp.

http://www.tsarevitch.org/ozmod/.

### Does not work for iOS (no audio device to hook into)

### Does not work for HTML (no audio device to hook info)

#### DESKTOP USERS: YOU MUST INCREASE YOUR AUDIO BUFFER SIZE !!!

config.audioDeviceBufferSize=16384;
