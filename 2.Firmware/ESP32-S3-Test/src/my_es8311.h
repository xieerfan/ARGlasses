#ifndef my_es8311_cpp
#define my_es8311_cpp

/**
 * @brief We define a custom board with the i2c and i2s pins and output_device a sine
 * with the help of the AudioTools I2SCodecStream
 * @author phil schatzmann
 */

#define I2S_NUM         (0)
#define I2S_MCK_IO      (GPIO_NUM_16)
#define I2S_BCK_IO      (GPIO_NUM_41)
#define I2S_WS_IO       (GPIO_NUM_45)
#define I2S_DO_IO       (GPIO_NUM_40)
#define I2S_DI_IO       (GPIO_NUM_42)

#define MP3_FILE_PATH "/MP3/"
void my_es8311_init();
void play_mp3(const char *path);
void mp3_loop();
void mp3_stop();
void mp3_update();

#endif