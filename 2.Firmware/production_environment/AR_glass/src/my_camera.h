#ifndef MY_CAMERA_H
#define MY_CAMERA_H


#include "Arduino.h"
#include "esp_camera.h"




#define CAM_PIN_PWDN 5
#define CAM_PIN_RESET -1
#define CAM_PIN_XCLK 8
#define CAM_PIN_SIOD 18
#define CAM_PIN_SIOC 17

#define CAM_PIN_D7 9
#define CAM_PIN_D6 7
#define CAM_PIN_D5 6
#define CAM_PIN_D4 11
#define CAM_PIN_D3 13
#define CAM_PIN_D2 21
#define CAM_PIN_D1 14
#define CAM_PIN_D0 12
#define CAM_PIN_VSYNC 16
#define CAM_PIN_HREF 15
#define CAM_PIN_PCLK 10
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
