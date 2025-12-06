#ifndef MY_CAMERA_H
#define MY_CAMERA_H


#include "Arduino.h"
#include "esp_camera.h"




#define CAM_PIN_PWDN 39
#define CAM_PIN_RESET -1
#define CAM_PIN_XCLK 21
#define CAM_PIN_SIOD 1
#define CAM_PIN_SIOC 2

#define CAM_PIN_D7 14
#define CAM_PIN_D6 48
#define CAM_PIN_D5 47
#define CAM_PIN_D4 15
#define CAM_PIN_D3 17
#define CAM_PIN_D2 40
#define CAM_PIN_D1 18
#define CAM_PIN_D0 16
#define CAM_PIN_VSYNC 45
#define CAM_PIN_HREF 46
#define CAM_PIN_PCLK 38
#define XCLK_FREQ_HZ 10000000


typedef struct
{
    size_t len;
    uint8_t *buf;
} queue_data_t;

extern queue_data_t my_image;
void my_camera_init(void);
void bsp_camera_deinit(void);
void get_image();
void get_image_forsdcard();
#endif
