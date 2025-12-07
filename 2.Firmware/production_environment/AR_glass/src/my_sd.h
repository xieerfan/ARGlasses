#ifndef MY_SD_H
#define MY_SD_H

#include "Arduino.h"
#define BSP_SD_CLK          (GPIO_NUM_5)
#define BSP_SD_CMD          (GPIO_NUM_4)
#define BSP_SD_D0           (GPIO_NUM_11)


// #define BSP_SD_CLK          (GPIO_NUM_47)
// #define BSP_SD_CMD          (GPIO_NUM_48)
// #define BSP_SD_D0           (GPIO_NUM_21)


int save_jpg_file(const uint8_t *jpeg_buf, size_t jpeg_size);
void start_write(const char *path);
void update_write(uint8_t *data, size_t len);
void end_write();
void my_sd_init();
void delete_file(const char *path);

#endif