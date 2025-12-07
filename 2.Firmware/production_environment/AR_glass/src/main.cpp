#include "my_ble.h"
#include "my_camera.h"
#include "my_sd.h"
#include "my_es8311.h"
#include "my_txt.h"
// #include "my_driver.h"
#include "my_uart.h"
#include "OneButton.h"
#include "Wire.h"
#include "FreeRTOS.h"
#include "my_ota.h"
#include "my_txt.h"
OneButton button(0, true);
TaskHandle_t my_mp3_task_handle;

#define BSP_I2C_SDA           (GPIO_NUM_1)   // SDA引脚
#define BSP_I2C_SCL           (GPIO_NUM_2)   // SCL引脚
int symaxnum;
char buff[100];
void button_pressed(){
  Serial.println("Button pressed");
  // get_image();
  // save_jpg_file(my_image.buf, my_image.len);

  get_image_forsdcard();
}
void button_double_pressed(){
  Serial.println("Button double pressed");
}


void my_driver_print(void *p){
  while (1)
  {
    // my_driver_get_battery_percent();
    vTaskDelay(20000 / portTICK_PERIOD_MS);
  }
}



void setup(void){
  delay(4000);
  Serial.begin(115200);
  Wire.begin(BSP_I2C_SDA, BSP_I2C_SCL);
  pinMode(0, INPUT_PULLUP);
  pinMode(6, OUTPUT);
  digitalWrite(6, 1);


  my_sd_init();
  // my_driver_init();
  my_es8311_init();
  my_uart_init();

  // print_axp2101_status();

  // my_camera_init();
  // play_mp3("/mp3/ltx.mp3");
  // mp3_update();
  button.attachClick(button_pressed);
  button.attachDoubleClick(button_double_pressed);
  // xTaskCreate(my_driver_print, "my_driver_print", 1024*3, NULL, 5, NULL);
  // BLEServerDemo::my_ble_init();
}

void loop() {
  if(BLEServerDemo::nowthing==1){
    Serial.println(BLEServerDemo::nowname);
    send_name(BLEServerDemo::nowname);
    display_txt(BLEServerDemo::nowname,BLEServerDemo::nowpage,&symaxnum);
    if(BLEServerDemo::nowpage>symaxnum){
      BLEServerDemo::nowpage=0;
    }
    sprintf(buff,"%d/%d",BLEServerDemo::nowpage+1,symaxnum+1);
    send_bar((BLEServerDemo::nowpage+1)*100/(symaxnum+1));
    send_pages(buff);
    Serial.println(buff);
    BLEServerDemo::nowthing=0;
  }else if(BLEServerDemo::nowthing==2){
    Serial.println(BLEServerDemo::nowname);
    send_name(BLEServerDemo::nowname);
    display_json(BLEServerDemo::nowname,BLEServerDemo::nowpage,&symaxnum);
    if(BLEServerDemo::nowpage>symaxnum){
      BLEServerDemo::nowpage=0;
    }
    sprintf(buff,"%d/%d",BLEServerDemo::nowpage+1,symaxnum+1);
    send_bar((BLEServerDemo::nowpage+1)*100/(symaxnum+1));
    send_pages(buff);
    Serial.println(buff);
    BLEServerDemo::nowthing=0;
  }else if(BLEServerDemo::nowthing==3){
    Serial.println(BLEServerDemo::nowname);
    play_mp3(BLEServerDemo::nowname);
    BLEServerDemo::nowthing=0;
  }else if(BLEServerDemo::nowthing==4){
    mp3_stop();
    BLEServerDemo::nowthing=0;
  }else if(BLEServerDemo::nowthing==5){
    delete_json_file();
    BLEServerDemo::nowthing=0;
  }else if(BLEServerDemo::nowthing==6){
    updateFromSD();
    BLEServerDemo::nowthing=0;
  }
  button.tick();
  // axp_off();
  mp3_loop();
}

