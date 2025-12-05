#ifndef MY_SD_H
#define MY_SD_H

#include "Arduino.h"


int save_jpg_file(const uint8_t *jpeg_buf, size_t jpeg_size);
void start_write(const char *path);
void update_write(uint8_t *data, size_t len);
void end_write();
void my_sd_init();


#endif