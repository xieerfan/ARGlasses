#ifndef my_es8311_cpp
#define my_es8311_cpp

/**
 * @brief We define a custom board with the i2c and i2s pins and output_device a sine
 * with the help of the AudioTools I2SCodecStream
 * @author phil schatzmann
 */

#define I2S_NUM         (0)
#define I2S_MCK_IO      (GPIO_NUM_46)
#define I2S_BCK_IO      (GPIO_NUM_1)
#define I2S_WS_IO       (GPIO_NUM_3)
#define I2S_DO_IO       (GPIO_NUM_4)
#define I2S_DI_IO       (GPIO_NUM_2)

void my_es8311_init();
void play_mp3(const char *path);
void mp3_loop();
void mp3_stop();
void mp3_update();
void vol_up();
void vol_down();

#endif