#include "my_es8311.h"
#include "AudioBoard.h"
#include "AudioTools.h"
#include "AudioTools/AudioLibs/I2SCodecStream.h"
#include "AudioTools/AudioCodecs/CodecMP3Helix.h"
#include "SD_MMC.h"
AudioInfo                     audio_info(44100, 2, 16);                // sampling rate, # channels, bit depth
DriverPins                    my_pins;                                 // board pins
AudioBoard                    audio_board(AudioDriverES8311, my_pins); // audio board
I2SCodecStream                i2s_out_stream(audio_board);             // i2s coded
EncodedAudioStream decoder(&i2s_out_stream, new MP3DecoderHelix()); // Decoding stream
File audioFile;
StreamCopy copier(decoder, audioFile);
static bool mp3_is_playing = false;
static float vol=0.5;
void my_es8311_init(){
  Serial.println("Setup starting...");
  Serial.println("I2C pin ...");
  my_pins.addI2C(PinFunction::CODEC,  Wire);
  Serial.println("I2S pin ...");
  my_pins.addI2S(PinFunction::CODEC, I2S_MCK_IO, I2S_BCK_IO, I2S_WS_IO, I2S_DO_IO, I2S_DI_IO);

  Serial.println("Pins begin ..."); 
  my_pins.begin();

  Serial.println("Board begin ..."); 
  audio_board.begin();
  Serial.println("I2S begin ..."); 
  auto i2s_config = i2s_out_stream.defaultConfig(TX_MODE);
  // i2s_config.copyFrom(audio_info);  
  i2s_out_stream.setVolume(vol);
  i2s_out_stream.begin(i2s_config); // this should apply I2C and I2S configuration
  Serial.println("decoder begin ..."); 
  decoder.begin();
}
void play_mp3(const char *path){
  // char file_path[128];
  // sprintf(file_path, "%s%s", MP3_FILE_PATH, path);
  audioFile = SD_MMC.open(path,FILE_READ);
  if(!audioFile){
    Serial.println("Failed to open file for reading");
    return;
  }
  mp3_is_playing=true;
}

void mp3_stop(){
  mp3_is_playing=false;
}
void mp3_update(){ 
    if(mp3_is_playing){
        mp3_is_playing=false;
    }else{
        mp3_is_playing=true;
    }

}
void vol_up(){
  vol=vol=0.1;
  if(vol>1){
    vol=1;
  }
  i2s_out_stream.setVolume(vol);
}
void vol_down(){
  vol=vol-0.1;
  if(vol<0){
    vol=0;
  }
  i2s_out_stream.setVolume(vol);
}
void mp3_loop(){
  if(mp3_is_playing){
    if(copier.copy()==0){ 
        mp3_is_playing=false;
    }
  }
}                                                                               