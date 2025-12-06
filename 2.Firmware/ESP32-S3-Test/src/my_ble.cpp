#include "my_ble.h"
#include "my_camera.h"
#include "my_sd.h"
#include "my_driver.h"
#define chunk_num 400

namespace BLEServerDemo
{
  bool deviceConnected = false;
  static int data_len;
  static int write_data_len;
  static bool image_prepared = false;

  BLEServer *pServer = nullptr;
  BLEService *pService1 = nullptr;
  BLEService *pService2 = nullptr;
  BLEService *pService3 = nullptr;

  BLECharacteristic *pCharacteristic1_1 = nullptr;
  BLECharacteristic *pCharacteristic1_2 = nullptr;
  BLECharacteristic *pCharacteristic1_3 = nullptr;

  BLECharacteristic *pCharacteristic2_1 = nullptr;
  BLECharacteristic *pCharacteristic2_2 = nullptr;
  BLECharacteristic *pCharacteristic2_3 = nullptr;

  BLECharacteristic *pCharacteristic3_1 = nullptr;
  BLECharacteristic *pCharacteristic3_2 = nullptr;
  BLECharacteristic *pCharacteristic3_3 = nullptr;

  void prepare_image_for_transfer() {
    if (my_image.buf != NULL) {
      data_len = my_image.len;
      write_data_len = 0;
      image_prepared = true;
      Serial.printf("图片准备完成，大小: %d 字节\n", data_len);
      
      // 发送通知告诉Android图片已准备好
      send_my_data("image_ready");
    }
  }

  void send_my_data(uint8_t *data, size_t len){
    if (deviceConnected && pCharacteristic3_3 != nullptr) {
      pCharacteristic3_3->setValue(data,len);
      pCharacteristic3_3->notify();
    }
  }
  
  void send_my_data(std::string value){
    if (deviceConnected && pCharacteristic3_3 != nullptr) {
      pCharacteristic3_3->setValue(value);
      pCharacteristic3_3->notify();
      Serial.printf("发送数据: %s\n",value.c_str());
    }
  }

  //----------------------------连接处理-------------------------//
  class ServerCallbacks : public BLEServerCallbacks
  {
    void onConnect(BLEServer *pServer)
    {
      deviceConnected = true;
      image_prepared = false;
      Serial.println("BLE connected");
    }

    void onDisconnect(BLEServer *pServer)
    {
      deviceConnected = false;
      image_prepared = false;
      Serial.println("BLE disconnected");
      pServer->startAdvertising();
    }
  };

  //------------------------------------------------------------//

  //-------------------------文件接收----------------------------//

  static uint8_t* json_data = nullptr;
  static size_t   json_len  = 0;
  static size_t   json_cap  = 0;

  class CharacteristicCallbacks1_2 : public BLECharacteristicCallbacks
  {
      void onWrite(BLECharacteristic* pChar) 
      {
          std::string value = pChar->getValue();
          if (value=="start")
          {
            Serial.printf("Received_data_name: %s\n",pCharacteristic1_3->getValue().c_str());
            start_write(pCharacteristic1_3->getValue().c_str());
            update_write(pCharacteristic1_1->getData(),pCharacteristic1_1->getValue().length());
          }else if(value=="update"){
            update_write(pCharacteristic1_1->getData(),pCharacteristic1_1->getValue().length());
          }else if(value=="end"){
            end_write();
            Serial.printf("Received_data_end: %s\n",pCharacteristic1_3->getValue().c_str());
          }
      }
  };
  // -----------------------------------------------------------//

  //--------------------------照片发送-------------------------//

  class CharacteristicCallbacks2_1 : public BLECharacteristicCallbacks
  {
    void onRead(BLECharacteristic *pCharacteristic, esp_ble_gatts_cb_param_t *param)
    {
      Serial.printf("read_image_len: %d\n", data_len);
      pCharacteristic->setValue(data_len);
    }
  };

  class CharacteristicCallbacks2_2 : public BLECharacteristicCallbacks
  {
    void onWrite(BLECharacteristic *pCharacteristic)
    {
      std::string value = pCharacteristic->getValue();
      Serial.printf("收到命令: %s\n", value.c_str());
      
      if (value == "getimage")
      {
        if (my_image.buf != NULL && image_prepared)
        {
          if (data_len > chunk_num)
          {
            pCharacteristic2_3->setValue(my_image.buf + write_data_len, chunk_num);
            pCharacteristic2_3->notify();
            write_data_len += chunk_num;
            data_len -= chunk_num;
            Serial.printf("发送图片数据块: %d 字节, 剩余: %d 字节\n", chunk_num, data_len);
          }
          else
          {
            pCharacteristic2_3->setValue(my_image.buf + write_data_len, data_len);
            pCharacteristic2_3->notify();
            write_data_len = 0;
            data_len = 0;
            image_prepared = false;
            delay(50); // 确保数据发送完成
            send_my_data("image_end");
            Serial.println("图片发送完成");
          }
        }
        else
        {
          Serial.println("图片尚未准备");
        }
      }
      else if (value == "takeimage")
      {
        if (image_prepared) {
          Serial.println("图片已准备就绪");
          // 立即发送就绪通知
          send_my_data("image_ready");
        } else {
          Serial.println("图片尚未准备");
        }
      }
    }
  };

