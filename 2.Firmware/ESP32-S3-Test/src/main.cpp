#include "my_ble.h"
#include "my_camera.h"
#include "my_sd.h"
#include "my_es8311.h"
#include "my_txt.h"
#include "my_driver.h"
#include "my_uart.h"
#include "OneButton.h"
#include "Wire.h"
#include "FreeRTOS.h"

OneButton button(0, true);

#define BSP_I2C_SDA           (GPIO_NUM_15)   // SDA引脚
#define BSP_I2C_SCL           (GPIO_NUM_14)   // SCL引脚

bool imageRequested = false;
bool imageTransferInProgress = false;

void button_pressed(){
  Serial.println("Button pressed");
  if (!imageTransferInProgress) {
    imageRequested = true;
    Serial.println("图片传输请求已设置");
  }
}

void button_double_pressed(){
  Serial.println("Button double pressed");
  BLEServerDemo::send_my_data("ai_work");
}

void image_transfer_task(void *p){
  while (1) {
    if (imageRequested && BLEServerDemo::deviceConnected) {
      imageRequested = false;
      imageTransferInProgress = true;
      
      Serial.println("开始图片传输流程");
      
      // 获取图片
      get_image_forsdcard();
      
      // 设置BLE图片数据
      BLEServerDemo::prepare_image_for_transfer();
      
      Serial.println("图片传输完成");
      imageTransferInProgress = false;
    }
    vTaskDelay(100 / portTICK_PERIOD_MS);
  }
}

void my_driver_print(void *p){
  while (1) {
     print_axp2101_status();
    vTaskDelay(20000 / portTICK_PERIOD_MS);
  }
}

void setup(void){
  delay(5000);
  Serial.begin(115200);
  Serial.setDebugOutput(true);
  Wire.begin(BSP_I2C_SDA, BSP_I2C_SCL);
  pinMode(0, INPUT_PULLUP);
  pinMode(46, OUTPUT);
  digitalWrite(46, HIGH);

  my_sd_init();
  my_es8311_init();
  button.attachClick(button_pressed);
  button.attachDoubleClick(button_double_pressed);
  my_driver_init();
  BLEServerDemo::my_ble_init();
  
  // 创建图片传输任务
  xTaskCreate(image_transfer_task, "image_transfer", 4096, NULL, 5, NULL);
}

void loop() {
  mp3_loop();
  button.tick();
}