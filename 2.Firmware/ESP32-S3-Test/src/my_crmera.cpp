#include "my_camera.h"
#include "SD_MMC.h"
#include <dirent.h>   // 为了 opendir/readdir
#define TAG "bsp_camera"
queue_data_t my_image={
    .len=0,
    .buf=nullptr
};
void my_camera_init(void)
{
    camera_config_t config;
    config.ledc_channel = LEDC_CHANNEL_1;  // LEDC通道选择  用于生成XCLK时钟 但是S3不用
    config.ledc_timer = LEDC_TIMER_1; // LEDC timer选择  用于生成XCLK时钟 但是S3不用
    config.pin_d0 = CAM_PIN_D0;
    config.pin_d1 = CAM_PIN_D1;
    config.pin_d2 = CAM_PIN_D2;
    config.pin_d3 = CAM_PIN_D3;
    config.pin_d4 = CAM_PIN_D4;
    config.pin_d5 = CAM_PIN_D5;
    config.pin_d6 = CAM_PIN_D6;
    config.pin_d7 = CAM_PIN_D7;
    config.pin_xclk = CAM_PIN_XCLK;
    config.pin_pclk = CAM_PIN_PCLK;
    config.pin_vsync = CAM_PIN_VSYNC;
    config.pin_href = CAM_PIN_HREF;
    config.pin_sccb_sda = -1;   // 这里写-1 表示使用已经初始化的I2C接口
    config.pin_sccb_scl = CAM_PIN_SIOC;
    config.sccb_i2c_port = 0;
    config.pin_pwdn = CAM_PIN_PWDN;
    config.pin_reset = CAM_PIN_RESET;
    config.xclk_freq_hz = XCLK_FREQ_HZ;
    config.pixel_format = PIXFORMAT_RGB565;
    config.frame_size = FRAMESIZE_VGA;
    config.jpeg_quality = 12;
    config.fb_count = 2;
    config.fb_location = CAMERA_FB_IN_PSRAM;
    config.grab_mode = CAMERA_GRAB_LATEST;

    // camera init
    esp_err_t err = esp_camera_init(&config); // 配置上面定义的参数
    if (err != ESP_OK)
    {
        ESP_LOGE(TAG, "Camera init failed with error 0x%x", err);
        return;
    }

    sensor_t *s = esp_camera_sensor_get(); // 获取摄像头型号

    if (s->id.PID == GC0308_PID) {
        s->set_hmirror(s, 1);  // 这里控制摄像头镜像 写1镜像 写0不镜像
    }
}


void bsp_camera_deinit(void)
{
    esp_camera_deinit();
}


void get_image() {
    camera_fb_t *pic = esp_camera_fb_get();
    if (pic->format == PIXFORMAT_JPEG) {
        my_image.len = pic->len;
        my_image.buf = pic->buf;
    } else {                       // 需要压缩
        Serial0.println("JPEG compression start");
        if(my_image.buf!=nullptr){
          free(my_image.buf);
          my_image.len = 0;
          my_image.buf = nullptr;
        }
        if (!frame2jpg(pic, 60, &my_image.buf, &my_image.len)) {
            Serial0.println("JPEG compression failed");
        }
        Serial0.printf("JPEG ready %zu B\n", my_image.len);
    }
    esp_camera_fb_return(pic);
}



static uint16_t get_picture_max_seq(void)
{
    const char *folder =  "/sdcard/picture";
    DIR *dir = opendir(folder);
    uint16_t max_n = 0;

    if (!dir) {          // 目录不存在就返回 0
        return 0;
    }

    struct dirent *entry;
    while ((entry = readdir(dir)) != nullptr) {
        /* 只处理形如 "IMG_xxxx.jpg" 的文件 */
        uint16_t num;
        if (sscanf(entry->d_name, "IMG_%hu.jpg", &num) == 1) {
            if (num > max_n) max_n = num;
        }
    }
    closedir(dir);
    return max_n;
}
void get_image_forsdcard() { 
    char path[64];
    sprintf(path, "/picture/IMG_%04d.jpg", get_picture_max_seq());
    File file = SD_MMC.open(path);
    if (!file) {
        Serial.println("无法打开图片文件");
        return;
    }

    size_t fileSize = file.size();
    uint8_t* buffer = (uint8_t*)ps_malloc(fileSize); // 使用 PSRAM（如果可用）
    if (!buffer) {
        Serial.println("内存分配失败");
        file.close();
        return;
    }

    size_t bytesRead = file.read(buffer, fileSize);
    file.close();

    if (bytesRead != fileSize) {
        Serial.println("读取不完整");
        free(buffer);
        return;
    }

    // 封装到结构体
    my_image.len = fileSize;
    my_image.buf = buffer;
    Serial.printf("图片读取成功，大小：%zu 字节\n", fileSize);
}