  //------------------------------------------------------------//

  //---------------------------数据获取--------------------------//
  class CharacteristicCallbacks3_1 : public BLECharacteristicCallbacks
  {
    void onRead(BLECharacteristic *pCharacteristic, esp_ble_gatts_cb_param_t *param)
    {
      Serial.println("read_battery_percent");
      char nowbattery[10];
      sprintf(nowbattery,"%d",my_driver_get_battery_percent());
      pCharacteristic->setValue(nowbattery);
    }
  };

  class CharacteristicCallbacks3_2 : public BLECharacteristicCallbacks
  {
    void onWrite(BLECharacteristic *pCharacteristic)
    {
      std::string value = pCharacteristic->getValue();
      Serial.printf("Received_data: %s\n",value.c_str());
    }
  };
  //------------------------------------------------------------//

  void my_ble_init()
  {
    BLEDevice::init("AR_GLASS");
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());

    pService1 = pServer->createService(BLEUUID("aabb0100-0000-1000-8000-00805f9b34fb"));
    pService2 = pServer->createService(BLEUUID("aabb0200-0000-1000-8000-00805f9b34fb"));
    pService3 = pServer->createService(BLEUUID("aabb0300-0000-1000-8000-00805f9b34fb"));

    //-------------------------文件接收----------------------------//
    pCharacteristic1_1 = pService1->createCharacteristic(
        BLEUUID("aabb0101-0000-1000-8000-00805f9b34fb"),
        BLECharacteristic::PROPERTY_WRITE);

    pCharacteristic1_2 = pService1->createCharacteristic(
        BLEUUID("aabb0102-0000-1000-8000-00805f9b34fb"),
        BLECharacteristic::PROPERTY_WRITE);
    pCharacteristic1_2->setCallbacks(new CharacteristicCallbacks1_2());

    pCharacteristic1_3 = pService1->createCharacteristic(
        BLEUUID("aabb0103-0000-1000-8000-00805f9b34fb"),
        BLECharacteristic::PROPERTY_WRITE);

    //-----------------------------------------------------------//

    //--------------------------照片发送-------------------------//
    pCharacteristic2_1 = pService2->createCharacteristic(
        BLEUUID("aabb0201-0000-1000-8000-00805f9b34fb"),
        BLECharacteristic::PROPERTY_READ);
    pCharacteristic2_1->setCallbacks(new CharacteristicCallbacks2_1());

    pCharacteristic2_2 = pService2->createCharacteristic(
        BLEUUID("aabb0202-0000-1000-8000-00805f9b34fb"),
        BLECharacteristic::PROPERTY_WRITE);
    pCharacteristic2_2->setCallbacks(new CharacteristicCallbacks2_2());

    pCharacteristic2_3 = pService2->createCharacteristic(
        BLEUUID("aabb0203-0000-1000-8000-00805f9b34fb"),
        BLECharacteristic::PROPERTY_NOTIFY);
    pCharacteristic2_3->addDescriptor(new BLE2902()); 

    //------------------------------------------------------------//

    //---------------------------数据获取--------------------------//
    pCharacteristic3_1 = pService3->createCharacteristic(
        BLEUUID("aabb0301-0000-1000-8000-00805f9b34fb"),
        BLECharacteristic::PROPERTY_READ);
    pCharacteristic3_1->setCallbacks(new CharacteristicCallbacks3_1());

    pCharacteristic3_2 = pService3->createCharacteristic(
        BLEUUID("aabb0302-0000-1000-8000-00805f9b34fb"),
        BLECharacteristic::PROPERTY_WRITE);
    pCharacteristic3_2->setCallbacks(new CharacteristicCallbacks3_2());

    pCharacteristic3_3 = pService3->createCharacteristic(
        BLEUUID("aabb0303-0000-1000-8000-00805f9b34fb"),
        BLECharacteristic::PROPERTY_NOTIFY);
    pCharacteristic3_3->addDescriptor(new BLE2902()); 

    //------------------------------------------------------------//

    pService1->start();
    pService2->start();
    pService3->start();
    pServer->getAdvertising()->start();
    Serial.println("Waiting for client connection...");
  }
}