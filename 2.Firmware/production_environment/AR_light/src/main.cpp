#include <Arduino.h>
#include "myoled.h"
#include "lvgl.h"
#include "my_uart.h"
#include "generated/gui_guider.h"
#include "font_alipuhui20.h"

lv_ui  guider_ui;


void setup() {
    Serial.begin(115200);
    bsp_lvgl_init();
    my_uart_init();
    Serial.println("Starting display sequence...");
    // lv_demo_benchmark(); 
    setup_ui(&guider_ui);
}

void loop() {
      onDataReceived();
   lv_timer_handler();
   delay(5);
}