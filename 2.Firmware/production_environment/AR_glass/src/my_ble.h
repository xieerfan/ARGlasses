#ifndef MY_BLE_H
#define MY_BLE_H


#include "Arduino.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include "string.h"

namespace BLEServerDemo {
extern int nowpage;
extern int nowmode;
extern int nowthing;
extern char nowname[256];
void send_my_data(uint8_t *data, size_t len);
void send_my_data(std::string value);
void my_ble_init();

}




#endif
