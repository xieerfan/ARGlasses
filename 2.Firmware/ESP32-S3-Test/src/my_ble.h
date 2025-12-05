#ifndef MY_BLE_H
#define MY_BLE_H

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

namespace BLEServerDemo {
    extern bool deviceConnected;
    
    void my_ble_init();
    void send_my_data(uint8_t *data, size_t len);
    void send_my_data(std::string value);
    void prepare_image_for_transfer();
}

#endif